package ru.pathcreator.pyc.rpc.serialization.serializer.sbe.introspection;

import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.annotation.SbeSerializable;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.codegen.SbeSchemaGenerator;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model.*;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SbeCompatibilityPlanner {

    private final SbeDtoIntrospector introspector = new SbeDtoIntrospector();

    public SbeAnalysisResult plan(final Class<?> rootType) {
        final SbeSerializable annotation = rootType.getAnnotation(SbeSerializable.class);
        if (annotation == null) {
            throw new IllegalArgumentException("Missing @SbeSerializable on " + rootType.getName());
        }
        final List<String> problems = new ArrayList<>();
        SbeTypeSpec rootSpec;
        try {
            rootSpec = introspector.inspectRoot(rootType);
        } catch (final IllegalStateException e) {
            problems.add(e.getMessage());
            return new SbeAnalysisResult(rootType, null, SbeAnalysisStatus.FAIL, List.copyOf(problems));
        }
        if (isSbeCompatible(rootSpec, true, problems)) {
            return new SbeAnalysisResult(rootType, rootSpec, SbeAnalysisStatus.SBE, List.copyOf(problems));
        }
        return new SbeAnalysisResult(rootType, rootSpec, SbeAnalysisStatus.FAIL, List.copyOf(problems));
    }

    private boolean isSbeCompatible(final SbeTypeSpec spec, final boolean root, final List<String> problems) {
        if (spec.instantiationStyle() == SbeInstantiationStyle.UNSUPPORTED_FOR_SBE) {
            problems.add("SBE decode requires a record or a no-args constructor: " + spec.javaType().getName());
            return false;
        }
        boolean compatible = validateSyntheticVariablePresenceNames(spec, problems);
        for (final SbeFieldSpec field : spec.fields()) {
            compatible &= validateFixedLengthUsage(field, problems);
            compatible &= validateRepeatingContainer(field, problems);
            compatible &= validateOptionalUsage(field, problems);
            compatible &= switch (field.kind()) {
                case PRIMITIVE, BOXED_PRIMITIVE, BOOLEAN, BOXED_BOOLEAN, CHAR, BOXED_CHAR, ENUM, FIXED_STRING,
                     FIXED_BYTES, UUID, INSTANT, LOCAL_DATE, LOCAL_TIME, LOCAL_DATE_TIME, OFFSET_DATE_TIME,
                     OFFSET_TIME, DURATION, PERIOD, YEAR, YEAR_MONTH, MONTH_DAY, ZONE_OFFSET,
                     BIG_INTEGER, BIG_DECIMAL -> true;
                case STRING, BYTES -> root;
                case NESTED_FIXED -> field.nestedType() != null
                                     && isNestedStructuredSupported(field.nestedType(), problems, field.ownerTypeName() + "." + field.name());
                case REPEATING_GROUP ->
                        root && field.nestedType() != null && isGroupElementSupported(field.nestedType(), problems, field.ownerTypeName() + "." + field.name());
                case REPEATING_SCALAR ->
                        root && field.elementSpec() != null && isScalarGroupElementSupported(field.elementSpec(), problems, field.ownerTypeName() + "." + field.name());
                case MAP -> root && field.mapKeySpec() != null && field.mapValueSpec() != null
                            && isMapEntrySupported(field.mapKeySpec(), field.mapValueSpec(), problems, field.ownerTypeName() + "." + field.name());
                case UNSUPPORTED -> false;
            };
            if (field.kind() == SbeFieldKind.UNSUPPORTED) {
                problems.add("SBE unsupported field: " + field.ownerTypeName() + "." + field.name() + " : " + field.javaType().getName());
            }
        }
        return compatible;
    }

    private boolean validateSyntheticVariablePresenceNames(final SbeTypeSpec spec, final List<String> problems) {
        boolean compatible = true;
        for (final SbeFieldSpec candidate : spec.fields()) {
            if (!candidate.variableLength()) {
                continue;
            }
            final String syntheticName = SbeSchemaGenerator.variablePresenceFieldName(candidate);
            for (final SbeFieldSpec sibling : spec.fields()) {
                if (sibling != candidate && sibling.name().equals(syntheticName)) {
                    compatible = false;
                    problems.add("SBE variable-length field " + candidate.ownerTypeName() + "." + candidate.name()
                                 + " conflicts with synthetic presence field name " + syntheticName);
                }
            }
        }
        return compatible;
    }

    private boolean validateFixedLengthUsage(final SbeFieldSpec field, final List<String> problems) {
        if (field.fixedLength() == null) {
            return true;
        }
        if (field.fixedLength() <= 0) {
            problems.add("SBE fixed-length hint must be positive: "
                         + field.ownerTypeName() + "." + field.name());
            return false;
        }
        if (!field.fixedLengthArray()) {
            problems.add("SBE fixed-length hint is supported only for String and byte[] fields: "
                         + field.ownerTypeName() + "." + field.name());
            return false;
        }
        if (field.fixedLength() > 65534) {
            problems.add("SBE fixed-length hint is too large for generated schema: "
                         + field.ownerTypeName() + "." + field.name());
            return false;
        }
        return true;
    }

    private boolean validateRepeatingContainer(final SbeFieldSpec field, final List<String> problems) {
        if (field.kind() != SbeFieldKind.REPEATING_GROUP
            && field.kind() != SbeFieldKind.REPEATING_SCALAR
            && field.kind() != SbeFieldKind.MAP) {
            return true;
        }
        final Class<?> rawType = field.javaType();
        if (rawType.isArray()) {
            return true;
        }
        if (rawType.isInterface()) {
            if (rawType == List.class || rawType == java.util.Collection.class || rawType == Set.class
                || rawType == java.util.SortedSet.class || rawType == java.util.NavigableSet.class
                || rawType == java.util.Queue.class || rawType == java.util.Deque.class) {
                return true;
            }
            if (field.kind() == SbeFieldKind.MAP
                && (rawType == java.util.Map.class || rawType == java.util.SortedMap.class || rawType == java.util.NavigableMap.class)) {
                return true;
            }
            problems.add("SBE repeating-group field uses unsupported collection interface: "
                         + field.ownerTypeName() + "." + field.name() + " : " + rawType.getName());
            return false;
        }
        try {
            final Constructor<?> constructor = rawType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return true;
        } catch (final NoSuchMethodException e) {
            problems.add("SBE repeating-group field requires an array, supported collection interface, or no-args collection type: "
                         + field.ownerTypeName() + "." + field.name() + " : " + rawType.getName());
            return false;
        }
    }

    private boolean validateOptionalUsage(final SbeFieldSpec field, final List<String> problems) {
        if (!field.optional()) {
            return true;
        }
        if (field.repeatingGroup()) {
            problems.add("Optional collection/map field is not supported because SBE would not distinguish null from empty: "
                         + field.ownerTypeName() + "." + field.name());
            return false;
        }
        return true;
    }

    private boolean isFixedOnly(final SbeTypeSpec spec, final List<String> problems, final String path) {
        boolean compatible = true;
        for (final SbeFieldSpec field : spec.fields()) {
            if (field.kind() == SbeFieldKind.NESTED_FIXED) {
                compatible &= isFixedOnly(field.nestedType(), problems, path + "." + field.name());
                continue;
            }
            if (!field.fixedLengthCompatible()) {
                compatible = false;
                problems.add("Nested DTO used by SBE must be fixed-only: " + path + "." + field.name());
            }
        }
        return compatible;
    }

    private boolean isGroupElementSupported(final SbeTypeSpec spec, final List<String> problems, final String path) {
        boolean compatible = true;
        for (final SbeFieldSpec field : spec.fields()) {
            if (field.kind() == SbeFieldKind.NESTED_FIXED) {
                compatible &= isNestedStructuredSupported(field.nestedType(), problems, path + "." + field.name());
                continue;
            }
            if (field.kind() == SbeFieldKind.REPEATING_GROUP) {
                compatible &= field.nestedType() != null && isGroupElementSupported(field.nestedType(), problems, path + "." + field.name());
                continue;
            }
            if (field.kind() == SbeFieldKind.REPEATING_SCALAR) {
                compatible &= field.elementSpec() != null && isScalarGroupElementSupported(field.elementSpec(), problems, path + "." + field.name());
                continue;
            }
            if (field.kind() == SbeFieldKind.MAP) {
                compatible &= field.mapKeySpec() != null && field.mapValueSpec() != null
                              && isMapEntrySupported(field.mapKeySpec(), field.mapValueSpec(), problems, path + "." + field.name());
                continue;
            }
            if (field.kind() == SbeFieldKind.UNSUPPORTED) {
                compatible = false;
                problems.add("SBE repeating-group element field is unsupported: " + path + "." + field.name());
            }
        }
        return compatible;
    }

    private boolean isScalarGroupElementSupported(final SbeFieldSpec elementSpec, final List<String> problems, final String path) {
        if (elementSpec.kind() == SbeFieldKind.UNSUPPORTED || elementSpec.repeatingGroup()) {
            problems.add("SBE scalar repeating-group element is unsupported: " + path);
            return false;
        }
        if (elementSpec.kind() == SbeFieldKind.NESTED_FIXED) {
            problems.add("SBE scalar repeating-group element must not be a nested DTO: " + path);
            return false;
        }
        if (elementSpec.optional()) {
            problems.add("Optional values inside repeating-group scalar collections are not supported: " + path);
            return false;
        }
        return true;
    }

    private boolean isMapEntrySupported(
            final SbeFieldSpec keySpec,
            final SbeFieldSpec valueSpec,
            final List<String> problems,
            final String path
    ) {
        boolean compatible = true;
        for (final SbeFieldSpec part : List.of(keySpec, valueSpec)) {
            if (part.kind() == SbeFieldKind.UNSUPPORTED || part.repeatingGroup()) {
                compatible = false;
                problems.add("SBE map entry part is unsupported: " + path + "." + part.name());
                continue;
            }
            if (part.kind() == SbeFieldKind.NESTED_FIXED) {
                compatible &= isNestedStructuredSupported(part.nestedType(), problems, path + "." + part.name());
                continue;
            }
            if (part.optional()) {
                compatible = false;
                problems.add("Optional map key/value is not supported: " + path + "." + part.name());
            }
        }
        return compatible;
    }

    private boolean isNestedStructuredSupported(final SbeTypeSpec spec, final List<String> problems, final String path) {
        boolean compatible = true;
        for (final SbeFieldSpec field : spec.fields()) {
            compatible &= validateFixedLengthUsage(field, problems);
            compatible &= validateRepeatingContainer(field, problems);
            compatible &= validateOptionalUsage(field, problems);
            compatible &= switch (field.kind()) {
                case PRIMITIVE, BOXED_PRIMITIVE, BOOLEAN, BOXED_BOOLEAN, CHAR, BOXED_CHAR, ENUM, STRING, BYTES,
                     FIXED_STRING, FIXED_BYTES, UUID, INSTANT, LOCAL_DATE, LOCAL_TIME, LOCAL_DATE_TIME,
                     OFFSET_DATE_TIME, OFFSET_TIME, DURATION, PERIOD, YEAR, YEAR_MONTH, MONTH_DAY, ZONE_OFFSET,
                     BIG_INTEGER, BIG_DECIMAL -> true;
                case NESTED_FIXED ->
                        field.nestedType() != null && isNestedStructuredSupported(field.nestedType(), problems, path + "." + field.name());
                case REPEATING_GROUP ->
                        field.nestedType() != null && isGroupElementSupported(field.nestedType(), problems, path + "." + field.name());
                case REPEATING_SCALAR ->
                        field.elementSpec() != null && isScalarGroupElementSupported(field.elementSpec(), problems, path + "." + field.name());
                case MAP -> field.mapKeySpec() != null && field.mapValueSpec() != null
                            && isMapEntrySupported(field.mapKeySpec(), field.mapValueSpec(), problems, path + "." + field.name());
                case UNSUPPORTED -> false;
            };
            if (field.kind() == SbeFieldKind.UNSUPPORTED) {
                problems.add("SBE nested DTO field is unsupported: " + path + "." + field.name() + " : " + field.javaType().getName());
            }
        }
        return compatible;
    }
}