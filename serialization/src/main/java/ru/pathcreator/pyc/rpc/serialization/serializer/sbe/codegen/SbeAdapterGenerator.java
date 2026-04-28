package ru.pathcreator.pyc.rpc.serialization.serializer.sbe.codegen;

import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model.SbeFieldKind;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model.SbeFieldSpec;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model.SbeInstantiationStyle;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model.SbeTypeSpec;

import java.util.*;

public final class SbeAdapterGenerator {

    public String generate(final SbeTypeSpec spec, final String generatedPackage) {
        final String officialPackage = SbeNaming.officialPackageName(generatedPackage, spec.javaType());
        final String simpleName = SbeNaming.codecSimpleName(spec.javaType());
        final String rootEncoderType = SbeNaming.messageEncoderSimpleName(spec);
        final String rootDecoderType = SbeNaming.messageDecoderSimpleName(spec);
        final StringBuilder source = new StringBuilder();
        source.append("package ").append(generatedPackage).append(";\n\n");
        source.append("import org.agrona.DirectBuffer;\n");
        source.append("import org.agrona.MutableDirectBuffer;\n");
        source.append("import org.agrona.collections.ArrayUtil;\n");
        source.append("import ru.pathcreator.pyc.rpc.codec.SerializationCodec;\n");
        source.append("import ru.pathcreator.pyc.rpc.codec.SerializationFormat;\n");
        source.append("import ru.pathcreator.pyc.rpc.serialization.support.reflect.ReflectionSupport;\n");
        source.append("import ").append(officialPackage).append(".*;\n");
        source.append("import java.lang.invoke.VarHandle;\n");
        source.append("import java.lang.reflect.Constructor;\n");
        source.append("import java.nio.charset.StandardCharsets;\n");
        source.append("import java.time.*;\n");
        source.append("import java.util.*;\n");
        source.append("import java.util.UUID;\n\n");
        source.append("@SuppressWarnings({\"unchecked\", \"rawtypes\"})\n");
        source.append("public final class ").append(simpleName).append(" implements SerializationCodec<").append(SbeNaming.javaTypeName(spec.javaType())).append("> {\n");
        source.append("    private static final byte[] EMPTY_BYTES = new byte[0];\n");
        emitFieldHandles(source, spec);
        emitConstructors(source, spec, new HashSet<>());
        emitEnumCaches(source, spec, new HashSet<>(), new HashSet<>());
        source.append('\n');
        source.append("    @Override\n");
        source.append("    public Class<").append(SbeNaming.javaTypeName(spec.javaType())).append("> javaType() {\n");
        source.append("        return ").append(SbeNaming.javaTypeName(spec.javaType())).append(".class;\n");
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    public SerializationFormat kind() {\n");
        source.append("        return SerializationFormat.SBE;\n");
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    public int measure(final ").append(SbeNaming.javaTypeName(spec.javaType())).append(" value) {\n");
        source.append("        if (value == null) throw new IllegalArgumentException(\"Message must not be null\");\n");
        source.append("        int size = MessageHeaderEncoder.ENCODED_LENGTH + ").append(rootBlockLength(spec)).append(";\n");
        emitMeasureDynamic(source, spec, "value");
        source.append("        return size;\n");
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    public int encode(final ").append(SbeNaming.javaTypeName(spec.javaType())).append(" value, final MutableDirectBuffer buffer, final int offset) {\n");
        source.append("        if (value == null) throw new IllegalArgumentException(\"Message must not be null\");\n");
        source.append("        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();\n");
        source.append("        final ").append(rootEncoderType).append(" messageEncoder = new ").append(rootEncoderType).append("();\n");
        source.append("        messageEncoder.wrapAndApplyHeader(buffer, offset, headerEncoder);\n");
        emitEncodeFixedFields(source, spec, "messageEncoder", "value");
        emitEncodeDynamicFields(source, spec, rootEncoderType, "messageEncoder", "value");
        source.append("        return MessageHeaderEncoder.ENCODED_LENGTH + messageEncoder.encodedLength();\n");
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    public ").append(SbeNaming.javaTypeName(spec.javaType())).append(" decode(final DirectBuffer buffer, final int offset, final int length) {\n");
        source.append("        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();\n");
        source.append("        final ").append(rootDecoderType).append(" messageDecoder = new ").append(rootDecoderType).append("();\n");
        source.append("        messageDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);\n");
        source.append("        return decode").append(SbeNaming.sanitize(spec.javaType().getName())).append("(messageDecoder);\n");
        source.append("    }\n\n");
        emitRootDecodeMethod(source, spec, rootDecoderType);
        emitGroupHelpers(source, spec, officialPackage);
        emitStructuredMeasureHelpers(source, spec, new HashSet<>());
        emitLogicalHelpers(source, spec, new HashSet<>(), new HashSet<>());
        emitUtilityHelpers(source);
        source.append("}\n");
        return source.toString();
    }

    private void emitFieldHandles(final StringBuilder source, final SbeTypeSpec spec) {
        emitFieldHandlesRecursive(source, spec, new HashSet<>());
    }

    private void emitFieldHandlesRecursive(final StringBuilder source, final SbeTypeSpec spec, final Set<Class<?>> visited) {
        if (!visited.add(spec.javaType())) {
            return;
        }
        for (final SbeFieldSpec field : spec.fields()) {
            source.append("    private static final VarHandle ")
                    .append(handle(field))
                    .append(" = ReflectionSupport.varHandle(")
                    .append(SbeNaming.javaTypeName(field.field().getDeclaringClass()))
                    .append(".class, \"")
                    .append(field.name())
                    .append("\", ")
                    .append(SbeNaming.javaTypeName(declaredJavaType(field)))
                    .append(".class);\n");
            if (field.kind() == SbeFieldKind.NESTED_FIXED || field.kind() == SbeFieldKind.REPEATING_GROUP) {
                emitFieldHandlesRecursive(source, field.nestedType(), visited);
            }
            if (field.kind() == SbeFieldKind.MAP) {
                if (field.mapKeySpec() != null && field.mapKeySpec().kind() == SbeFieldKind.NESTED_FIXED && field.mapKeySpec().nestedType() != null) {
                    emitFieldHandlesRecursive(source, field.mapKeySpec().nestedType(), visited);
                }
                if (field.mapValueSpec() != null && field.mapValueSpec().kind() == SbeFieldKind.NESTED_FIXED && field.mapValueSpec().nestedType() != null) {
                    emitFieldHandlesRecursive(source, field.mapValueSpec().nestedType(), visited);
                }
            }
        }
    }

    private void emitConstructors(final StringBuilder source, final SbeTypeSpec spec, final Set<Class<?>> visited) {
        emitConstructorsRecursive(source, spec, visited);
    }

    private void emitConstructorsRecursive(final StringBuilder source, final SbeTypeSpec spec, final Set<Class<?>> visited) {
        if (!visited.add(spec.javaType())) {
            return;
        }
        if (spec.instantiationStyle() == SbeInstantiationStyle.NO_ARGS_CONSTRUCTOR) {
            source.append("    private static final Constructor<")
                    .append(SbeNaming.javaTypeName(spec.javaType()))
                    .append("> ")
                    .append(constructorField(spec.javaType()))
                    .append(" = ReflectionSupport.noArgsConstructor(")
                    .append(SbeNaming.javaTypeName(spec.javaType()))
                    .append(".class);\n");
        }
        for (final SbeFieldSpec field : spec.fields()) {
            if (field.kind() == SbeFieldKind.NESTED_FIXED || field.kind() == SbeFieldKind.REPEATING_GROUP) {
                emitConstructorsRecursive(source, field.nestedType(), visited);
            }
            if (field.kind() == SbeFieldKind.MAP) {
                if (field.mapKeySpec() != null && field.mapKeySpec().kind() == SbeFieldKind.NESTED_FIXED) {
                    emitConstructorsRecursive(source, field.mapKeySpec().nestedType(), visited);
                }
                if (field.mapValueSpec() != null && field.mapValueSpec().kind() == SbeFieldKind.NESTED_FIXED) {
                    emitConstructorsRecursive(source, field.mapValueSpec().nestedType(), visited);
                }
            }
            if ((field.kind() == SbeFieldKind.REPEATING_GROUP
                 || field.kind() == SbeFieldKind.REPEATING_SCALAR
                 || field.kind() == SbeFieldKind.MAP)
                && !field.javaType().isArray()
                && !field.javaType().isInterface()
                && (Collection.class.isAssignableFrom(field.javaType()) || Map.class.isAssignableFrom(field.javaType()))) {
                source.append("    private static final Constructor<")
                        .append(SbeNaming.javaTypeName(field.javaType()))
                        .append("> ")
                        .append(collectionConstructorField(field))
                        .append(" = ReflectionSupport.noArgsConstructor(")
                        .append(SbeNaming.javaTypeName(field.javaType()))
                        .append(".class);\n");
            }
        }
    }

    private void emitEnumCaches(
            final StringBuilder source,
            final SbeTypeSpec spec,
            final Set<Class<?>> emittedEnums,
            final Set<Class<?>> visited
    ) {
        if (!visited.add(spec.javaType())) {
            return;
        }
        for (final SbeFieldSpec field : spec.fields()) {
            if (field.kind() == SbeFieldKind.ENUM && emittedEnums.add(field.javaType())) {
                source.append("    private static final ")
                        .append(SbeNaming.javaTypeName(field.javaType()))
                        .append("[] ")
                        .append(enumValuesField(field.javaType()))
                        .append(" = ")
                        .append(SbeNaming.javaTypeName(field.javaType()))
                        .append(".values();\n");
            }
            if (field.kind() == SbeFieldKind.NESTED_FIXED || field.kind() == SbeFieldKind.REPEATING_GROUP) {
                emitEnumCaches(source, field.nestedType(), emittedEnums, visited);
            }
            if (field.kind() == SbeFieldKind.REPEATING_SCALAR && field.elementSpec() != null
                && field.elementSpec().kind() == SbeFieldKind.ENUM && emittedEnums.add(field.elementSpec().javaType())) {
                source.append("    private static final ")
                        .append(SbeNaming.javaTypeName(field.elementSpec().javaType()))
                        .append("[] ")
                        .append(enumValuesField(field.elementSpec().javaType()))
                        .append(" = ")
                        .append(SbeNaming.javaTypeName(field.elementSpec().javaType()))
                        .append(".values();\n");
            }
            if (field.kind() == SbeFieldKind.MAP) {
                emitMapEnumCache(source, field.mapKeySpec(), emittedEnums, visited);
                emitMapEnumCache(source, field.mapValueSpec(), emittedEnums, visited);
            }
        }
    }

    private void emitMapEnumCache(
            final StringBuilder source,
            final SbeFieldSpec field,
            final Set<Class<?>> emittedEnums,
            final Set<Class<?>> visited
    ) {
        if (field == null) {
            return;
        }
        if (field.kind() == SbeFieldKind.ENUM && emittedEnums.add(field.javaType())) {
            source.append("    private static final ")
                    .append(SbeNaming.javaTypeName(field.javaType()))
                    .append("[] ")
                    .append(enumValuesField(field.javaType()))
                    .append(" = ")
                    .append(SbeNaming.javaTypeName(field.javaType()))
                    .append(".values();\n");
        }
        if (field.kind() == SbeFieldKind.NESTED_FIXED && field.nestedType() != null) {
            emitEnumCaches(source, field.nestedType(), emittedEnums, visited);
        }
    }

    private void emitMeasureDynamic(final StringBuilder source, final SbeTypeSpec spec, final String objectRef) {
        for (final SbeFieldSpec field : spec.fields()) {
            if (field.kind() == SbeFieldKind.NESTED_FIXED && field.nestedType() != null) {
                source.append("        size += measureStructuredDynamic").append(SbeNaming.sanitize(field.nestedType().javaType().getName()))
                        .append("((").append(SbeNaming.javaTypeName(field.javaType())).append(") ")
                        .append(referenceValueExpr(field, objectRef)).append(");\n");
            } else if (field.kind() == SbeFieldKind.STRING || field.kind() == SbeFieldKind.BIG_INTEGER || field.kind() == SbeFieldKind.BIG_DECIMAL) {
                source.append("        size += measureVariableString(").append(variableStringValueExpr(field, objectRef)).append(");\n");
            } else if (field.kind() == SbeFieldKind.BYTES) {
                source.append("        size += measureVariableBytes(").append(variableBytesValueExpr(field, objectRef)).append(");\n");
            } else if (field.kind() == SbeFieldKind.REPEATING_GROUP) {
                source.append("        size += 4 + measureGroup").append(helperSuffix(field))
                        .append("(").append(handle(field)).append(".get(").append(objectRef).append("));\n");
            } else if (field.kind() == SbeFieldKind.REPEATING_SCALAR) {
                source.append("        size += 4 + measureScalarGroup").append(helperSuffix(field))
                        .append("(").append(handle(field)).append(".get(").append(objectRef).append("));\n");
            } else if (field.kind() == SbeFieldKind.MAP) {
                source.append("        size += 4 + measureMapGroup").append(helperSuffix(field))
                        .append("(").append(handle(field)).append(".get(").append(objectRef).append("));\n");
            }
        }
    }

    private void emitEncodeFixedFields(
            final StringBuilder source,
            final SbeTypeSpec spec,
            final String encoderRef,
            final String objectRef
    ) {
        for (final SbeFieldSpec field : spec.fields()) {
            if (field.variableLength()) {
                source.append("        ").append(encoderRef).append(".").append(SbeSchemaGenerator.variablePresenceFieldName(field))
                        .append("((short) (").append(variablePresenceExpr(field, objectRef)).append(" ? 1 : 0));\n");
                continue;
            }
            if (field.repeatingGroup()) {
                continue;
            }
            emitFixedEncodeField(source, field, encoderRef, objectRef);
        }
    }

    private void emitFixedEncodeField(
            final StringBuilder source,
            final SbeFieldSpec field,
            final String encoderRef,
            final String objectRef
    ) {
        final String local = local(field);
        switch (field.kind()) {
            case PRIMITIVE -> emitPrimitiveEncode(source, field, encoderRef, objectRef);
            case BOXED_PRIMITIVE -> {
                source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(local).append(" = (")
                        .append(SbeNaming.javaTypeName(field.javaType())).append(") ").append(referenceValueExpr(field, objectRef)).append(";\n");
                source.append("        ").append(encoderRef).append(".").append(schemaName(field)).append("().present((short) (").append(local).append(" != null ? 1 : 0)).value(")
                        .append(local).append(" == null ? 0 : ").append(local).append(");\n");
            }
            case BOOLEAN ->
                    source.append("        ").append(encoderRef).append(".").append(schemaName(field)).append("((short) (((boolean) ")
                            .append(handle(field)).append(".get(").append(objectRef).append(")) ? 1 : 0));\n");
            case BOXED_BOOLEAN -> {
                source.append("        final Boolean ").append(local).append(" = (Boolean) ").append(referenceValueExpr(field, objectRef)).append(";\n");
                source.append("        ").append(encoderRef).append(".").append(schemaName(field)).append("().present((short) (").append(local).append(" != null ? 1 : 0)).value(")
                        .append(local).append(" != null && ").append(local).append(" ? (short) 1 : (short) 0);\n");
            }
            case CHAR -> {
                source.append("        final char ").append(local).append(" = (char) ").append(handle(field)).append(".get(").append(objectRef).append(");\n");
                source.append("        requireLatin1Char(").append(local).append(", \"").append(field.name()).append("\");\n");
                source.append("        ").append(encoderRef).append(".").append(schemaName(field)).append("((byte) ").append(local).append(");\n");
            }
            case BOXED_CHAR -> {
                source.append("        final Character ").append(local).append(" = (Character) ").append(referenceValueExpr(field, objectRef)).append(";\n");
                source.append("        if (").append(local).append(" != null) requireLatin1Char(").append(local).append(", \"").append(field.name()).append("\");\n");
                source.append("        ").append(encoderRef).append(".").append(schemaName(field)).append("().present((short) (").append(local).append(" != null ? 1 : 0)).value(")
                        .append(local).append(" == null ? (byte) 0 : (byte) (char) ").append(local).append(");\n");
            }
            case ENUM -> {
                source.append("        final Enum<?> ").append(local).append(" = (Enum<?>) ").append(referenceValueExpr(field, objectRef)).append(";\n");
                source.append("        ").append(encoderRef).append(".").append(schemaName(field)).append("().present((short) (").append(local).append(" != null ? 1 : 0)).value(")
                        .append(local).append(" == null ? 0 : ").append(local).append(".ordinal());\n");
            }
            case FIXED_STRING -> {
                source.append("        final String ").append(local).append(" = (String) ").append(referenceValueExpr(field, objectRef)).append(";\n");
                source.append("        final var ").append(local).append("_wrapper = ").append(encoderRef).append(".").append(schemaName(field)).append("();\n");
                source.append("        ").append(local).append("_wrapper.present((short) (").append(local).append(" != null ? 1 : 0));\n");
                source.append("        if (").append(local).append(" == null) {\n");
                source.append("            ").append(local).append("_wrapper.length(0).value(\"\");\n");
                source.append("        } else {\n");
                source.append("            final byte[] encoded = encodeFixedLatin1(").append(local).append(", ").append(field.fixedLength()).append(", \"").append(field.name()).append("\");\n");
                source.append("            ").append(local).append("_wrapper.length(encoded.length).value(").append(local).append(");\n");
                source.append("        }\n");
            }
            case FIXED_BYTES -> {
                source.append("        final byte[] ").append(local).append(" = (byte[]) ").append(referenceValueExpr(field, objectRef)).append(";\n");
                source.append("        final var ").append(local).append("_wrapper = ").append(encoderRef).append(".").append(schemaName(field)).append("();\n");
                source.append("        ").append(local).append("_wrapper.present((short) (").append(local).append(" != null ? 1 : 0));\n");
                source.append("        if (").append(local).append(" == null) {\n");
                source.append("            ").append(local).append("_wrapper.length(0).putValue(EMPTY_BYTES, 0, 0);\n");
                source.append("        } else {\n");
                source.append("            requireFixedBytesLength(").append(local).append(", ").append(field.fixedLength()).append(", \"").append(field.name()).append("\");\n");
                source.append("            ").append(local).append("_wrapper.length(").append(local).append(".length).putValue(").append(local).append(", 0, ").append(local).append(".length);\n");
                source.append("        }\n");
            }
            case NESTED_FIXED ->
                    emitEncodeFlattenedNestedField(source, field, encoderRef, referenceValueExpr(field, objectRef), "");
            case UUID, INSTANT, LOCAL_DATE, LOCAL_TIME, LOCAL_DATE_TIME, OFFSET_DATE_TIME, OFFSET_TIME, DURATION,
                 PERIOD, YEAR, YEAR_MONTH, MONTH_DAY, ZONE_OFFSET ->
                    emitLogicalEncode(source, field, encoderRef, objectRef);
            default -> {
            }
        }
    }

    private void emitPrimitiveEncode(
            final StringBuilder source,
            final SbeFieldSpec field,
            final String encoderRef,
            final String objectRef
    ) {
        if (field.javaType() == byte.class || field.javaType() == short.class || field.javaType() == int.class
            || field.javaType() == long.class || field.javaType() == float.class || field.javaType() == double.class) {
            source.append("        ").append(encoderRef).append(".").append(schemaName(field)).append("((")
                    .append(field.javaType().getName()).append(") ").append(handle(field)).append(".get(").append(objectRef).append("));\n");
        }
    }

    private void emitEncodeSyntheticField(
            final StringBuilder source,
            final SbeFieldSpec field,
            final String encoderRef,
            final String valueExpr
    ) {
        final String local = local(field);
        switch (field.kind()) {
            case PRIMITIVE -> source.append("        ").append(encoderRef).append(".").append(schemaName(field))
                    .append("((").append(SbeNaming.javaTypeName(field.javaType())).append(") (").append(valueExpr).append("));\n");
            case BOOLEAN -> source.append("        ").append(encoderRef).append(".").append(schemaName(field))
                    .append("((short) (((boolean) (").append(valueExpr).append(")) ? 1 : 0));\n");
            case CHAR -> {
                source.append("        final char ").append(local).append(" = (char) (").append(valueExpr).append(");\n");
                source.append("        requireLatin1Char(").append(local).append(", \"").append(field.name()).append("\");\n");
                source.append("        ").append(encoderRef).append(".").append(schemaName(field)).append("((byte) ").append(local).append(");\n");
            }
            case BOXED_PRIMITIVE -> {
                source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(local).append(" = (")
                        .append(SbeNaming.javaTypeName(field.javaType())).append(") ").append(valueExpr).append(";\n");
                source.append("        ").append(encoderRef).append(".").append(schemaName(field)).append("().present((short) (").append(local).append(" != null ? 1 : 0)).value(")
                        .append(local).append(" == null ? 0 : ").append(local).append(");\n");
            }
            case BOXED_BOOLEAN -> {
                source.append("        final Boolean ").append(local).append(" = (Boolean) ").append(valueExpr).append(";\n");
                source.append("        ").append(encoderRef).append(".").append(schemaName(field)).append("().present((short) (").append(local).append(" != null ? 1 : 0)).value(")
                        .append(local).append(" != null && ").append(local).append(" ? (short) 1 : (short) 0);\n");
            }
            case BOXED_CHAR -> {
                source.append("        final Character ").append(local).append(" = (Character) ").append(valueExpr).append(";\n");
                source.append("        if (").append(local).append(" != null) requireLatin1Char(").append(local).append(", \"").append(field.name()).append("\");\n");
                source.append("        ").append(encoderRef).append(".").append(schemaName(field)).append("().present((short) (").append(local).append(" != null ? 1 : 0)).value(")
                        .append(local).append(" == null ? (byte) 0 : (byte) (char) ").append(local).append(");\n");
            }
            case ENUM -> {
                source.append("        final Enum<?> ").append(local).append(" = (Enum<?>) (").append(valueExpr).append(");\n");
                source.append("        ").append(encoderRef).append(".").append(schemaName(field)).append("().present((short) (").append(local).append(" != null ? 1 : 0)).value(")
                        .append(local).append(" == null ? 0 : ").append(local).append(".ordinal());\n");
            }
            case STRING, BIG_INTEGER, BIG_DECIMAL -> {
                source.append("        final String ").append(local).append(" = ").append(field.kind() == SbeFieldKind.STRING ? "(String) (" + valueExpr + ")" : "stringify(" + valueExpr + ")").append(";\n");
                source.append("        ").append(encoderRef).append(".").append(schemaName(field)).append("Present((short) (").append(local).append(" != null ? 1 : 0));\n");
                source.append("        ").append(encoderRef).append(".").append(schemaName(field)).append("(").append(local).append(" == null ? \"\" : ").append(local).append(");\n");
            }
            case BYTES -> {
                source.append("        final byte[] ").append(local).append(" = (byte[]) (").append(valueExpr).append(");\n");
                source.append("        ").append(encoderRef).append(".").append(schemaName(field)).append("Present((short) (").append(local).append(" != null ? 1 : 0));\n");
                source.append("        ").append(encoderRef).append(".put").append(schemaCapitalized(field)).append("(").append(local)
                        .append(" == null ? EMPTY_BYTES : ").append(local).append(", 0, ").append(local).append(" == null ? 0 : ").append(local).append(".length);\n");
            }
            case FIXED_STRING -> {
                source.append("        final String ").append(local).append(" = (String) (").append(valueExpr).append(");\n");
                source.append("        final var ").append(local).append("_wrapper = ").append(encoderRef).append(".").append(schemaName(field)).append("();\n");
                source.append("        ").append(local).append("_wrapper.present((short) (").append(local).append(" != null ? 1 : 0));\n");
                source.append("        if (").append(local).append(" == null) {\n");
                source.append("            ").append(local).append("_wrapper.length(0).value(\"\");\n");
                source.append("        } else {\n");
                source.append("            encodeFixedLatin1(").append(local).append(", ").append(field.fixedLength()).append(", \"").append(field.name()).append("\");\n");
                source.append("            ").append(local).append("_wrapper.length(").append(local).append(".length()).value(").append(local).append(");\n");
                source.append("        }\n");
            }
            case FIXED_BYTES -> {
                source.append("        final byte[] ").append(local).append(" = (byte[]) (").append(valueExpr).append(");\n");
                source.append("        final var ").append(local).append("_wrapper = ").append(encoderRef).append(".").append(schemaName(field)).append("();\n");
                source.append("        ").append(local).append("_wrapper.present((short) (").append(local).append(" != null ? 1 : 0));\n");
                source.append("        if (").append(local).append(" == null) {\n");
                source.append("            ").append(local).append("_wrapper.length(0).putValue(EMPTY_BYTES, 0, 0);\n");
                source.append("        } else {\n");
                source.append("            requireFixedBytesLength(").append(local).append(", ").append(field.fixedLength()).append(", \"").append(field.name()).append("\");\n");
                source.append("            ").append(local).append("_wrapper.length(").append(local).append(".length).putValue(").append(local).append(", 0, ").append(local).append(".length);\n");
                source.append("        }\n");
            }
            case NESTED_FIXED -> emitEncodeFlattenedNestedField(source, field, encoderRef, valueExpr, "");
            case UUID, INSTANT, LOCAL_DATE, LOCAL_TIME, LOCAL_DATE_TIME, OFFSET_DATE_TIME, OFFSET_TIME, DURATION,
                 PERIOD, YEAR, YEAR_MONTH, MONTH_DAY, ZONE_OFFSET -> {
                source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(local).append(" = (")
                        .append(SbeNaming.javaTypeName(field.javaType())).append(") ").append(valueExpr).append(";\n");
                source.append("        final var ").append(local).append("_wrapper = ").append(encoderRef).append(".").append(schemaName(field)).append("();\n");
                source.append("        ").append(local).append("_wrapper.present((short) (").append(local).append(" != null ? 1 : 0));\n");
                source.append("        if (").append(local).append(" != null) {\n");
                source.append("            encode").append(logicalHelperSuffix(field.kind())).append("(").append(local).append("_wrapper.value(), ").append(local).append(");\n");
                source.append("        }\n");
            }
            default -> throw new IllegalArgumentException("Unsupported synthetic field encode kind: " + field.kind());
        }
    }

    private void emitDecodeSyntheticField(
            final StringBuilder source,
            final SbeFieldSpec field,
            final String decoderRef,
            final String targetVar
    ) {
        switch (field.kind()) {
            case PRIMITIVE ->
                    source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(targetVar)
                            .append(" = ").append(decoderRef).append(".").append(schemaName(field)).append("();\n");
            case BOOLEAN ->
                    source.append("        final boolean ").append(targetVar).append(" = ").append(decoderRef).append(".")
                            .append(schemaName(field)).append("() != 0;\n");
            case CHAR ->
                    source.append("        final char ").append(targetVar).append(" = (char) (").append(decoderRef).append(".")
                            .append(schemaName(field)).append("() & 0xFF);\n");
            case BOXED_PRIMITIVE ->
                    source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(targetVar)
                            .append(" = ").append(decoderRef).append(".").append(schemaName(field)).append("().present() == 0 ? null : ")
                            .append(decoderRef).append(".").append(schemaName(field)).append("().value();\n");
            case BOXED_BOOLEAN ->
                    source.append("        final Boolean ").append(targetVar).append(" = ").append(decoderRef).append(".")
                            .append(schemaName(field)).append("().present() == 0 ? null : ").append(decoderRef).append(".").append(schemaName(field)).append("().value() != 0;\n");
            case BOXED_CHAR ->
                    source.append("        final Character ").append(targetVar).append(" = ").append(decoderRef).append(".")
                            .append(schemaName(field)).append("().present() == 0 ? null : (char) (").append(decoderRef).append(".").append(schemaName(field)).append("().value() & 0xFF);\n");
            case ENUM -> {
                source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(targetVar).append(";\n");
                source.append("        if (").append(decoderRef).append(".").append(schemaName(field)).append("().present() == 0) {\n");
                source.append("            ").append(targetVar).append(" = null;\n");
                source.append("        } else {\n");
                source.append("            final int ordinal = ").append(decoderRef).append(".").append(schemaName(field)).append("().value();\n");
                source.append("            ").append(targetVar).append(" = decodeEnum(").append(enumValuesField(field.javaType())).append(", ordinal, \"").append(field.name()).append("\");\n");
                source.append("        }\n");
            }
            case STRING -> {
                source.append("        final String raw_").append(targetVar).append(" = ").append(decoderRef).append(".").append(schemaName(field)).append("Present() == 0 ? null : ")
                        .append(decoderRef).append(".").append(schemaName(field)).append("();\n");
                source.append("        if (").append(decoderRef).append(".").append(schemaName(field)).append("Present() == 0) ").append(decoderRef).append(".").append(schemaName(field)).append("();\n");
                source.append("        final String ").append(targetVar).append(" = raw_").append(targetVar).append(";\n");
            }
            case BIG_INTEGER -> {
                source.append("        final String raw_").append(targetVar).append(" = ").append(decoderRef).append(".").append(schemaName(field)).append("Present() == 0 ? null : ")
                        .append(decoderRef).append(".").append(schemaName(field)).append("();\n");
                source.append("        if (").append(decoderRef).append(".").append(schemaName(field)).append("Present() == 0) ").append(decoderRef).append(".").append(schemaName(field)).append("();\n");
                source.append("        final java.math.BigInteger ").append(targetVar).append(" = raw_").append(targetVar).append(" == null ? null : new java.math.BigInteger(raw_").append(targetVar).append(");\n");
            }
            case BIG_DECIMAL -> {
                source.append("        final String raw_").append(targetVar).append(" = ").append(decoderRef).append(".").append(schemaName(field)).append("Present() == 0 ? null : ")
                        .append(decoderRef).append(".").append(schemaName(field)).append("();\n");
                source.append("        if (").append(decoderRef).append(".").append(schemaName(field)).append("Present() == 0) ").append(decoderRef).append(".").append(schemaName(field)).append("();\n");
                source.append("        final java.math.BigDecimal ").append(targetVar).append(" = raw_").append(targetVar).append(" == null ? null : new java.math.BigDecimal(raw_").append(targetVar).append(");\n");
            }
            case BYTES -> {
                source.append("        final boolean present_").append(targetVar).append(" = ").append(decoderRef).append(".").append(schemaName(field)).append("Present() != 0;\n");
                source.append("        final byte[] ").append(targetVar).append(";\n");
                source.append("        {\n");
                source.append("            final int dataLength = ").append(decoderRef).append(".").append(schemaName(field)).append("Length();\n");
                source.append("            final byte[] tmp = new byte[dataLength];\n");
                source.append("            ").append(decoderRef).append(".get").append(schemaCapitalized(field)).append("(tmp, 0, dataLength);\n");
                source.append("            ").append(targetVar).append(" = present_").append(targetVar).append(" ? tmp : null;\n");
                source.append("        }\n");
            }
            case FIXED_STRING ->
                    source.append("        final String ").append(targetVar).append(" = ").append(decoderRef).append(".")
                            .append(schemaName(field)).append("().present() == 0 ? null : ").append(decoderRef).append(".").append(schemaName(field)).append("().value();\n");
            case FIXED_BYTES -> {
                source.append("        final byte[] ").append(targetVar).append(";\n");
                source.append("        if (").append(decoderRef).append(".").append(schemaName(field)).append("().present() == 0) {\n");
                source.append("            ").append(targetVar).append(" = null;\n");
                source.append("        } else {\n");
                source.append("            final int length = ").append(decoderRef).append(".").append(schemaName(field)).append("().length();\n");
                source.append("            final byte[] tmp = new byte[length];\n");
                source.append("            ").append(decoderRef).append(".").append(schemaName(field)).append("().getValue(tmp, 0, length);\n");
                source.append("            ").append(targetVar).append(" = length == tmp.length ? tmp : java.util.Arrays.copyOf(tmp, length);\n");
                source.append("        }\n");
            }
            case NESTED_FIXED -> emitDecodeFlattenedNestedField(source, field, decoderRef, "");
            case UUID, INSTANT, LOCAL_DATE, LOCAL_TIME, LOCAL_DATE_TIME, OFFSET_DATE_TIME, OFFSET_TIME, DURATION,
                 PERIOD, YEAR, YEAR_MONTH, MONTH_DAY, ZONE_OFFSET ->
                    source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(targetVar).append(" = ")
                            .append(decoderRef).append(".").append(schemaName(field)).append("().present() == 0 ? null : decode")
                            .append(logicalHelperSuffix(field.kind())).append("(").append(decoderRef).append(".").append(schemaName(field)).append("().value());\n");
            default -> throw new IllegalArgumentException("Unsupported synthetic field decode kind: " + field.kind());
        }
    }

    private void emitEncodeSyntheticDynamicField(
            final StringBuilder source,
            final SbeFieldSpec field,
            final String encoderRef,
            final String valueExpr
    ) {
        if (field == null || field.kind() != SbeFieldKind.NESTED_FIXED || field.nestedType() == null) {
            return;
        }
        emitEncodeStructuredDynamicFields(
                source,
                field.nestedType(),
                encoderRef,
                "(" + SbeNaming.javaTypeName(field.javaType()) + ") (" + valueExpr + ")",
                field.name(),
                true
        );
    }

    private void emitMeasureSyntheticField(final StringBuilder source, final SbeFieldSpec field, final String valueExpr) {
        if (field.kind() == SbeFieldKind.NESTED_FIXED) {
            source.append("        final int size = 1 + measureStructured").append(SbeNaming.sanitize(field.nestedType().javaType().getName()))
                    .append("((").append(SbeNaming.javaTypeName(field.javaType())).append(") ").append(valueExpr).append(");\n");
            source.append("        return size;\n");
            return;
        }
        source.append("        final int size = ").append(syntheticMeasureExpr(field, valueExpr)).append(";\n");
        source.append("        return size;\n");
    }

    private String syntheticMeasureExpr(final SbeFieldSpec field, final String valueExpr) {
        return switch (field.kind()) {
            case PRIMITIVE -> Integer.toString(primitiveSize(field.javaType()));
            case BOOLEAN, CHAR -> "1";
            case BOXED_PRIMITIVE -> Integer.toString(1 + primitiveSize(unbox(field.javaType())));
            case BOXED_BOOLEAN, BOXED_CHAR -> "2";
            case ENUM -> "5";
            case FIXED_STRING, FIXED_BYTES -> Integer.toString(3 + field.fixedLength());
            case UUID -> "17";
            case INSTANT -> "13";
            case LOCAL_DATE -> "5";
            case LOCAL_TIME -> "9";
            case LOCAL_DATE_TIME -> "13";
            case OFFSET_DATE_TIME -> "17";
            case OFFSET_TIME -> "13";
            case DURATION -> "13";
            case PERIOD -> "13";
            case YEAR -> "5";
            case YEAR_MONTH -> "6";
            case MONTH_DAY -> "3";
            case ZONE_OFFSET -> "5";
            case STRING -> "1 + measureVariableString((String) " + valueExpr + ")";
            case BIG_INTEGER, BIG_DECIMAL -> "1 + measureVariableString(stringify(" + valueExpr + "))";
            case BYTES -> "1 + measureVariableBytes((byte[]) " + valueExpr + ")";
            case NESTED_FIXED -> "1 + measureStructured" + SbeNaming.sanitize(field.nestedType().javaType().getName())
                                 + "((" + SbeNaming.javaTypeName(field.javaType()) + ") " + valueExpr + ")";
            default -> "0";
        };
    }

    private void emitEncodeStructuredFields(
            final StringBuilder source,
            final SbeTypeSpec spec,
            final String encoderRef,
            final String objectRef,
            final String rawPrefix,
            final boolean objectNullable
    ) {
        emitEncodeStructuredFixedFields(source, spec, encoderRef, objectRef, rawPrefix, objectNullable);
        emitEncodeStructuredDynamicFields(source, spec, encoderRef, objectRef, rawPrefix, objectNullable);
    }

    private void emitEncodeStructuredFixedFields(
            final StringBuilder source,
            final SbeTypeSpec spec,
            final String encoderRef,
            final String objectRef,
            final String rawPrefix,
            final boolean objectNullable
    ) {
        for (final SbeFieldSpec field : spec.fields()) {
            if (field.kind() == SbeFieldKind.NESTED_FIXED) {
                emitEncodeFlattenedNestedField(source, field, encoderRef, nestedObjectValueExpr(field, objectRef, objectNullable), rawPrefix);
                continue;
            }
            if (field.variableLength() || field.repeatingGroup()) {
                continue;
            }
            emitEncodeSyntheticField(
                    source,
                    renamedField(field, flattenedName(rawPrefix, field)),
                    encoderRef,
                    guardedFieldValueExpr(field, objectRef, objectNullable)
            );
        }
    }

    private void emitEncodeStructuredDynamicFields(
            final StringBuilder source,
            final SbeTypeSpec spec,
            final String encoderRef,
            final String objectRef,
            final String rawPrefix,
            final boolean objectNullable
    ) {
        emitEncodeStructuredGroupFields(source, spec, encoderRef, objectRef, rawPrefix, objectNullable);
        emitEncodeStructuredDataFields(source, spec, encoderRef, objectRef, rawPrefix, objectNullable);
    }

    private void emitEncodeStructuredGroupFields(
            final StringBuilder source,
            final SbeTypeSpec spec,
            final String encoderRef,
            final String objectRef,
            final String rawPrefix,
            final boolean objectNullable
    ) {
        for (final SbeFieldSpec field : spec.fields()) {
            if (field.kind() == SbeFieldKind.NESTED_FIXED) {
                emitEncodeStructuredGroupFields(
                        source,
                        field.nestedType(),
                        encoderRef,
                        nestedObjectValueExpr(field, objectRef, objectNullable),
                        flattenedName(rawPrefix, field),
                        true
                );
                continue;
            }
            if (field.repeatingGroup()) {
                emitEncodeRepeatingField(
                        source,
                        field,
                        encoderRef,
                        guardedReferenceValueExpr(field, objectRef, objectNullable),
                        renamedField(field, flattenedName(rawPrefix, field))
                );
            }
        }
    }

    private void emitEncodeStructuredDataFields(
            final StringBuilder source,
            final SbeTypeSpec spec,
            final String encoderRef,
            final String objectRef,
            final String rawPrefix,
            final boolean objectNullable
    ) {
        for (final SbeFieldSpec field : spec.fields()) {
            if (field.kind() == SbeFieldKind.NESTED_FIXED) {
                emitEncodeStructuredDataFields(
                        source,
                        field.nestedType(),
                        encoderRef,
                        nestedObjectValueExpr(field, objectRef, objectNullable),
                        flattenedName(rawPrefix, field),
                        true
                );
                continue;
            }
            if (field.variableLength()) {
                emitEncodeSyntheticField(
                        source,
                        renamedField(field, flattenedName(rawPrefix, field)),
                        encoderRef,
                        guardedFieldValueExpr(field, objectRef, objectNullable)
                );
            }
        }
    }

    private void emitEncodeFlattenedNestedField(
            final StringBuilder source,
            final SbeFieldSpec field,
            final String encoderRef,
            final String valueExpr,
            final String rawPrefix
    ) {
        final String local = local(field) + "_" + SbeNaming.sanitize(flattenedName(rawPrefix, field));
        final String nestedPrefix = flattenedName(rawPrefix, field);
        source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(local).append(" = (")
                .append(SbeNaming.javaTypeName(field.javaType())).append(") ")
                .append(valueExpr).append(";\n");
        source.append("        ").append(encoderRef).append(".").append(schemaName(nestedPrefix)).append("Present((short) (")
                .append(local).append(" != null ? 1 : 0));\n");
        emitEncodeStructuredFixedFields(source, field.nestedType(), encoderRef, local, nestedPrefix, true);
    }

    private void emitDecodeStructuredFields(
            final StringBuilder source,
            final SbeTypeSpec spec,
            final String decoderRef,
            final String rawPrefix
    ) {
        emitDecodeStructuredFixedFields(source, spec, decoderRef, rawPrefix);
        emitDecodeStructuredGroupFields(source, spec, decoderRef, rawPrefix);
        emitDecodeStructuredDataFields(source, spec, decoderRef, rawPrefix);
        emitInstantiateNestedStructuredValues(source, spec, rawPrefix);
    }

    private void emitDecodeStructuredFixedFields(
            final StringBuilder source,
            final SbeTypeSpec spec,
            final String decoderRef,
            final String rawPrefix
    ) {
        for (final SbeFieldSpec field : spec.fields()) {
            if (field.kind() == SbeFieldKind.NESTED_FIXED) {
                emitDecodeFlattenedNestedFixedFields(source, field, decoderRef, rawPrefix);
                continue;
            }
            if (field.variableLength() || field.repeatingGroup()) {
                continue;
            }
            emitDecodeSyntheticField(source, renamedField(field, flattenedName(rawPrefix, field)), decoderRef, decodedVar(flattenedName(rawPrefix, field)));
        }
    }

    private void emitDecodeStructuredGroupFields(
            final StringBuilder source,
            final SbeTypeSpec spec,
            final String decoderRef,
            final String rawPrefix
    ) {
        for (final SbeFieldSpec field : spec.fields()) {
            if (field.kind() == SbeFieldKind.NESTED_FIXED) {
                emitDecodeStructuredGroupFields(source, field.nestedType(), decoderRef, flattenedName(rawPrefix, field));
                continue;
            }
            if (field.repeatingGroup()) {
                emitDecodeRepeatingField(source, field, decoderRef, renamedField(field, flattenedName(rawPrefix, field)));
            }
        }
    }

    private void emitDecodeStructuredDataFields(
            final StringBuilder source,
            final SbeTypeSpec spec,
            final String decoderRef,
            final String rawPrefix
    ) {
        for (final SbeFieldSpec field : spec.fields()) {
            if (field.kind() == SbeFieldKind.NESTED_FIXED) {
                emitDecodeStructuredDataFields(source, field.nestedType(), decoderRef, flattenedName(rawPrefix, field));
                continue;
            }
            if (field.variableLength()) {
                emitDecodeSyntheticField(source, renamedField(field, flattenedName(rawPrefix, field)), decoderRef, decodedVar(flattenedName(rawPrefix, field)));
            }
        }
    }

    private void emitDecodeFlattenedNestedFixedFields(
            final StringBuilder source,
            final SbeFieldSpec field,
            final String decoderRef,
            final String rawPrefix
    ) {
        final String nestedPrefix = flattenedName(rawPrefix, field);
        final String targetVar = decodedVar(nestedPrefix);
        source.append("        final boolean ").append(targetVar).append("_present = ").append(decoderRef).append(".")
                .append(schemaName(nestedPrefix)).append("Present() != 0;\n");
        emitDecodeStructuredFixedFields(source, field.nestedType(), decoderRef, nestedPrefix);
    }

    private void emitDecodeFlattenedNestedField(
            final StringBuilder source,
            final SbeFieldSpec field,
            final String decoderRef,
            final String rawPrefix
    ) {
        final String nestedPrefix = flattenedName(rawPrefix, field);
        final String targetVar = decodedVar(nestedPrefix);
        emitDecodeFlattenedNestedFixedFields(source, field, decoderRef, rawPrefix);
        emitDecodeStructuredGroupFields(source, field.nestedType(), decoderRef, nestedPrefix);
        emitDecodeStructuredDataFields(source, field.nestedType(), decoderRef, nestedPrefix);
        emitInstantiateNestedStructuredValues(source, field.nestedType(), nestedPrefix);
        source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(targetVar).append(";\n");
        source.append("        if (!").append(targetVar).append("_present) {\n");
        source.append("            ").append(targetVar).append(" = null;\n");
        source.append("        } else {\n");
        emitInstantiateValue(source, field.nestedType(), targetVar, nestedPrefix);
        source.append("        }\n");
    }

    private void emitInstantiateNestedStructuredValues(
            final StringBuilder source,
            final SbeTypeSpec spec,
            final String rawPrefix
    ) {
        for (final SbeFieldSpec field : spec.fields()) {
            if (field.kind() != SbeFieldKind.NESTED_FIXED || field.nestedType() == null) {
                continue;
            }
            final String nestedPrefix = flattenedName(rawPrefix, field);
            emitInstantiateNestedStructuredValues(source, field.nestedType(), nestedPrefix);
            final String targetVar = decodedVar(nestedPrefix);
            source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(targetVar).append(";\n");
            source.append("        if (!").append(targetVar).append("_present) {\n");
            source.append("            ").append(targetVar).append(" = null;\n");
            source.append("        } else {\n");
            emitInstantiateValue(source, field.nestedType(), targetVar, nestedPrefix);
            source.append("        }\n");
        }
    }

    private void emitInstantiateValue(
            final StringBuilder source,
            final SbeTypeSpec spec,
            final String targetVar,
            final String rawPrefix
    ) {
        if (spec.instantiationStyle() == SbeInstantiationStyle.RECORD) {
            source.append("            ").append(targetVar).append(" = new ").append(SbeNaming.javaTypeName(spec.javaType())).append("(");
            for (int index = 0; index < spec.fields().size(); index++) {
                if (index > 0) {
                    source.append(", ");
                }
                final SbeFieldSpec field = spec.fields().get(index);
                final String valueName = decodedVar(flattenedName(rawPrefix, field));
                if (field.optional()) {
                    source.append("optionalOfNullable(").append(valueName).append(")");
                } else {
                    source.append(valueName);
                }
            }
            source.append(");\n");
            return;
        }
        final String localValue = targetVar + "_value";
        source.append("            final ").append(SbeNaming.javaTypeName(spec.javaType())).append(" ").append(localValue)
                .append(" = ReflectionSupport.instantiate(").append(constructorField(spec.javaType())).append(");\n");
        for (final SbeFieldSpec field : spec.fields()) {
            final String valueName = decodedVar(flattenedName(rawPrefix, field));
            source.append("            ").append(handle(field)).append(".set(").append(localValue).append(", ");
            if (field.optional()) {
                source.append("optionalOfNullable(").append(valueName).append(")");
            } else {
                source.append(valueName);
            }
            source.append(");\n");
        }
        source.append("            ").append(targetVar).append(" = ").append(localValue).append(";\n");
    }

    private void emitStructuredMeasureHelpers(final StringBuilder source, final SbeTypeSpec spec, final Set<Class<?>> visited) {
        if (!visited.add(spec.javaType())) {
            return;
        }
        source.append("    private static int measureStructured").append(SbeNaming.sanitize(spec.javaType().getName())).append("(final ")
                .append(SbeNaming.javaTypeName(spec.javaType())).append(" value) {\n");
        source.append("        int size = ").append(fixedSize(spec)).append(";\n");
        source.append("        size += measureStructuredDynamic").append(SbeNaming.sanitize(spec.javaType().getName())).append("(value);\n");
        source.append("        return size;\n");
        source.append("    }\n\n");
        source.append("    private static int measureStructuredDynamic").append(SbeNaming.sanitize(spec.javaType().getName())).append("(final ")
                .append(SbeNaming.javaTypeName(spec.javaType())).append(" value) {\n");
        source.append("        if (value == null) return 0;\n");
        source.append("        int size = 0;\n");
        emitMeasureStructuredDynamic(source, spec, "value");
        source.append("        return size;\n");
        source.append("    }\n\n");
        for (final SbeFieldSpec field : spec.fields()) {
            if ((field.kind() == SbeFieldKind.NESTED_FIXED || field.kind() == SbeFieldKind.REPEATING_GROUP) && field.nestedType() != null) {
                emitStructuredMeasureHelpers(source, field.nestedType(), visited);
            }
            if (field.kind() == SbeFieldKind.MAP) {
                if (field.mapKeySpec() != null && field.mapKeySpec().kind() == SbeFieldKind.NESTED_FIXED && field.mapKeySpec().nestedType() != null) {
                    emitStructuredMeasureHelpers(source, field.mapKeySpec().nestedType(), visited);
                }
                if (field.mapValueSpec() != null && field.mapValueSpec().kind() == SbeFieldKind.NESTED_FIXED && field.mapValueSpec().nestedType() != null) {
                    emitStructuredMeasureHelpers(source, field.mapValueSpec().nestedType(), visited);
                }
            }
        }
    }

    private void emitMeasureStructuredDynamic(final StringBuilder source, final SbeTypeSpec spec, final String objectRef) {
        for (final SbeFieldSpec field : spec.fields()) {
            if (field.kind() == SbeFieldKind.NESTED_FIXED && field.nestedType() != null) {
                source.append("        size += measureStructuredDynamic").append(SbeNaming.sanitize(field.nestedType().javaType().getName()))
                        .append("((").append(SbeNaming.javaTypeName(field.javaType())).append(") ")
                        .append(nestedObjectValueExpr(field, objectRef, true)).append(");\n");
                continue;
            }
            if (field.kind() == SbeFieldKind.STRING || field.kind() == SbeFieldKind.BIG_INTEGER || field.kind() == SbeFieldKind.BIG_DECIMAL) {
                source.append("        size += measureVariableString(").append(variableStringGuardedValueExpr(field, objectRef)).append(");\n");
            } else if (field.kind() == SbeFieldKind.BYTES) {
                source.append("        size += measureVariableBytes(").append(variableBytesGuardedValueExpr(field, objectRef)).append(");\n");
            } else if (field.kind() == SbeFieldKind.REPEATING_GROUP) {
                source.append("        size += 4 + measureGroup").append(helperSuffix(field))
                        .append("(").append(guardedReferenceValueExpr(field, objectRef, true)).append(");\n");
            } else if (field.kind() == SbeFieldKind.REPEATING_SCALAR) {
                source.append("        size += 4 + measureScalarGroup").append(helperSuffix(field))
                        .append("(").append(guardedReferenceValueExpr(field, objectRef, true)).append(");\n");
            } else if (field.kind() == SbeFieldKind.MAP) {
                source.append("        size += 4 + measureMapGroup").append(helperSuffix(field))
                        .append("(").append(guardedReferenceValueExpr(field, objectRef, true)).append(");\n");
            }
        }
    }

    private void emitLogicalEncode(
            final StringBuilder source,
            final SbeFieldSpec field,
            final String encoderRef,
            final String objectRef
    ) {
        final String local = local(field);
        source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(local).append(" = (")
                .append(SbeNaming.javaTypeName(field.javaType())).append(") ").append(referenceValueExpr(field, objectRef)).append(";\n");
        source.append("        final var ").append(local).append("_wrapper = ").append(encoderRef).append(".").append(schemaName(field)).append("();\n");
        source.append("        ").append(local).append("_wrapper.present((short) (").append(local).append(" != null ? 1 : 0));\n");
        source.append("        if (").append(local).append(" != null) {\n");
        source.append("            encode").append(logicalHelperSuffix(field.kind())).append("(").append(local).append("_wrapper.value(), ").append(local).append(");\n");
        source.append("        }\n");
    }

    private void emitEncodeDynamicFields(
            final StringBuilder source,
            final SbeTypeSpec spec,
            final String rootEncoderType,
            final String encoderRef,
            final String objectRef
    ) {
        emitEncodeStructuredGroupFields(source, spec, encoderRef, objectRef, "", false);
        emitEncodeStructuredDataFields(source, spec, encoderRef, objectRef, "", false);
    }

    private void emitEncodeRepeatingField(
            final StringBuilder source,
            final SbeFieldSpec field,
            final String encoderRef,
            final String valueExpr,
            final SbeFieldSpec schemaField
    ) {
        final String local = local(schemaField);
        final String helper = helperSuffix(field);
        source.append("        final Object ").append(local).append(" = ").append(valueExpr).append(";\n");
        source.append("        final var ").append(local).append("_group = ").append(encoderRef).append(".").append(schemaName(schemaField))
                .append("Count(count").append(helper).append("(").append(local).append("));\n");
        if (field.kind() == SbeFieldKind.MAP) {
            final String entryVar = local + "_entry";
            source.append("        if (").append(local).append(" != null) {\n");
            source.append("            for (final java.util.Map.Entry<?, ?> ").append(entryVar).append(" : ((java.util.Map<?, ?>) ").append(local).append(").entrySet()) {\n");
            source.append("                encodeMapGroup").append(helper).append("(").append(local).append("_group.next(), ").append(entryVar).append(");\n");
            source.append("            }\n");
            source.append("        }\n");
            return;
        }
        if (field.javaType().isArray()) {
            final String elementType = field.kind() == SbeFieldKind.REPEATING_GROUP
                    ? SbeNaming.javaTypeName(field.nestedType().javaType())
                    : SbeNaming.javaTypeName(field.elementSpec().javaType());
            final String encodeMethod = field.kind() == SbeFieldKind.REPEATING_GROUP ? "encodeGroup" : "encodeScalarGroup";
            source.append("        if (").append(local).append(" != null) {\n");
            source.append("            for (final ").append(elementType).append(" item : (").append(elementType).append("[]) ").append(local).append(") {\n");
            source.append("                ").append(encodeMethod).append(helper).append("(").append(local).append("_group.next(), item);\n");
            source.append("            }\n");
            source.append("        }\n");
            return;
        }
        final String itemType = field.kind() == SbeFieldKind.REPEATING_GROUP
                ? SbeNaming.javaTypeName(field.nestedType().javaType())
                : "Object";
        final String encodeMethod = field.kind() == SbeFieldKind.REPEATING_GROUP ? "encodeGroup" : "encodeScalarGroup";
        source.append("        if (").append(local).append(" != null) {\n");
        source.append("            for (final ").append(itemType).append(" item : (java.util.Collection<")
                .append(itemType).append(">) ").append(local).append(") {\n");
        source.append("                ").append(encodeMethod).append(helper).append("(").append(local).append("_group.next(), item);\n");
        source.append("            }\n");
        source.append("        }\n");
    }

    private void emitRootDecodeMethod(final StringBuilder source, final SbeTypeSpec spec, final String rootDecoderType) {
        source.append("    private static ").append(SbeNaming.javaTypeName(spec.javaType())).append(" decode")
                .append(SbeNaming.sanitize(spec.javaType().getName())).append("(final ").append(rootDecoderType).append(" decoder) {\n");
        emitDecodeStructuredFields(source, spec, "decoder", "");
        emitInstantiate(source, spec);
        source.append("    }\n\n");
    }

    private void emitGroupHelpers(final StringBuilder source, final SbeTypeSpec spec, final String officialPackage) {
        emitGroupHelpersRecursive(
                source,
                spec,
                officialPackage + "." + SbeNaming.messageEncoderSimpleName(spec),
                officialPackage + "." + SbeNaming.messageDecoderSimpleName(spec),
                "",
                new HashSet<>(),
                new HashSet<>()
        );
    }

    private void emitGroupHelpersRecursive(
            final StringBuilder source,
            final SbeTypeSpec spec,
            final String enclosingEncoderType,
            final String enclosingDecoderType,
            final String rawPrefix,
            final Set<String> emittedContexts,
            final Set<String> emittedHelperBodies
    ) {
        for (final SbeFieldSpec field : spec.fields()) {
            if (field.kind() == SbeFieldKind.NESTED_FIXED && field.nestedType() != null) {
                emitGroupHelpersRecursive(
                        source,
                        field.nestedType(),
                        enclosingEncoderType,
                        enclosingDecoderType,
                        flattenedName(rawPrefix, field),
                        emittedContexts,
                        emittedHelperBodies
                );
                continue;
            }
            if (!field.repeatingGroup()) {
                continue;
            }
            final String helper = helperSuffix(field);
            final SbeFieldSpec schemaField = renamedField(field, flattenedName(rawPrefix, field));
            final String groupEncoderType = enclosingEncoderType + "." + schemaCapitalized(schemaField) + "Encoder";
            final String groupDecoderType = enclosingDecoderType + "." + schemaCapitalized(schemaField) + "Decoder";
            if (field.kind() == SbeFieldKind.REPEATING_GROUP) {
                if (emittedContexts.add("encodeGroup|" + helper + "|" + groupEncoderType)) {
                    source.append("    private static void encodeGroup").append(helper).append("(final ").append(groupEncoderType).append(" encoder, final ")
                            .append(SbeNaming.javaTypeName(field.nestedType().javaType())).append(" value) {\n");
                    emitEncodeStructuredFields(source, field.nestedType(), "encoder", "value", "", false);
                    source.append("    }\n\n");
                }
                if (emittedContexts.add("decodeGroup|" + helper + "|" + groupDecoderType)) {
                    source.append("    private static ").append(SbeNaming.javaTypeName(field.nestedType().javaType())).append(" decodeGroup").append(helper)
                            .append("(final ").append(groupDecoderType).append(" decoder) {\n");
                    emitDecodeStructuredFields(source, field.nestedType(), "decoder", "");
                    emitInstantiate(source, field.nestedType());
                    source.append("    }\n\n");
                }
                if (emittedHelperBodies.add("group|" + helper)) {
                    source.append("    private static int measureGroup").append(helper).append("(final Object value) {\n");
                    source.append("        if (value == null) return 0;\n");
                    source.append("        int size = 0;\n");
                    if (field.javaType().isArray()) {
                        source.append("        for (final ").append(SbeNaming.javaTypeName(field.nestedType().javaType())).append(" item : (")
                                .append(SbeNaming.javaTypeName(field.nestedType().javaType())).append("[]) value) {\n");
                    } else {
                        source.append("        for (final ").append(SbeNaming.javaTypeName(field.nestedType().javaType())).append(" item : (java.util.Collection<")
                                .append(SbeNaming.javaTypeName(field.nestedType().javaType())).append(">) value) {\n");
                    }
                    source.append("            size += measureGroupItem").append(helper).append("(item);\n");
                    source.append("        }\n");
                    source.append("        return size;\n");
                    source.append("    }\n\n");
                    source.append("    private static int measureGroupItem").append(helper).append("(final ")
                            .append(SbeNaming.javaTypeName(field.nestedType().javaType())).append(" value) {\n");
                    source.append("        return measureStructured").append(SbeNaming.sanitize(field.nestedType().javaType().getName())).append("(value);\n");
                    source.append("    }\n\n");
                    emitCollectionCountAndFactory(source, field, helper, SbeNaming.javaTypeName(field.nestedType().javaType()));
                }
                emitGroupHelpersRecursive(source, field.nestedType(), groupEncoderType, groupDecoderType, "", emittedContexts, emittedHelperBodies);
            } else if (field.kind() == SbeFieldKind.REPEATING_SCALAR) {
                if (emittedContexts.add("encodeScalarGroup|" + helper + "|" + groupEncoderType)) {
                    source.append("    private static void encodeScalarGroup").append(helper).append("(final ").append(groupEncoderType).append(" encoder, final Object value) {\n");
                    emitEncodeSyntheticField(source, field.elementSpec(), "encoder", "value");
                    source.append("    }\n\n");
                }
                if (emittedContexts.add("decodeScalarGroup|" + helper + "|" + groupDecoderType)) {
                    source.append("    private static ").append(SbeNaming.javaTypeName(field.elementSpec().javaType())).append(" decodeScalarGroup").append(helper)
                            .append("(final ").append(groupDecoderType).append(" decoder) {\n");
                    emitDecodeSyntheticField(source, field.elementSpec(), "decoder", "decoded_value");
                    source.append("        return decoded_value;\n");
                    source.append("    }\n\n");
                }
                if (emittedHelperBodies.add("scalar|" + helper)) {
                    source.append("    private static int measureScalarGroup").append(helper).append("(final Object value) {\n");
                    source.append("        if (value == null) return 0;\n");
                    source.append("        int size = 0;\n");
                    if (field.javaType().isArray()) {
                        source.append("        for (final ").append(SbeNaming.javaTypeName(field.elementSpec().javaType())).append(" item : (")
                                .append(SbeNaming.javaTypeName(field.elementSpec().javaType())).append("[]) value) {\n");
                    } else {
                        source.append("        for (final Object item : (java.util.Collection<?>) value) {\n");
                    }
                    source.append("            size += measureSyntheticField").append(helper).append("(item);\n");
                    source.append("        }\n");
                    source.append("        return size;\n");
                    source.append("    }\n\n");
                    source.append("    private static int measureSyntheticField").append(helper).append("(final Object value) {\n");
                    emitMeasureSyntheticField(source, field.elementSpec(), "value");
                    source.append("    }\n\n");
                    emitCollectionCountAndFactory(source, field, helper, SbeNaming.javaTypeName(field.elementSpec().javaType()));
                }
            } else if (field.kind() == SbeFieldKind.MAP) {
                if (emittedContexts.add("encodeMapGroup|" + helper + "|" + groupEncoderType)) {
                    source.append("    private static void encodeMapGroup").append(helper).append("(final ").append(groupEncoderType).append(" encoder, final java.util.Map.Entry<?, ?> entry) {\n");
                    emitEncodeSyntheticField(source, field.mapKeySpec(), "encoder", "entry.getKey()");
                    emitEncodeSyntheticField(source, field.mapValueSpec(), "encoder", "entry.getValue()");
                    emitEncodeSyntheticDynamicField(source, field.mapKeySpec(), "encoder", "entry.getKey()");
                    emitEncodeSyntheticDynamicField(source, field.mapValueSpec(), "encoder", "entry.getValue()");
                    source.append("    }\n\n");
                }
                if (emittedContexts.add("decodeMapGroup|" + helper + "|" + groupDecoderType)) {
                    source.append("    private static java.util.Map.Entry<").append(SbeNaming.javaTypeName(field.mapKeySpec().javaType())).append(", ")
                            .append(SbeNaming.javaTypeName(field.mapValueSpec().javaType())).append("> decodeMapGroup").append(helper)
                            .append("(final ").append(groupDecoderType).append(" decoder) {\n");
                    emitDecodeSyntheticField(source, field.mapKeySpec(), "decoder", "decoded_key");
                    emitDecodeSyntheticField(source, field.mapValueSpec(), "decoder", "decoded_value");
                    source.append("        return new java.util.AbstractMap.SimpleImmutableEntry<>(decoded_key, decoded_value);\n");
                    source.append("    }\n\n");
                }
                if (emittedHelperBodies.add("map|" + helper)) {
                    source.append("    private static int measureMapGroup").append(helper).append("(final Object value) {\n");
                    source.append("        if (value == null) return 0;\n");
                    source.append("        int size = 0;\n");
                    source.append("        for (final java.util.Map.Entry<?, ?> entry : ((java.util.Map<?, ?>) value).entrySet()) {\n");
                    source.append("            size += measureMapEntry").append(helper).append("(entry);\n");
                    source.append("        }\n");
                    source.append("        return size;\n");
                    source.append("    }\n\n");
                    source.append("    private static int measureMapEntry").append(helper).append("(final java.util.Map.Entry<?, ?> entry) {\n");
                    source.append("        return ")
                            .append(syntheticMeasureExpr(field.mapKeySpec(), "entry.getKey()"))
                            .append(" + ")
                            .append(syntheticMeasureExpr(field.mapValueSpec(), "entry.getValue()"))
                            .append(";\n");
                    source.append("    }\n\n");
                    source.append("    private static int count").append(helper).append("(final Object value) {\n");
                    source.append("        return value == null ? 0 : ((java.util.Map<?, ?>) value).size();\n");
                    source.append("    }\n\n");
                    source.append("    @SuppressWarnings(\"unchecked\")\n");
                    source.append("    private static java.util.Map<").append(SbeNaming.javaTypeName(field.mapKeySpec().javaType())).append(", ")
                            .append(SbeNaming.javaTypeName(field.mapValueSpec().javaType())).append("> createMap").append(helper).append("(final int expectedSize) {\n");
                    if (field.javaType().isInterface()) {
                        if (field.javaType() == java.util.SortedMap.class || field.javaType() == java.util.NavigableMap.class) {
                            source.append("        return new java.util.TreeMap<>();\n");
                        } else {
                            source.append("        return new java.util.LinkedHashMap<>(Math.max(16, expectedSize * 2));\n");
                        }
                    } else {
                        source.append("        return (java.util.Map<").append(SbeNaming.javaTypeName(field.mapKeySpec().javaType())).append(", ")
                                .append(SbeNaming.javaTypeName(field.mapValueSpec().javaType())).append(">) ReflectionSupport.instantiate(")
                                .append(collectionConstructorField(field)).append(");\n");
                    }
                    source.append("    }\n\n");
                }
                emitGroupHelpersForSyntheticNested(source, field.mapKeySpec(), groupEncoderType, groupDecoderType, emittedContexts, emittedHelperBodies);
                emitGroupHelpersForSyntheticNested(source, field.mapValueSpec(), groupEncoderType, groupDecoderType, emittedContexts, emittedHelperBodies);
            }
        }
    }

    private void emitGroupHelpersForSyntheticNested(
            final StringBuilder source,
            final SbeFieldSpec field,
            final String enclosingEncoderType,
            final String enclosingDecoderType,
            final Set<String> emittedContexts,
            final Set<String> emittedHelperBodies
    ) {
        if (field == null || field.kind() != SbeFieldKind.NESTED_FIXED || field.nestedType() == null) {
            return;
        }
        emitGroupHelpersRecursive(
                source,
                field.nestedType(),
                enclosingEncoderType,
                enclosingDecoderType,
                field.name(),
                emittedContexts,
                emittedHelperBodies
        );
    }

    private void emitCollectionCountAndFactory(
            final StringBuilder source,
            final SbeFieldSpec field,
            final String helper,
            final String elementTypeName
    ) {
        source.append("    private static int count").append(helper).append("(final Object value) {\n");
        source.append("        if (value == null) return 0;\n");
        if (field.javaType().isArray()) {
            source.append("        return ((").append(elementTypeName).append("[]) value).length;\n");
        } else {
            source.append("        return ((java.util.Collection<?>) value).size();\n");
        }
        source.append("    }\n\n");
        if (isPrimitiveScalarArrayField(field)) {
            return;
        }
        source.append("    @SuppressWarnings(\"unchecked\")\n");
        source.append("    private static java.util.Collection<").append(elementTypeName).append("> createCollection")
                .append(helper).append("(final int expectedSize) {\n");
        if (field.javaType().isInterface()) {
            if (field.javaType() == Set.class) {
                source.append("        return new java.util.LinkedHashSet<>(Math.max(16, expectedSize * 2));\n");
            } else if (field.javaType() == java.util.SortedSet.class || field.javaType() == java.util.NavigableSet.class) {
                source.append("        return new java.util.TreeSet<>();\n");
            } else if (field.javaType() == Queue.class || field.javaType() == Deque.class) {
                source.append("        return new java.util.ArrayDeque<>(Math.max(1, expectedSize));\n");
            } else {
                source.append("        return new java.util.ArrayList<>(Math.max(0, expectedSize));\n");
            }
        } else if (!field.javaType().isArray()) {
            source.append("        return (java.util.Collection<").append(elementTypeName).append(">) ")
                    .append("ReflectionSupport.instantiate(").append(collectionConstructorField(field)).append(");\n");
        } else {
            source.append("        return new java.util.ArrayList<>(Math.max(0, expectedSize));\n");
        }
        source.append("    }\n\n");
    }

    private void emitDecodeRepeatingField(
            final StringBuilder source,
            final SbeFieldSpec field,
            final String decoderRef,
            final SbeFieldSpec schemaField
    ) {
        final String helper = helperSuffix(field);
        final String targetName = decodedVar(schemaField.name());
        source.append("        final var ").append(targetName).append("_group = ").append(decoderRef).append(".").append(schemaName(schemaField)).append("();\n");
        if (field.kind() == SbeFieldKind.REPEATING_GROUP) {
            source.append("        final java.util.Collection<").append(SbeNaming.javaTypeName(field.nestedType().javaType())).append("> ")
                    .append(targetName).append("_items = createCollection").append(helper).append("(").append(targetName).append("_group.count());\n");
            source.append("        while (").append(targetName).append("_group.hasNext()) {\n");
            source.append("            ").append(targetName).append("_items.add(decodeGroup").append(helper).append("(").append(targetName).append("_group.next()));\n");
            source.append("        }\n");
            if (field.javaType().isArray()) {
                source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(targetName)
                        .append(" = ").append(targetName).append("_items.toArray(")
                        .append(zeroLengthArrayExpr(field.nestedType().javaType())).append(");\n");
            } else {
                source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(targetName)
                        .append(" = (").append(SbeNaming.javaTypeName(field.javaType())).append(") ").append(targetName).append("_items;\n");
            }
            return;
        }
        if (field.kind() == SbeFieldKind.REPEATING_SCALAR) {
            if (isPrimitiveScalarArrayField(field)) {
                source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(targetName)
                        .append(" = new ").append(SbeNaming.javaTypeName(field.javaType().getComponentType())).append("[")
                        .append(targetName).append("_group.count()];\n");
                source.append("        int ").append(targetName).append("_index = 0;\n");
                source.append("        while (").append(targetName).append("_group.hasNext()) {\n");
                source.append("            ").append(targetName).append("[").append(targetName).append("_index++] = decodeScalarGroup").append(helper)
                        .append("(").append(targetName).append("_group.next());\n");
                source.append("        }\n");
            } else {
                source.append("        final java.util.Collection<").append(SbeNaming.javaTypeName(field.elementSpec().javaType())).append("> ")
                        .append(targetName).append("_items = createCollection").append(helper).append("(").append(targetName).append("_group.count());\n");
                source.append("        while (").append(targetName).append("_group.hasNext()) {\n");
                source.append("            ").append(targetName).append("_items.add(decodeScalarGroup").append(helper).append("(").append(targetName).append("_group.next()));\n");
                source.append("        }\n");
                if (field.javaType().isArray()) {
                    source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(targetName)
                            .append(" = ").append(targetName).append("_items.toArray(")
                            .append(zeroLengthArrayExpr(field.elementSpec().javaType())).append(");\n");
                } else {
                    source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(targetName)
                            .append(" = (").append(SbeNaming.javaTypeName(field.javaType())).append(") ").append(targetName).append("_items;\n");
                }
            }
            return;
        }
        source.append("        final java.util.Map<").append(SbeNaming.javaTypeName(field.mapKeySpec().javaType())).append(", ")
                .append(SbeNaming.javaTypeName(field.mapValueSpec().javaType())).append("> ").append(targetName)
                .append("_map = createMap").append(helper).append("(").append(targetName).append("_group.count());\n");
        source.append("        while (").append(targetName).append("_group.hasNext()) {\n");
        source.append("            final java.util.Map.Entry<").append(SbeNaming.javaTypeName(field.mapKeySpec().javaType())).append(", ")
                .append(SbeNaming.javaTypeName(field.mapValueSpec().javaType())).append("> entry = decodeMapGroup").append(helper)
                .append("(").append(targetName).append("_group.next());\n");
        source.append("            ").append(targetName).append("_map.put(entry.getKey(), entry.getValue());\n");
        source.append("        }\n");
        source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(targetName)
                .append(" = (").append(SbeNaming.javaTypeName(field.javaType())).append(") ").append(targetName).append("_map;\n");
    }

    private void emitDecodeFixedField(final StringBuilder source, final SbeFieldSpec field, final String decoderRef) {
        final String name = "decoded_" + field.name();
        switch (field.kind()) {
            case PRIMITIVE, BOOLEAN, CHAR -> emitPrimitiveDecode(source, field, decoderRef, name);
            case BOXED_PRIMITIVE ->
                    source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(name)
                            .append(" = ").append(decoderRef).append(".").append(schemaName(field)).append("().present() == 0 ? null : ")
                            .append(decoderRef).append(".").append(schemaName(field)).append("().value();\n");
            case BOXED_BOOLEAN ->
                    source.append("        final Boolean ").append(name).append(" = ").append(decoderRef).append(".")
                            .append(schemaName(field)).append("().present() == 0 ? null : ").append(decoderRef).append(".").append(schemaName(field)).append("().value() != 0;\n");
            case BOXED_CHAR ->
                    source.append("        final Character ").append(name).append(" = ").append(decoderRef).append(".")
                            .append(schemaName(field)).append("().present() == 0 ? null : (char) (").append(decoderRef).append(".").append(schemaName(field)).append("().value() & 0xFF);\n");
            case ENUM -> {
                source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(name).append(";\n");
                source.append("        if (").append(decoderRef).append(".").append(schemaName(field)).append("().present() == 0) {\n");
                source.append("            ").append(name).append(" = null;\n");
                source.append("        } else {\n");
                source.append("            final int ordinal = ").append(decoderRef).append(".").append(schemaName(field)).append("().value();\n");
                source.append("            ").append(name).append(" = decodeEnum(").append(enumValuesField(field.javaType())).append(", ordinal, \"")
                        .append(field.name()).append("\");\n");
                source.append("        }\n");
            }
            case FIXED_STRING ->
                    source.append("        final String ").append(name).append(" = ").append(decoderRef).append(".")
                            .append(schemaName(field)).append("().present() == 0 ? null : ").append(decoderRef).append(".").append(schemaName(field)).append("().value();\n");
            case FIXED_BYTES -> {
                source.append("        final byte[] ").append(name).append(";\n");
                source.append("        if (").append(decoderRef).append(".").append(schemaName(field)).append("().present() == 0) {\n");
                source.append("            ").append(name).append(" = null;\n");
                source.append("        } else {\n");
                source.append("            final int length = ").append(decoderRef).append(".").append(schemaName(field)).append("().length();\n");
                source.append("            final byte[] tmp = new byte[length];\n");
                source.append("            ").append(decoderRef).append(".").append(schemaName(field)).append("().getValue(tmp, 0, length);\n");
                source.append("            ").append(name).append(" = length == tmp.length ? tmp : java.util.Arrays.copyOf(tmp, length);\n");
                source.append("        }\n");
            }
            case NESTED_FIXED -> emitDecodeFlattenedNestedField(source, field, decoderRef, "");
            case UUID, INSTANT, LOCAL_DATE, LOCAL_TIME, LOCAL_DATE_TIME, OFFSET_DATE_TIME, OFFSET_TIME, DURATION,
                 PERIOD, YEAR, YEAR_MONTH, MONTH_DAY, ZONE_OFFSET -> {
                source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(name).append(" = ")
                        .append(decoderRef).append(".").append(schemaName(field)).append("().present() == 0 ? null : decode")
                        .append(logicalHelperSuffix(field.kind())).append("(").append(decoderRef).append(".").append(schemaName(field)).append("().value());\n");
            }
            default -> {
            }
        }
    }

    private void emitPrimitiveDecode(final StringBuilder source, final SbeFieldSpec field, final String decoderRef, final String name) {
        if (field.javaType() == boolean.class) {
            source.append("        final boolean ").append(name).append(" = ").append(decoderRef).append(".").append(schemaName(field)).append("() != 0;\n");
        } else if (field.javaType() == char.class) {
            source.append("        final char ").append(name).append(" = (char) (").append(decoderRef).append(".").append(schemaName(field)).append("() & 0xFF);\n");
        } else {
            source.append("        final ").append(field.javaType().getName()).append(" ").append(name).append(" = ").append(decoderRef).append(".")
                    .append(schemaName(field)).append("();\n");
        }
    }

    private void emitInstantiate(final StringBuilder source, final SbeTypeSpec spec) {
        if (spec.instantiationStyle() == SbeInstantiationStyle.RECORD) {
            source.append("        return new ").append(SbeNaming.javaTypeName(spec.javaType())).append("(");
            for (int index = 0; index < spec.fields().size(); index++) {
                if (index > 0) {
                    source.append(", ");
                }
                final SbeFieldSpec field = spec.fields().get(index);
                if (field.optional()) {
                    source.append("optionalOfNullable(decoded_").append(field.name()).append(")");
                } else {
                    source.append("decoded_").append(field.name());
                }
            }
            source.append(");\n");
            return;
        }
        source.append("        final ").append(SbeNaming.javaTypeName(spec.javaType())).append(" value = ReflectionSupport.instantiate(")
                .append(constructorField(spec.javaType())).append(");\n");
        for (final SbeFieldSpec field : spec.fields()) {
            source.append("        ").append(handle(field)).append(".set(value, ");
            if (field.optional()) {
                source.append("optionalOfNullable(decoded_").append(field.name()).append(")");
            } else {
                source.append("decoded_").append(field.name());
            }
            source.append(");\n");
        }
        source.append("        return value;\n");
    }

    private void emitLogicalHelpers(
            final StringBuilder source,
            final SbeTypeSpec spec,
            final Set<SbeFieldKind> emittedKinds,
            final Set<Class<?>> visitedTypes
    ) {
        if (!visitedTypes.add(spec.javaType())) {
            return;
        }
        for (final SbeFieldSpec field : spec.fields()) {
            if (field.logicalFixed() && emittedKinds.add(field.kind())) {
                emitLogicalHelper(source, field.kind());
            }
            if (field.kind() == SbeFieldKind.NESTED_FIXED || field.kind() == SbeFieldKind.REPEATING_GROUP) {
                emitLogicalHelpers(source, field.nestedType(), emittedKinds, visitedTypes);
            }
            if (field.kind() == SbeFieldKind.REPEATING_SCALAR && field.elementSpec() != null
                && field.elementSpec().logicalFixed() && emittedKinds.add(field.elementSpec().kind())) {
                emitLogicalHelper(source, field.elementSpec().kind());
            }
            if (field.kind() == SbeFieldKind.MAP) {
                emitLogicalHelperForSyntheticField(source, field.mapKeySpec(), emittedKinds, visitedTypes);
                emitLogicalHelperForSyntheticField(source, field.mapValueSpec(), emittedKinds, visitedTypes);
            }
        }
    }

    private void emitLogicalHelperForSyntheticField(
            final StringBuilder source,
            final SbeFieldSpec field,
            final Set<SbeFieldKind> emittedKinds,
            final Set<Class<?>> visitedTypes
    ) {
        if (field == null) {
            return;
        }
        if (field.logicalFixed() && emittedKinds.add(field.kind())) {
            emitLogicalHelper(source, field.kind());
        }
        if (field.kind() == SbeFieldKind.NESTED_FIXED && field.nestedType() != null) {
            emitLogicalHelpers(source, field.nestedType(), emittedKinds, visitedTypes);
        }
    }

    private void emitLogicalHelper(final StringBuilder source, final SbeFieldKind kind) {
        switch (kind) {
            case UUID -> {
                source.append("    private static void encodeUuid(final UuidEncoder encoder, final UUID value) {\n");
                source.append("        encoder.mostSigBits(value.getMostSignificantBits()).leastSigBits(value.getLeastSignificantBits());\n");
                source.append("    }\n\n");
                source.append("    private static UUID decodeUuid(final UuidDecoder decoder) {\n");
                source.append("        return new UUID(decoder.mostSigBits(), decoder.leastSigBits());\n");
                source.append("    }\n\n");
            }
            case INSTANT -> {
                source.append("    private static void encodeInstant(final InstantEncoder encoder, final Instant value) {\n");
                source.append("        encoder.epochSecond(value.getEpochSecond()).nano(value.getNano());\n");
                source.append("    }\n\n");
                source.append("    private static Instant decodeInstant(final InstantDecoder decoder) {\n");
                source.append("        return Instant.ofEpochSecond(decoder.epochSecond(), decoder.nano());\n");
                source.append("    }\n\n");
            }
            case LOCAL_DATE -> {
                source.append("    private static void encodeLocalDate(final LocalDateEncoder encoder, final LocalDate value) {\n");
                source.append("        encoder.epochDay(Math.toIntExact(value.toEpochDay()));\n");
                source.append("    }\n\n");
                source.append("    private static LocalDate decodeLocalDate(final LocalDateDecoder decoder) {\n");
                source.append("        return LocalDate.ofEpochDay(decoder.epochDay());\n");
                source.append("    }\n\n");
            }
            case LOCAL_TIME -> {
                source.append("    private static void encodeLocalTime(final LocalTimeEncoder encoder, final LocalTime value) {\n");
                source.append("        encoder.nanoOfDay(value.toNanoOfDay());\n");
                source.append("    }\n\n");
                source.append("    private static LocalTime decodeLocalTime(final LocalTimeDecoder decoder) {\n");
                source.append("        return LocalTime.ofNanoOfDay(decoder.nanoOfDay());\n");
                source.append("    }\n\n");
            }
            case LOCAL_DATE_TIME -> {
                source.append("    private static void encodeLocalDateTime(final LocalDateTimeEncoder encoder, final LocalDateTime value) {\n");
                source.append("        encoder.epochDay(Math.toIntExact(value.toLocalDate().toEpochDay())).nanoOfDay(value.toLocalTime().toNanoOfDay());\n");
                source.append("    }\n\n");
                source.append("    private static LocalDateTime decodeLocalDateTime(final LocalDateTimeDecoder decoder) {\n");
                source.append("        return LocalDate.ofEpochDay(decoder.epochDay()).atTime(LocalTime.ofNanoOfDay(decoder.nanoOfDay()));\n");
                source.append("    }\n\n");
            }
            case OFFSET_DATE_TIME -> {
                source.append("    private static void encodeOffsetDateTime(final OffsetDateTimeEncoder encoder, final OffsetDateTime value) {\n");
                source.append("        encoder.epochDay(Math.toIntExact(value.toLocalDate().toEpochDay())).nanoOfDay(value.toLocalTime().toNanoOfDay()).offsetSeconds(value.getOffset().getTotalSeconds());\n");
                source.append("    }\n\n");
                source.append("    private static OffsetDateTime decodeOffsetDateTime(final OffsetDateTimeDecoder decoder) {\n");
                source.append("        return OffsetDateTime.of(LocalDate.ofEpochDay(decoder.epochDay()), LocalTime.ofNanoOfDay(decoder.nanoOfDay()), ZoneOffset.ofTotalSeconds(decoder.offsetSeconds()));\n");
                source.append("    }\n\n");
            }
            case OFFSET_TIME -> {
                source.append("    private static void encodeOffsetTime(final OffsetTimeEncoder encoder, final OffsetTime value) {\n");
                source.append("        encoder.nanoOfDay(value.toLocalTime().toNanoOfDay()).offsetSeconds(value.getOffset().getTotalSeconds());\n");
                source.append("    }\n\n");
                source.append("    private static OffsetTime decodeOffsetTime(final OffsetTimeDecoder decoder) {\n");
                source.append("        return OffsetTime.of(LocalTime.ofNanoOfDay(decoder.nanoOfDay()), ZoneOffset.ofTotalSeconds(decoder.offsetSeconds()));\n");
                source.append("    }\n\n");
            }
            case DURATION -> {
                source.append("    private static void encodeDuration(final DurationEncoder encoder, final Duration value) {\n");
                source.append("        encoder.seconds(value.getSeconds()).nanos(value.getNano());\n");
                source.append("    }\n\n");
                source.append("    private static Duration decodeDuration(final DurationDecoder decoder) {\n");
                source.append("        return Duration.ofSeconds(decoder.seconds(), decoder.nanos());\n");
                source.append("    }\n\n");
            }
            case PERIOD -> {
                source.append("    private static void encodePeriod(final PeriodEncoder encoder, final Period value) {\n");
                source.append("        encoder.years(value.getYears()).months(value.getMonths()).days(value.getDays());\n");
                source.append("    }\n\n");
                source.append("    private static Period decodePeriod(final PeriodDecoder decoder) {\n");
                source.append("        return Period.of(decoder.years(), decoder.months(), decoder.days());\n");
                source.append("    }\n\n");
            }
            case YEAR -> {
                source.append("    private static void encodeYear(final YearEncoder encoder, final Year value) {\n");
                source.append("        encoder.value(value.getValue());\n");
                source.append("    }\n\n");
                source.append("    private static Year decodeYear(final YearDecoder decoder) {\n");
                source.append("        return Year.of(decoder.value());\n");
                source.append("    }\n\n");
            }
            case YEAR_MONTH -> {
                source.append("    private static void encodeYearMonth(final YearMonthEncoder encoder, final YearMonth value) {\n");
                source.append("        encoder.year(value.getYear()).month((short) value.getMonthValue());\n");
                source.append("    }\n\n");
                source.append("    private static YearMonth decodeYearMonth(final YearMonthDecoder decoder) {\n");
                source.append("        return YearMonth.of(decoder.year(), decoder.month());\n");
                source.append("    }\n\n");
            }
            case MONTH_DAY -> {
                source.append("    private static void encodeMonthDay(final MonthDayEncoder encoder, final MonthDay value) {\n");
                source.append("        encoder.month((short) value.getMonthValue()).day((short) value.getDayOfMonth());\n");
                source.append("    }\n\n");
                source.append("    private static MonthDay decodeMonthDay(final MonthDayDecoder decoder) {\n");
                source.append("        return MonthDay.of(decoder.month(), decoder.day());\n");
                source.append("    }\n\n");
            }
            case ZONE_OFFSET -> {
                source.append("    private static void encodeZoneOffset(final ZoneOffsetEncoder encoder, final ZoneOffset value) {\n");
                source.append("        encoder.totalSeconds(value.getTotalSeconds());\n");
                source.append("    }\n\n");
                source.append("    private static ZoneOffset decodeZoneOffset(final ZoneOffsetDecoder decoder) {\n");
                source.append("        return ZoneOffset.ofTotalSeconds(decoder.totalSeconds());\n");
                source.append("    }\n\n");
            }
            default -> {
            }
        }
    }

    private void emitUtilityHelpers(final StringBuilder source) {
        source.append("    private static Object optionalOrNull(final Object value) {\n");
        source.append("        return value == null ? null : ((java.util.Optional<?>) value).orElse(null);\n");
        source.append("    }\n\n");
        source.append("    private static java.util.Optional optionalOfNullable(final Object value) {\n");
        source.append("        return value == null ? java.util.Optional.empty() : java.util.Optional.of(value);\n");
        source.append("    }\n\n");
        source.append("    private static String stringify(final Object value) {\n");
        source.append("        return value == null ? null : value.toString();\n");
        source.append("    }\n\n");
        source.append("    private static int measureVariableString(final String value) {\n");
        source.append("        return 4 + (value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length);\n");
        source.append("    }\n\n");
        source.append("    private static int measureVariableBytes(final byte[] value) {\n");
        source.append("        return 4 + (value == null ? 0 : value.length);\n");
        source.append("    }\n\n");
        source.append("    private static void requireLatin1Char(final char value, final String fieldName) {\n");
        source.append("        if (value > 255) throw new IllegalArgumentException(\"char field \" + fieldName + \" must fit in one byte for SBE\");\n");
        source.append("    }\n\n");
        source.append("    private static byte[] encodeFixedLatin1(final String value, final int fixedLength, final String fieldName) {\n");
        source.append("        final byte[] encoded = value.getBytes(StandardCharsets.ISO_8859_1);\n");
        source.append("        if (!value.equals(new String(encoded, StandardCharsets.ISO_8859_1))) {\n");
        source.append("            throw new IllegalArgumentException(\"String field \" + fieldName + \" contains characters not representable in ISO-8859-1\");\n");
        source.append("        }\n");
        source.append("        if (encoded.length > fixedLength) {\n");
        source.append("            throw new IllegalArgumentException(\"String field \" + fieldName + \" exceeds fixed SBE length \" + fixedLength);\n");
        source.append("        }\n");
        source.append("        return encoded;\n");
        source.append("    }\n\n");
        source.append("    private static void requireFixedBytesLength(final byte[] value, final int fixedLength, final String fieldName) {\n");
        source.append("        if (value.length > fixedLength) {\n");
        source.append("            throw new IllegalArgumentException(\"byte[] field \" + fieldName + \" exceeds fixed SBE length \" + fixedLength);\n");
        source.append("        }\n");
        source.append("    }\n\n");
        source.append("    private static <E extends Enum<E>> E decodeEnum(final E[] values, final int ordinal, final String fieldName) {\n");
        source.append("        if (ordinal < 0 || ordinal >= values.length) {\n");
        source.append("            throw new IllegalStateException(\"Enum ordinal out of range for field \" + fieldName + \": \" + ordinal);\n");
        source.append("        }\n");
        source.append("        return values[ordinal];\n");
        source.append("    }\n\n");
    }

    private int rootBlockLength(final SbeTypeSpec spec) {
        return fixedSize(spec);
    }

    private int fixedSize(final SbeTypeSpec spec) {
        int size = 0;
        for (final SbeFieldSpec field : spec.fields()) {
            if (field.variableLength()) {
                size += 1;
                continue;
            }
            if (field.repeatingGroup()) {
                continue;
            }
            size += fixedFieldSize(field);
        }
        return size;
    }

    private int fixedFieldSize(final SbeFieldSpec field) {
        return switch (field.kind()) {
            case PRIMITIVE -> primitiveSize(field.javaType());
            case BOXED_PRIMITIVE -> 1 + primitiveSize(unbox(field.javaType()));
            case BOOLEAN -> 1;
            case BOXED_BOOLEAN -> 2;
            case CHAR -> 1;
            case BOXED_CHAR -> 2;
            case ENUM -> 5;
            case FIXED_STRING -> 3 + field.fixedLength();
            case FIXED_BYTES -> 3 + field.fixedLength();
            case NESTED_FIXED -> 1 + fixedSize(field.nestedType());
            case UUID -> 1 + 16;
            case INSTANT -> 1 + 12;
            case LOCAL_DATE -> 1 + 4;
            case LOCAL_TIME -> 1 + 8;
            case LOCAL_DATE_TIME -> 1 + 12;
            case OFFSET_DATE_TIME -> 1 + 16;
            case OFFSET_TIME -> 1 + 12;
            case DURATION -> 1 + 12;
            case PERIOD -> 1 + 12;
            case YEAR -> 1 + 4;
            case YEAR_MONTH -> 1 + 5;
            case MONTH_DAY -> 1 + 2;
            case ZONE_OFFSET -> 1 + 4;
            default -> 0;
        };
    }

    private Class<?> unbox(final Class<?> boxed) {
        if (boxed == Byte.class) return byte.class;
        if (boxed == Short.class) return short.class;
        if (boxed == Integer.class) return int.class;
        if (boxed == Long.class) return long.class;
        if (boxed == Float.class) return float.class;
        if (boxed == Double.class) return double.class;
        throw new IllegalArgumentException("Unsupported boxed type " + boxed.getName());
    }

    private int primitiveSize(final Class<?> primitive) {
        if (primitive == byte.class) return 1;
        if (primitive == short.class) return 2;
        if (primitive == int.class || primitive == float.class) return 4;
        if (primitive == long.class || primitive == double.class) return 8;
        throw new IllegalArgumentException("Unsupported primitive type " + primitive.getName());
    }

    private String logicalHelperSuffix(final SbeFieldKind kind) {
        return switch (kind) {
            case UUID -> "Uuid";
            case INSTANT -> "Instant";
            case LOCAL_DATE -> "LocalDate";
            case LOCAL_TIME -> "LocalTime";
            case LOCAL_DATE_TIME -> "LocalDateTime";
            case OFFSET_DATE_TIME -> "OffsetDateTime";
            case OFFSET_TIME -> "OffsetTime";
            case DURATION -> "Duration";
            case PERIOD -> "Period";
            case YEAR -> "Year";
            case YEAR_MONTH -> "YearMonth";
            case MONTH_DAY -> "MonthDay";
            case ZONE_OFFSET -> "ZoneOffset";
            default -> throw new IllegalArgumentException("Unsupported logical kind " + kind);
        };
    }

    private String handle(final SbeFieldSpec field) {
        return "VH_" + SbeNaming.sanitize(field.ownerTypeName()) + "_" + field.name();
    }

    private String constructorField(final Class<?> type) {
        return "CTOR_" + SbeNaming.sanitize(type.getName());
    }

    private String collectionConstructorField(final SbeFieldSpec field) {
        return "CTOR_COLLECTION_" + SbeNaming.sanitize(field.ownerTypeName()) + "_" + field.name();
    }

    private String enumValuesField(final Class<?> type) {
        return "ENUM_VALUES_" + SbeNaming.sanitize(type.getName());
    }

    private String local(final SbeFieldSpec field) {
        return "value_" + field.name();
    }

    private String referenceValueExpr(final SbeFieldSpec field, final String objectRef) {
        final String base = handle(field) + ".get(" + objectRef + ")";
        return field.optional() ? "optionalOrNull(" + base + ")" : base;
    }

    private String variableStringValueExpr(final SbeFieldSpec field, final String objectRef) {
        final String base = referenceValueExpr(field, objectRef);
        return switch (field.kind()) {
            case STRING -> "(String) (" + base + ")";
            case BIG_INTEGER, BIG_DECIMAL -> "stringify(" + base + ")";
            default -> "null";
        };
    }

    private String variableBytesValueExpr(final SbeFieldSpec field, final String objectRef) {
        return "(byte[]) (" + referenceValueExpr(field, objectRef) + ")";
    }

    private String variablePresenceExpr(final SbeFieldSpec field, final String objectRef) {
        return referenceValueExpr(field, objectRef) + " != null";
    }

    private void emitDecodedVariableAssignment(
            final StringBuilder source,
            final SbeFieldSpec field,
            final String rawName,
            final String targetName
    ) {
        final String converted = switch (field.kind()) {
            case STRING -> rawName;
            case BYTES -> rawName;
            case BIG_INTEGER -> rawName + " == null ? null : new java.math.BigInteger(" + rawName + ")";
            case BIG_DECIMAL -> rawName + " == null ? null : new java.math.BigDecimal(" + rawName + ")";
            default -> rawName;
        };
        source.append("        final ").append(SbeNaming.javaTypeName(field.javaType())).append(" ").append(targetName).append(" = ").append(converted).append(";\n");
    }

    private Class<?> declaredJavaType(final SbeFieldSpec field) {
        return field.synthetic() ? field.javaType() : field.field().getType();
    }

    private String schemaName(final SbeFieldSpec field) {
        return SbeNaming.schemaFieldName(field);
    }

    private String schemaName(final String rawFieldName) {
        return SbeNaming.schemaFieldName(rawFieldName);
    }

    private String schemaCapitalized(final SbeFieldSpec field) {
        return capitalize(schemaName(field));
    }

    private String helperSuffix(final SbeFieldSpec field) {
        return SbeNaming.sanitize(field.ownerTypeName()) + "_" + field.name();
    }

    private SbeFieldSpec renamedField(final SbeFieldSpec field, final String newName) {
        final SbeFieldSpec renamed = SbeFieldSpec.synthetic(field.ownerTypeName(), newName, field.javaType(), field.kind(), field.fixedLength(), field.nestedType())
                .withOptional(field.optional())
                .withElementSpec(field.elementSpec());
        if (field.mapKeySpec() != null || field.mapValueSpec() != null) {
            return renamed.withMapSpecs(field.mapKeySpec(), field.mapValueSpec());
        }
        return renamed;
    }

    private String flattenedName(final String prefix, final SbeFieldSpec field) {
        if (prefix == null || prefix.isEmpty()) {
            return field.name();
        }
        return prefix + Character.toUpperCase(field.name().charAt(0)) + field.name().substring(1);
    }

    private String decodedVar(final String rawName) {
        return "decoded_" + rawName;
    }

    private String nestedObjectValueExpr(final SbeFieldSpec field, final String objectRef, final boolean objectNullable) {
        final String base = referenceValueExpr(field, objectRef);
        if (!objectNullable) {
            return base;
        }
        return "(" + objectRef + " == null ? null : " + base + ")";
    }

    private String guardedReferenceValueExpr(final SbeFieldSpec field, final String objectRef, final boolean objectNullable) {
        final String base = referenceValueExpr(field, objectRef);
        if (!objectNullable) {
            return base;
        }
        return "(" + objectRef + " == null ? null : " + base + ")";
    }

    private String guardedFieldValueExpr(final SbeFieldSpec field, final String objectRef, final boolean objectNullable) {
        final String base = referenceValueExpr(field, objectRef);
        if (!objectNullable) {
            return switch (field.kind()) {
                case PRIMITIVE ->
                        "(" + SbeNaming.javaTypeName(field.javaType()) + ") " + handle(field) + ".get(" + objectRef + ")";
                case BOOLEAN -> "(boolean) " + handle(field) + ".get(" + objectRef + ")";
                case CHAR -> "(char) " + handle(field) + ".get(" + objectRef + ")";
                default -> base;
            };
        }
        return switch (field.kind()) {
            case PRIMITIVE ->
                    objectRef + " == null ? " + defaultValueExpr(field) + " : (" + SbeNaming.javaTypeName(field.javaType()) + ") " + handle(field) + ".get(" + objectRef + ")";
            case BOOLEAN -> objectRef + " == null ? false : (boolean) " + handle(field) + ".get(" + objectRef + ")";
            case CHAR -> objectRef + " == null ? '\\0' : (char) " + handle(field) + ".get(" + objectRef + ")";
            default -> "(" + objectRef + " == null ? null : " + base + ")";
        };
    }

    private String variableStringGuardedValueExpr(final SbeFieldSpec field, final String objectRef) {
        final String valueExpr = guardedFieldValueExpr(field, objectRef, true);
        return switch (field.kind()) {
            case STRING -> "(String) (" + valueExpr + ")";
            case BIG_INTEGER, BIG_DECIMAL -> "stringify(" + valueExpr + ")";
            default -> "null";
        };
    }

    private String variableBytesGuardedValueExpr(final SbeFieldSpec field, final String objectRef) {
        return "(byte[]) (" + guardedFieldValueExpr(field, objectRef, true) + ")";
    }

    private String defaultValueExpr(final SbeFieldSpec field) {
        return switch (field.kind()) {
            case PRIMITIVE -> {
                if (field.javaType() == long.class) yield "0L";
                if (field.javaType() == float.class) yield "0.0f";
                if (field.javaType() == double.class) yield "0.0d";
                yield "0";
            }
            case BOOLEAN -> "false";
            case CHAR -> "'\\0'";
            default -> "null";
        };
    }

    private String zeroLengthArrayExpr(final Class<?> componentType) {
        return "(" + SbeNaming.javaTypeName(componentType) + "[]) java.lang.reflect.Array.newInstance("
               + SbeNaming.javaTypeName(componentType) + ".class, 0)";
    }

    private boolean isPrimitiveScalarArrayField(final SbeFieldSpec field) {
        return field.kind() == SbeFieldKind.REPEATING_SCALAR
               && field.javaType().isArray()
               && field.javaType() != byte[].class
               && field.javaType().getComponentType().isPrimitive();
    }

    private String capitalize(final String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}