package ru.pathcreator.pyc.rpc.serialization.serializer.sbe.introspection;

import ru.pathcreator.pyc.rpc.serialization.annotation.RpcFieldOrder;
import ru.pathcreator.pyc.rpc.serialization.annotation.RpcFixedLength;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.annotation.SbeSerializable;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model.SbeFieldKind;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model.SbeFieldSpec;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model.SbeInstantiationStyle;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model.SbeTypeSpec;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.*;

public final class SbeDtoIntrospector {

    public SbeTypeSpec inspectRoot(final Class<?> rootType) {
        return inspect(rootType, true, new IdentityHashMap<>());
    }

    private SbeTypeSpec inspect(final Class<?> type, final boolean root, final IdentityHashMap<Class<?>, Boolean> stack) {
        if (stack.containsKey(type)) {
            throw new IllegalStateException("Recursive DTO graph is not supported for SBE generation: " + type.getName());
        }
        stack.put(type, Boolean.TRUE);
        final List<Field> fields = declaredInstanceFields(type);
        final List<SbeFieldSpec> specs = new ArrayList<>(fields.size());
        for (final Field field : fields) {
            specs.add(classifyField(field, root, stack));
        }
        stack.remove(type);
        final SbeSerializable annotation = type.getAnnotation(SbeSerializable.class);
        final String schemaName = annotation != null && !annotation.schemaName().isBlank()
                ? annotation.schemaName()
                : type.getSimpleName();
        return new SbeTypeSpec(type, schemaName, instantiationStyle(type), specs);
    }

    private SbeFieldSpec classifyField(final Field field, final boolean root, final IdentityHashMap<Class<?>, Boolean> stack) {
        final RpcFixedLength fixedLength = field.getAnnotation(RpcFixedLength.class);
        final Integer fixed = fixedLength != null ? fixedLength.value() : null;
        return classifyType(field, field.getGenericType(), field.getType(), field.getDeclaringClass().getName(), field.getName(), fixed, root, stack, false);
    }

    private SbeFieldSpec classifyType(
            final Field sourceField,
            final Type genericType,
            final Class<?> rawType,
            final String ownerTypeName,
            final String memberName,
            final Integer fixed,
            final boolean root,
            final IdentityHashMap<Class<?>, Boolean> stack,
            final boolean optional
    ) {
        if (rawType.isPrimitive()) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, primitiveKind(rawType), fixed, null, optional);
        }
        if (isBoxedPrimitive(rawType)) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.BOXED_PRIMITIVE, fixed, null, optional);
        }
        if (rawType == Boolean.class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.BOXED_BOOLEAN, fixed, null, optional);
        }
        if (rawType == Character.class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.BOXED_CHAR, fixed, null, optional);
        }
        if (rawType.isEnum()) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.ENUM, fixed, null, optional);
        }
        if (rawType == UUID.class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.UUID, fixed, null, optional);
        }
        if (rawType == Instant.class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.INSTANT, fixed, null, optional);
        }
        if (rawType == LocalDate.class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.LOCAL_DATE, fixed, null, optional);
        }
        if (rawType == LocalTime.class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.LOCAL_TIME, fixed, null, optional);
        }
        if (rawType == LocalDateTime.class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.LOCAL_DATE_TIME, fixed, null, optional);
        }
        if (rawType == OffsetDateTime.class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.OFFSET_DATE_TIME, fixed, null, optional);
        }
        if (rawType == OffsetTime.class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.OFFSET_TIME, fixed, null, optional);
        }
        if (rawType == Duration.class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.DURATION, fixed, null, optional);
        }
        if (rawType == Period.class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.PERIOD, fixed, null, optional);
        }
        if (rawType == Year.class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.YEAR, fixed, null, optional);
        }
        if (rawType == YearMonth.class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.YEAR_MONTH, fixed, null, optional);
        }
        if (rawType == MonthDay.class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.MONTH_DAY, fixed, null, optional);
        }
        if (rawType == ZoneOffset.class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.ZONE_OFFSET, fixed, null, optional);
        }
        if (rawType == BigInteger.class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.BIG_INTEGER, fixed, null, optional);
        }
        if (rawType == BigDecimal.class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.BIG_DECIMAL, fixed, null, optional);
        }
        if (rawType == String.class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, fixed != null ? SbeFieldKind.FIXED_STRING : SbeFieldKind.STRING, fixed, null, optional);
        }
        if (rawType == byte[].class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, fixed != null ? SbeFieldKind.FIXED_BYTES : SbeFieldKind.BYTES, fixed, null, optional);
        }
        if (rawType == Optional.class) {
            return classifyOptionalField(sourceField, genericType, ownerTypeName, memberName, fixed, root, stack);
        }
        if (rawType.isArray()) {
            return classifyArrayField(sourceField, rawType, ownerTypeName, memberName, fixed, root, stack);
        }
        if (Map.class.isAssignableFrom(rawType)) {
            return classifyMapField(sourceField, genericType, rawType, ownerTypeName, memberName, fixed, root, stack, optional);
        }
        if (Collection.class.isAssignableFrom(rawType)) {
            return classifyCollectionField(sourceField, genericType, rawType, ownerTypeName, memberName, fixed, root, stack, optional);
        }
        if (rawType == Object.class
            || rawType.isInterface()
            || Modifier.isAbstract(rawType.getModifiers())
            || rawType.isAnonymousClass()
            || rawType.isLocalClass()) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.UNSUPPORTED, fixed, null, optional);
        }
        final SbeTypeSpec nestedType = inspect(rawType, false, stack);
        return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.NESTED_FIXED, fixed, nestedType, optional);
    }

    private SbeFieldSpec classifyOptionalField(
            final Field sourceField,
            final Type genericType,
            final String ownerTypeName,
            final String memberName,
            final Integer fixed,
            final boolean root,
            final IdentityHashMap<Class<?>, Boolean> stack
    ) {
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return newSpec(sourceField, ownerTypeName, memberName, Optional.class, SbeFieldKind.UNSUPPORTED, fixed, null, false);
        }
        final Type[] arguments = parameterizedType.getActualTypeArguments();
        if (arguments.length != 1) {
            return newSpec(sourceField, ownerTypeName, memberName, Optional.class, SbeFieldKind.UNSUPPORTED, fixed, null, false);
        }
        final Type argument = arguments[0];
        final Class<?> rawArgument = rawClass(argument);
        if (rawArgument == null || rawArgument == Optional.class) {
            return newSpec(sourceField, ownerTypeName, memberName, Optional.class, SbeFieldKind.UNSUPPORTED, fixed, null, false);
        }
        return classifyType(sourceField, argument, rawArgument, ownerTypeName, memberName, fixed, root, stack, true);
    }

    private SbeFieldSpec classifyArrayField(
            final Field sourceField,
            final Class<?> rawType,
            final String ownerTypeName,
            final String memberName,
            final Integer fixed,
            final boolean root,
            final IdentityHashMap<Class<?>, Boolean> stack
    ) {
        final Class<?> componentType = rawType.getComponentType();
        if (componentType == null) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.UNSUPPORTED, fixed, null, false);
        }
        if (componentType == byte.class) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.UNSUPPORTED, fixed, null, false);
        }
        return classifyRepeatingElement(sourceField, ownerTypeName, memberName, rawType, componentType, fixed, root, stack, false);
    }

    private SbeFieldSpec classifyCollectionField(
            final Field sourceField,
            final Type genericType,
            final Class<?> rawType,
            final String ownerTypeName,
            final String memberName,
            final Integer fixed,
            final boolean root,
            final IdentityHashMap<Class<?>, Boolean> stack,
            final boolean optional
    ) {
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.UNSUPPORTED, fixed, null, optional);
        }
        final Type[] arguments = parameterizedType.getActualTypeArguments();
        if (arguments.length != 1) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.UNSUPPORTED, fixed, null, optional);
        }
        final Class<?> elementType = rawClass(arguments[0]);
        if (elementType == null) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.UNSUPPORTED, fixed, null, optional);
        }
        return classifyRepeatingElement(sourceField, ownerTypeName, memberName, rawType, elementType, fixed, root, stack, optional);
    }

    private SbeFieldSpec classifyRepeatingElement(
            final Field sourceField,
            final String ownerTypeName,
            final String memberName,
            final Class<?> containerType,
            final Class<?> elementType,
            final Integer fixed,
            final boolean root,
            final IdentityHashMap<Class<?>, Boolean> stack,
            final boolean optional
    ) {
        if ((elementType.isArray() && elementType != byte[].class)
            || elementType == Optional.class
            || Map.class.isAssignableFrom(elementType)) {
            return newSpec(sourceField, ownerTypeName, memberName, containerType, SbeFieldKind.UNSUPPORTED, fixed, null, optional);
        }
        final SbeFieldSpec elementSpec = classifyType(
                null,
                elementType,
                elementType,
                ownerTypeName + "." + memberName,
                "value",
                fixed,
                root,
                stack,
                false
        );
        if (elementSpec.kind() == SbeFieldKind.UNSUPPORTED) {
            return newSpec(sourceField, ownerTypeName, memberName, containerType, SbeFieldKind.UNSUPPORTED, fixed, null, optional);
        }
        if (elementSpec.kind() == SbeFieldKind.NESTED_FIXED) {
            return newSpec(sourceField, ownerTypeName, memberName, containerType, SbeFieldKind.REPEATING_GROUP, fixed, elementSpec.nestedType(), optional);
        }
        return newSpec(sourceField, ownerTypeName, memberName, containerType, SbeFieldKind.REPEATING_SCALAR, fixed, null, optional)
                .withElementSpec(elementSpec);
    }

    private SbeFieldSpec classifyMapField(
            final Field sourceField,
            final Type genericType,
            final Class<?> rawType,
            final String ownerTypeName,
            final String memberName,
            final Integer fixed,
            final boolean root,
            final IdentityHashMap<Class<?>, Boolean> stack,
            final boolean optional
    ) {
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.UNSUPPORTED, fixed, null, optional);
        }
        final Type[] arguments = parameterizedType.getActualTypeArguments();
        if (arguments.length != 2) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.UNSUPPORTED, fixed, null, optional);
        }
        final Class<?> keyType = rawClass(arguments[0]);
        final Class<?> valueType = rawClass(arguments[1]);
        if (keyType == null || valueType == null) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.UNSUPPORTED, fixed, null, optional);
        }
        final SbeFieldSpec keySpec = classifyType(null, keyType, keyType, ownerTypeName + "." + memberName, "key", null, root, stack, false);
        final SbeFieldSpec valueSpec = classifyType(null, valueType, valueType, ownerTypeName + "." + memberName, "value", null, root, stack, false);
        if (keySpec.kind() == SbeFieldKind.UNSUPPORTED
            || valueSpec.kind() == SbeFieldKind.UNSUPPORTED
            || keySpec.repeatingGroup()
            || valueSpec.repeatingGroup()) {
            return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.UNSUPPORTED, fixed, null, optional);
        }
        return newSpec(sourceField, ownerTypeName, memberName, rawType, SbeFieldKind.MAP, fixed, null, optional)
                .withMapSpecs(keySpec, valueSpec);
    }

    private SbeFieldSpec newSpec(
            final Field sourceField,
            final String ownerTypeName,
            final String memberName,
            final Class<?> rawType,
            final SbeFieldKind kind,
            final Integer fixed,
            final SbeTypeSpec nestedType,
            final boolean optional
    ) {
        final SbeFieldSpec base = sourceField != null
                ? new SbeFieldSpec(sourceField, memberName, rawType, kind, fixed, nestedType)
                : SbeFieldSpec.synthetic(ownerTypeName, memberName, rawType, kind, fixed, nestedType);
        return base.withOptional(optional);
    }

    private Class<?> rawClass(final Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            final Type raw = parameterizedType.getRawType();
            return raw instanceof Class<?> clazz ? clazz : null;
        }
        if (type instanceof WildcardType wildcardType && wildcardType.getUpperBounds().length == 1) {
            return rawClass(wildcardType.getUpperBounds()[0]);
        }
        return null;
    }

    private SbeInstantiationStyle instantiationStyle(final Class<?> type) {
        if (type.isRecord()) {
            return SbeInstantiationStyle.RECORD;
        }
        try {
            final Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return SbeInstantiationStyle.NO_ARGS_CONSTRUCTOR;
        } catch (final NoSuchMethodException e) {
            return SbeInstantiationStyle.UNSUPPORTED_FOR_SBE;
        }
    }

    private SbeFieldKind primitiveKind(final Class<?> type) {
        if (type == boolean.class) {
            return SbeFieldKind.BOOLEAN;
        }
        if (type == char.class) {
            return SbeFieldKind.CHAR;
        }
        return SbeFieldKind.PRIMITIVE;
    }

    private boolean isBoxedPrimitive(final Class<?> type) {
        return type == Byte.class || type == Short.class || type == Integer.class || type == Long.class
               || type == Float.class || type == Double.class;
    }

    private List<Field> declaredInstanceFields(final Class<?> type) {
        final Map<String, Integer> declarationOrder = new LinkedHashMap<>();
        final List<Field> fields = new ArrayList<>();
        final Field[] declaredFields = type.getDeclaredFields();
        for (int i = 0; i < declaredFields.length; i++) {
            final Field field = declaredFields[i];
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            field.setAccessible(true);
            fields.add(field);
            declarationOrder.put(field.getName(), i);
        }
        fields.sort(Comparator
                .comparingInt((Field field) -> {
                    final RpcFieldOrder order = field.getAnnotation(RpcFieldOrder.class);
                    return order != null ? order.value() : Integer.MAX_VALUE;
                })
                .thenComparingInt(field -> declarationOrder.getOrDefault(field.getName(), Integer.MAX_VALUE)));
        return fields;
    }
}