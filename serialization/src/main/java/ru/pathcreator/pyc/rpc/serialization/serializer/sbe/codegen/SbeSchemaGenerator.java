package ru.pathcreator.pyc.rpc.serialization.serializer.sbe.codegen;

import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model.SbeFieldKind;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model.SbeFieldSpec;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model.SbeTypeSpec;

import java.util.HashSet;
import java.util.Set;

public final class SbeSchemaGenerator {

    public String generate(final SbeTypeSpec spec) {
        final StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<sbe:messageSchema xmlns:sbe=\"http://fixprotocol.io/2016/sbe\" package=\"")
                .append(spec.javaType().getPackageName())
                .append("\" id=\"1\" version=\"0\" semanticVersion=\"0.0.1\" byteOrder=\"littleEndian\">\n");
        builder.append("    <types>\n");
        emitInfrastructureTypes(builder);
        emitLogicalComposites(builder, spec, new HashSet<>(), new HashSet<>());
        emitNullableWrappers(builder, spec, new HashSet<>(), new HashSet<>());
        emitNestedComposites(builder, spec, new HashSet<>());
        builder.append("    </types>\n");
        builder.append("    <message name=\"").append(spec.schemaName()).append("\" id=\"1\">\n");

        final IdSequence ids = new IdSequence();
        for (final SbeFieldSpec field : spec.fields()) {
            if (field.kind() == SbeFieldKind.NESTED_FIXED && field.nestedType() != null) {
                emitNestedStructuredPresence(builder, field, "", ids);
                emitNestedStructuredFixed(builder, field, "", ids);
                continue;
            }
            if (field.variableLength()) {
                builder.append("        <field name=\"")
                        .append(variablePresenceFieldName(field))
                        .append("\" id=\"")
                        .append(ids.next())
                        .append("\" type=\"uint8\"/>\n");
                continue;
            }
            if (field.repeatingGroup()) {
                continue;
            }
            builder.append("        <field name=\"")
                    .append(SbeNaming.schemaFieldName(field))
                    .append("\" id=\"")
                    .append(ids.next())
                    .append("\" type=\"")
                    .append(xmlType(field))
                    .append("\"/>\n");
        }

        for (final SbeFieldSpec field : spec.fields()) {
            if (field.kind() == SbeFieldKind.NESTED_FIXED && field.nestedType() != null) {
                emitNestedStructuredGroups(builder, field, "", ids);
                continue;
            }
            if (field.repeatingGroup()) {
                emitRepeatingGroup(builder, field, ids.next());
            }
        }

        for (final SbeFieldSpec field : spec.fields()) {
            if (field.kind() == SbeFieldKind.NESTED_FIXED && field.nestedType() != null) {
                emitNestedStructuredData(builder, field, "", ids);
                continue;
            }
            if (field.variableLength()) {
                builder.append("        <data name=\"")
                        .append(SbeNaming.schemaFieldName(field))
                        .append("\" id=\"")
                        .append(ids.next())
                        .append("\" type=\"")
                        .append(field.kind() == SbeFieldKind.BYTES ? "varDataEncoding" : "varStringEncoding")
                        .append("\"/>\n");
            }
        }

        builder.append("    </message>\n");
        builder.append("</sbe:messageSchema>\n");
        return builder.toString();
    }

    private void emitInfrastructureTypes(final StringBuilder builder) {
        builder.append("        <composite name=\"messageHeader\">\n");
        builder.append("            <type name=\"blockLength\" primitiveType=\"uint16\"/>\n");
        builder.append("            <type name=\"templateId\" primitiveType=\"uint16\"/>\n");
        builder.append("            <type name=\"schemaId\" primitiveType=\"uint16\"/>\n");
        builder.append("            <type name=\"version\" primitiveType=\"uint16\"/>\n");
        builder.append("        </composite>\n");
        builder.append("        <composite name=\"varStringEncoding\">\n");
        builder.append("            <type name=\"length\" primitiveType=\"uint32\" maxValue=\"2147483647\"/>\n");
        builder.append("            <type name=\"varData\" primitiveType=\"uint8\" length=\"0\" characterEncoding=\"UTF-8\"/>\n");
        builder.append("        </composite>\n");
        builder.append("        <composite name=\"varDataEncoding\">\n");
        builder.append("            <type name=\"length\" primitiveType=\"uint32\" maxValue=\"2147483647\"/>\n");
        builder.append("            <type name=\"varData\" primitiveType=\"uint8\" length=\"0\"/>\n");
        builder.append("        </composite>\n");
        builder.append("        <composite name=\"groupSizeEncoding\">\n");
        builder.append("            <type name=\"blockLength\" primitiveType=\"uint16\"/>\n");
        builder.append("            <type name=\"numInGroup\" primitiveType=\"uint16\"/>\n");
        builder.append("        </composite>\n");
    }

    private void emitLogicalComposites(
            final StringBuilder builder,
            final SbeTypeSpec spec,
            final Set<SbeFieldKind> emittedKinds,
            final Set<Class<?>> visitedTypes
    ) {
        if (!visitedTypes.add(spec.javaType())) {
            return;
        }
        for (final SbeFieldSpec field : spec.fields()) {
            if (field.logicalFixed() && emittedKinds.add(field.kind())) {
                emitLogicalComposite(builder, field.kind());
            }
            if ((field.kind() == SbeFieldKind.NESTED_FIXED || field.kind() == SbeFieldKind.REPEATING_GROUP) && field.nestedType() != null) {
                emitLogicalComposites(builder, field.nestedType(), emittedKinds, visitedTypes);
            }
            if (field.kind() == SbeFieldKind.REPEATING_SCALAR && field.elementSpec() != null && field.elementSpec().logicalFixed()
                && emittedKinds.add(field.elementSpec().kind())) {
                emitLogicalComposite(builder, field.elementSpec().kind());
            }
            if (field.kind() == SbeFieldKind.MAP) {
                emitLogicalCompositeIfNeeded(builder, field.mapKeySpec(), emittedKinds);
                emitLogicalCompositeIfNeeded(builder, field.mapValueSpec(), emittedKinds);
                emitNestedLogicalComposites(builder, field.mapKeySpec(), emittedKinds, visitedTypes);
                emitNestedLogicalComposites(builder, field.mapValueSpec(), emittedKinds, visitedTypes);
            }
        }
    }

    private void emitLogicalCompositeIfNeeded(
            final StringBuilder builder,
            final SbeFieldSpec field,
            final Set<SbeFieldKind> emittedKinds
    ) {
        if (field != null && field.logicalFixed() && emittedKinds.add(field.kind())) {
            emitLogicalComposite(builder, field.kind());
        }
    }

    private void emitNestedLogicalComposites(
            final StringBuilder builder,
            final SbeFieldSpec field,
            final Set<SbeFieldKind> emittedKinds,
            final Set<Class<?>> visitedTypes
    ) {
        if (field != null && field.kind() == SbeFieldKind.NESTED_FIXED && field.nestedType() != null) {
            emitLogicalComposites(builder, field.nestedType(), emittedKinds, visitedTypes);
        }
    }

    private void emitLogicalComposite(final StringBuilder builder, final SbeFieldKind kind) {
        switch (kind) {
            case UUID -> {
                builder.append("        <composite name=\"Uuid\">\n");
                builder.append("            <type name=\"mostSigBits\" primitiveType=\"int64\"/>\n");
                builder.append("            <type name=\"leastSigBits\" primitiveType=\"int64\"/>\n");
                builder.append("        </composite>\n");
            }
            case INSTANT -> {
                builder.append("        <composite name=\"Instant\">\n");
                builder.append("            <type name=\"epochSecond\" primitiveType=\"int64\"/>\n");
                builder.append("            <type name=\"nano\" primitiveType=\"int32\"/>\n");
                builder.append("        </composite>\n");
            }
            case LOCAL_DATE -> {
                builder.append("        <composite name=\"LocalDate\">\n");
                builder.append("            <type name=\"epochDay\" primitiveType=\"int32\"/>\n");
                builder.append("        </composite>\n");
            }
            case LOCAL_TIME -> {
                builder.append("        <composite name=\"LocalTime\">\n");
                builder.append("            <type name=\"nanoOfDay\" primitiveType=\"int64\"/>\n");
                builder.append("        </composite>\n");
            }
            case LOCAL_DATE_TIME -> {
                builder.append("        <composite name=\"LocalDateTime\">\n");
                builder.append("            <type name=\"epochDay\" primitiveType=\"int32\"/>\n");
                builder.append("            <type name=\"nanoOfDay\" primitiveType=\"int64\"/>\n");
                builder.append("        </composite>\n");
            }
            case OFFSET_DATE_TIME -> {
                builder.append("        <composite name=\"OffsetDateTime\">\n");
                builder.append("            <type name=\"epochDay\" primitiveType=\"int32\"/>\n");
                builder.append("            <type name=\"nanoOfDay\" primitiveType=\"int64\"/>\n");
                builder.append("            <type name=\"offsetSeconds\" primitiveType=\"int32\"/>\n");
                builder.append("        </composite>\n");
            }
            case OFFSET_TIME -> {
                builder.append("        <composite name=\"OffsetTime\">\n");
                builder.append("            <type name=\"nanoOfDay\" primitiveType=\"int64\"/>\n");
                builder.append("            <type name=\"offsetSeconds\" primitiveType=\"int32\"/>\n");
                builder.append("        </composite>\n");
            }
            case DURATION -> {
                builder.append("        <composite name=\"Duration\">\n");
                builder.append("            <type name=\"seconds\" primitiveType=\"int64\"/>\n");
                builder.append("            <type name=\"nanos\" primitiveType=\"int32\"/>\n");
                builder.append("        </composite>\n");
            }
            case PERIOD -> {
                builder.append("        <composite name=\"Period\">\n");
                builder.append("            <type name=\"years\" primitiveType=\"int32\"/>\n");
                builder.append("            <type name=\"months\" primitiveType=\"int32\"/>\n");
                builder.append("            <type name=\"days\" primitiveType=\"int32\"/>\n");
                builder.append("        </composite>\n");
            }
            case YEAR -> {
                builder.append("        <composite name=\"Year\">\n");
                builder.append("            <type name=\"value\" primitiveType=\"int32\"/>\n");
                builder.append("        </composite>\n");
            }
            case YEAR_MONTH -> {
                builder.append("        <composite name=\"YearMonth\">\n");
                builder.append("            <type name=\"year\" primitiveType=\"int32\"/>\n");
                builder.append("            <type name=\"month\" primitiveType=\"uint8\"/>\n");
                builder.append("        </composite>\n");
            }
            case MONTH_DAY -> {
                builder.append("        <composite name=\"MonthDay\">\n");
                builder.append("            <type name=\"month\" primitiveType=\"uint8\"/>\n");
                builder.append("            <type name=\"day\" primitiveType=\"uint8\"/>\n");
                builder.append("        </composite>\n");
            }
            case ZONE_OFFSET -> {
                builder.append("        <composite name=\"ZoneOffset\">\n");
                builder.append("            <type name=\"totalSeconds\" primitiveType=\"int32\"/>\n");
                builder.append("        </composite>\n");
            }
            default -> {
            }
        }
    }

    private void emitNullableWrappers(
            final StringBuilder builder,
            final SbeTypeSpec spec,
            final Set<String> emittedWrappers,
            final Set<Class<?>> visitedTypes
    ) {
        if (!visitedTypes.add(spec.javaType())) {
            return;
        }
        for (final SbeFieldSpec field : spec.fields()) {
            final String wrapperName = nullableWrapperName(field);
            if (wrapperName != null && emittedWrappers.add(wrapperName)) {
                builder.append("        <composite name=\"").append(wrapperName).append("\">\n");
                builder.append("            <type name=\"present\" primitiveType=\"uint8\"/>\n");
                if (field.kind() == SbeFieldKind.NESTED_FIXED) {
                    builder.append("            <ref name=\"value\" type=\"").append(field.nestedType().schemaName()).append("\"/>\n");
                } else if (field.kind() == SbeFieldKind.FIXED_STRING) {
                    builder.append("            <type name=\"length\" primitiveType=\"uint16\"/>\n");
                    builder.append("            <type name=\"value\" primitiveType=\"char\" length=\"")
                            .append(field.fixedLength())
                            .append("\" characterEncoding=\"ISO-8859-1\"/>\n");
                } else if (field.kind() == SbeFieldKind.FIXED_BYTES) {
                    builder.append("            <type name=\"length\" primitiveType=\"uint16\"/>\n");
                    builder.append("            <type name=\"value\" primitiveType=\"uint8\" length=\"")
                            .append(field.fixedLength())
                            .append("\"/>\n");
                } else if (field.logicalFixed()) {
                    builder.append("            <ref name=\"value\" type=\"").append(logicalCompositeName(field.kind())).append("\"/>\n");
                } else {
                    builder.append("            <type name=\"value\" primitiveType=\"").append(nullablePrimitiveType(field)).append("\"/>\n");
                }
                builder.append("        </composite>\n");
            }
            if ((field.kind() == SbeFieldKind.NESTED_FIXED || field.kind() == SbeFieldKind.REPEATING_GROUP) && field.nestedType() != null) {
                emitNullableWrappers(builder, field.nestedType(), emittedWrappers, visitedTypes);
            }
            if (field.kind() == SbeFieldKind.REPEATING_SCALAR) {
                emitNullableWrapperIfNeeded(builder, field.elementSpec(), emittedWrappers);
                emitNestedNullableWrappers(builder, field.elementSpec(), emittedWrappers, visitedTypes);
            }
            if (field.kind() == SbeFieldKind.MAP) {
                emitNullableWrapperIfNeeded(builder, field.mapKeySpec(), emittedWrappers);
                emitNullableWrapperIfNeeded(builder, field.mapValueSpec(), emittedWrappers);
                emitNestedNullableWrappers(builder, field.mapKeySpec(), emittedWrappers, visitedTypes);
                emitNestedNullableWrappers(builder, field.mapValueSpec(), emittedWrappers, visitedTypes);
            }
        }
    }

    private void emitNullableWrapperIfNeeded(
            final StringBuilder builder,
            final SbeFieldSpec field,
            final Set<String> emittedWrappers
    ) {
        if (field == null) {
            return;
        }
        final String wrapperName = nullableWrapperName(field);
        if (wrapperName == null || !emittedWrappers.add(wrapperName)) {
            return;
        }
        builder.append("        <composite name=\"").append(wrapperName).append("\">\n");
        builder.append("            <type name=\"present\" primitiveType=\"uint8\"/>\n");
        if (field.kind() == SbeFieldKind.NESTED_FIXED) {
            builder.append("            <ref name=\"value\" type=\"").append(field.nestedType().schemaName()).append("\"/>\n");
        } else if (field.kind() == SbeFieldKind.FIXED_STRING) {
            builder.append("            <type name=\"length\" primitiveType=\"uint16\"/>\n");
            builder.append("            <type name=\"value\" primitiveType=\"char\" length=\"")
                    .append(field.fixedLength())
                    .append("\" characterEncoding=\"ISO-8859-1\"/>\n");
        } else if (field.kind() == SbeFieldKind.FIXED_BYTES) {
            builder.append("            <type name=\"length\" primitiveType=\"uint16\"/>\n");
            builder.append("            <type name=\"value\" primitiveType=\"uint8\" length=\"")
                    .append(field.fixedLength())
                    .append("\"/>\n");
        } else if (field.logicalFixed()) {
            builder.append("            <ref name=\"value\" type=\"").append(logicalCompositeName(field.kind())).append("\"/>\n");
        } else {
            builder.append("            <type name=\"value\" primitiveType=\"").append(nullablePrimitiveType(field)).append("\"/>\n");
        }
        builder.append("        </composite>\n");
    }

    private void emitNestedNullableWrappers(
            final StringBuilder builder,
            final SbeFieldSpec field,
            final Set<String> emittedWrappers,
            final Set<Class<?>> visitedTypes
    ) {
        if (field != null && field.kind() == SbeFieldKind.NESTED_FIXED && field.nestedType() != null) {
            emitNullableWrappers(builder, field.nestedType(), emittedWrappers, visitedTypes);
        }
    }

    private void emitNestedComposites(final StringBuilder builder, final SbeTypeSpec spec, final Set<Class<?>> visited) {
        for (final SbeFieldSpec field : spec.fields()) {
            if ((field.kind() != SbeFieldKind.NESTED_FIXED && field.kind() != SbeFieldKind.REPEATING_GROUP) || field.nestedType() == null) {
                if (field.kind() == SbeFieldKind.MAP) {
                    emitNestedCompositeIfNeeded(builder, field.mapKeySpec(), visited);
                    emitNestedCompositeIfNeeded(builder, field.mapValueSpec(), visited);
                }
                continue;
            }
            final SbeTypeSpec nested = field.nestedType();
            if (!visited.add(nested.javaType())) {
                continue;
            }
            builder.append("        <composite name=\"").append(nested.schemaName()).append("\">\n");
            for (final SbeFieldSpec nestedField : nested.fields()) {
                if (nestedField.variableLength() || nestedField.repeatingGroup()) {
                    continue;
                }
                final String primitiveType = primitiveXmlType(nestedField);
                if (primitiveType != null) {
                    builder.append("            <type name=\"")
                            .append(SbeNaming.schemaFieldName(nestedField))
                            .append("\" primitiveType=\"")
                            .append(primitiveType)
                            .append("\"");
                    if (nestedField.kind() == SbeFieldKind.FIXED_STRING || nestedField.kind() == SbeFieldKind.FIXED_BYTES) {
                        builder.append(" length=\"").append(nestedField.fixedLength()).append("\"");
                        if (nestedField.kind() == SbeFieldKind.FIXED_STRING) {
                            builder.append(" characterEncoding=\"ISO-8859-1\"");
                        }
                    }
                    builder.append("/>\n");
                } else {
                    builder.append("            <ref name=\"")
                            .append(SbeNaming.schemaFieldName(nestedField))
                            .append("\" type=\"")
                            .append(xmlType(nestedField))
                            .append("\"/>\n");
                }
            }
            builder.append("        </composite>\n");
            emitNestedComposites(builder, nested, visited);
        }
    }

    private void emitNestedCompositeIfNeeded(
            final StringBuilder builder,
            final SbeFieldSpec field,
            final Set<Class<?>> visited
    ) {
        if (field == null || field.kind() != SbeFieldKind.NESTED_FIXED || field.nestedType() == null) {
            return;
        }
        final SbeTypeSpec nested = field.nestedType();
        if (!visited.add(nested.javaType())) {
            return;
        }
        builder.append("        <composite name=\"").append(nested.schemaName()).append("\">\n");
        for (final SbeFieldSpec nestedField : nested.fields()) {
            if (nestedField.variableLength() || nestedField.repeatingGroup()) {
                continue;
            }
            final String primitiveType = primitiveXmlType(nestedField);
            if (primitiveType != null) {
                builder.append("            <type name=\"")
                        .append(SbeNaming.schemaFieldName(nestedField))
                        .append("\" primitiveType=\"")
                        .append(primitiveType)
                        .append("\"");
                if (nestedField.kind() == SbeFieldKind.FIXED_STRING || nestedField.kind() == SbeFieldKind.FIXED_BYTES) {
                    builder.append(" length=\"").append(nestedField.fixedLength()).append("\"");
                    if (nestedField.kind() == SbeFieldKind.FIXED_STRING) {
                        builder.append(" characterEncoding=\"ISO-8859-1\"");
                    }
                }
                builder.append("/>\n");
            } else {
                builder.append("            <ref name=\"")
                        .append(SbeNaming.schemaFieldName(nestedField))
                        .append("\" type=\"")
                        .append(xmlType(nestedField))
                        .append("\"/>\n");
            }
        }
        builder.append("        </composite>\n");
        emitNestedComposites(builder, nested, visited);
    }

    private String xmlType(final SbeFieldSpec field) {
        return switch (field.kind()) {
            case BOOLEAN -> "uint8";
            case CHAR -> "char";
            case BOXED_BOOLEAN, BOXED_CHAR, BOXED_PRIMITIVE, ENUM, FIXED_STRING, FIXED_BYTES, NESTED_FIXED,
                 UUID, INSTANT, LOCAL_DATE, LOCAL_TIME, LOCAL_DATE_TIME, OFFSET_DATE_TIME, OFFSET_TIME,
                 DURATION, PERIOD, YEAR, YEAR_MONTH, MONTH_DAY, ZONE_OFFSET -> nullableWrapperName(field);
            case REPEATING_GROUP ->
                    throw new IllegalArgumentException("Repeating group is emitted separately: " + field.name());
            default -> primitiveType(field.kind(), field.javaType());
        };
    }

    private String primitiveXmlType(final SbeFieldSpec field) {
        return switch (field.kind()) {
            case PRIMITIVE, BOOLEAN, CHAR, FIXED_STRING, FIXED_BYTES -> primitiveType(field.kind(), field.javaType());
            default -> null;
        };
    }

    private String nullableWrapperName(final SbeFieldSpec field) {
        return switch (field.kind()) {
            case BOXED_BOOLEAN -> "NullableBoolean";
            case BOXED_CHAR -> "NullableChar";
            case BOXED_PRIMITIVE ->
                    "Nullable" + capitalize(primitiveType(SbeFieldKind.PRIMITIVE, unbox(field.javaType())));
            case ENUM -> "NullableEnumInt32";
            case FIXED_STRING -> "NullableFixedString" + field.fixedLength();
            case FIXED_BYTES -> "NullableFixedBytes" + field.fixedLength();
            case NESTED_FIXED -> "Nullable" + field.nestedType().schemaName();
            case UUID, INSTANT, LOCAL_DATE, LOCAL_TIME, LOCAL_DATE_TIME, OFFSET_DATE_TIME, OFFSET_TIME, DURATION,
                 PERIOD, YEAR, YEAR_MONTH, MONTH_DAY, ZONE_OFFSET -> "Nullable" + logicalCompositeName(field.kind());
            default -> null;
        };
    }

    private String nullablePrimitiveType(final SbeFieldSpec field) {
        return switch (field.kind()) {
            case BOXED_BOOLEAN -> "uint8";
            case BOXED_CHAR -> "char";
            case BOXED_PRIMITIVE -> primitiveType(SbeFieldKind.PRIMITIVE, unbox(field.javaType()));
            case ENUM -> "int32";
            default -> throw new IllegalArgumentException("No nullable primitive type for " + field.kind());
        };
    }

    private String primitiveType(final SbeFieldKind kind, final Class<?> type) {
        if (kind == SbeFieldKind.FIXED_STRING) {
            return "char";
        }
        if (kind == SbeFieldKind.FIXED_BYTES) {
            return "uint8";
        }
        if (kind == SbeFieldKind.BOXED_PRIMITIVE) {
            if (type == Byte.class) return "int8";
            if (type == Short.class) return "int16";
            if (type == Integer.class) return "int32";
            if (type == Long.class) return "int64";
            if (type == Float.class) return "float";
            if (type == Double.class) return "double";
        }
        if (type == byte.class) return "int8";
        if (type == short.class) return "int16";
        if (type == int.class) return "int32";
        if (type == long.class) return "int64";
        if (type == float.class) return "float";
        if (type == double.class) return "double";
        return "int32";
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

    private String capitalize(final String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String logicalCompositeName(final SbeFieldKind kind) {
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
            default -> throw new IllegalArgumentException("No logical composite name for " + kind);
        };
    }

    private void emitRepeatingGroup(
            final StringBuilder builder,
            final SbeFieldSpec field,
            final int id
    ) {
        builder.append("        <group name=\"")
                .append(SbeNaming.schemaFieldName(field))
                .append("\" id=\"")
                .append(id)
                .append("\" dimensionType=\"groupSizeEncoding\">\n");
        final IdSequence nestedIds = new IdSequence();
        if (field.kind() == SbeFieldKind.REPEATING_SCALAR) {
            emitGroupBody(builder, "", nestedIds, field.elementSpec());
        } else if (field.kind() == SbeFieldKind.MAP) {
            emitGroupBody(builder, "", nestedIds, field.mapKeySpec(), field.mapValueSpec());
        } else {
            emitGroupBody(builder, "", nestedIds, field.nestedType().fields().toArray(SbeFieldSpec[]::new));
        }
        builder.append("        </group>\n");
    }

    private void emitGroupBody(
            final StringBuilder builder,
            final String prefix,
            final IdSequence ids,
            final SbeFieldSpec... fields
    ) {
        for (final SbeFieldSpec groupField : fields) {
            if (groupField.kind() == SbeFieldKind.NESTED_FIXED && groupField.nestedType() != null) {
                emitNestedStructuredPresence(builder, groupField, prefix, ids);
                emitNestedStructuredFixed(builder, groupField, prefix, ids);
                continue;
            }
            if (groupField.variableLength()) {
                builder.append("            <field name=\"")
                        .append(variablePresenceFieldName(flattenedName(prefix, groupField)))
                        .append("\" id=\"")
                        .append(ids.next())
                        .append("\" type=\"uint8\"/>\n");
                continue;
            }
            if (groupField.repeatingGroup()) {
                continue;
            }
            builder.append("            <field name=\"")
                    .append(schemaFieldName(prefix, groupField))
                    .append("\" id=\"")
                    .append(ids.next())
                    .append("\" type=\"")
                    .append(xmlType(groupField))
                    .append("\"/>\n");
        }
        for (final SbeFieldSpec groupField : fields) {
            if (groupField.kind() == SbeFieldKind.NESTED_FIXED && groupField.nestedType() != null) {
                emitNestedStructuredGroups(builder, groupField, prefix, ids);
                continue;
            }
            if (groupField.repeatingGroup()) {
                emitNestedRepeatingGroup(builder, groupField, prefix, ids);
            }
        }
        for (final SbeFieldSpec groupField : fields) {
            if (groupField.kind() == SbeFieldKind.NESTED_FIXED && groupField.nestedType() != null) {
                emitNestedStructuredData(builder, groupField, prefix, ids);
                continue;
            }
            if (groupField.variableLength()) {
                builder.append("            <data name=\"")
                        .append(schemaFieldName(prefix, groupField))
                        .append("\" id=\"")
                        .append(ids.next())
                        .append("\" type=\"")
                        .append(groupField.kind() == SbeFieldKind.BYTES ? "varDataEncoding" : "varStringEncoding")
                        .append("\"/>\n");
            }
        }
    }

    private void emitNestedStructuredPresence(
            final StringBuilder builder,
            final SbeFieldSpec field,
            final String prefix,
            final IdSequence ids
    ) {
        builder.append("            <field name=\"")
                .append(schemaFieldName(prefix, field))
                .append("Present\" id=\"")
                .append(ids.next())
                .append("\" type=\"uint8\"/>\n");
    }

    private void emitNestedStructuredGroups(
            final StringBuilder builder,
            final SbeFieldSpec field,
            final String prefix,
            final IdSequence ids
    ) {
        final String nestedPrefix = nestedPrefix(prefix, field);
        for (final SbeFieldSpec nestedField : field.nestedType().fields()) {
            if (nestedField.kind() == SbeFieldKind.NESTED_FIXED && nestedField.nestedType() != null) {
                emitNestedStructuredGroups(builder, nestedField, nestedPrefix, ids);
                continue;
            }
            if (nestedField.repeatingGroup()) {
                emitNestedRepeatingGroup(builder, nestedField, nestedPrefix, ids);
            }
        }
    }

    private void emitNestedStructuredData(
            final StringBuilder builder,
            final SbeFieldSpec field,
            final String prefix,
            final IdSequence ids
    ) {
        final String nestedPrefix = nestedPrefix(prefix, field);
        for (final SbeFieldSpec nestedField : field.nestedType().fields()) {
            if (nestedField.kind() == SbeFieldKind.NESTED_FIXED && nestedField.nestedType() != null) {
                emitNestedStructuredData(builder, nestedField, nestedPrefix, ids);
                continue;
            }
            if (nestedField.variableLength()) {
                builder.append("            <data name=\"")
                        .append(schemaFieldName(nestedPrefix, nestedField))
                        .append("\" id=\"")
                        .append(ids.next())
                        .append("\" type=\"")
                        .append(nestedField.kind() == SbeFieldKind.BYTES ? "varDataEncoding" : "varStringEncoding")
                        .append("\"/>\n");
            }
        }
    }

    private void emitNestedStructuredFixed(
            final StringBuilder builder,
            final SbeFieldSpec field,
            final String prefix,
            final IdSequence ids
    ) {
        final String nestedPrefix = nestedPrefix(prefix, field);
        for (final SbeFieldSpec nestedField : field.nestedType().fields()) {
            if (nestedField.kind() == SbeFieldKind.NESTED_FIXED && nestedField.nestedType() != null) {
                emitNestedStructuredPresence(builder, nestedField, nestedPrefix, ids);
                emitNestedStructuredFixed(builder, nestedField, nestedPrefix, ids);
                continue;
            }
            if (nestedField.variableLength()) {
                builder.append("            <field name=\"")
                        .append(variablePresenceFieldName(flattenedName(nestedPrefix, nestedField)))
                        .append("\" id=\"")
                        .append(ids.next())
                        .append("\" type=\"uint8\"/>\n");
                continue;
            }
            if (nestedField.repeatingGroup()) {
                continue;
            }
            builder.append("            <field name=\"")
                    .append(schemaFieldName(nestedPrefix, nestedField))
                    .append("\" id=\"")
                    .append(ids.next())
                    .append("\" type=\"")
                    .append(xmlType(nestedField))
                    .append("\"/>\n");
        }
    }

    private void emitNestedRepeatingGroup(
            final StringBuilder builder,
            final SbeFieldSpec field,
            final String prefix,
            final IdSequence ids
    ) {
        builder.append("            <group name=\"")
                .append(schemaFieldName(prefix, field))
                .append("\" id=\"")
                .append(ids.next())
                .append("\" dimensionType=\"groupSizeEncoding\">\n");
        final IdSequence nestedIds = new IdSequence();
        if (field.kind() == SbeFieldKind.REPEATING_SCALAR) {
            emitGroupBody(builder, "", nestedIds, field.elementSpec());
        } else if (field.kind() == SbeFieldKind.MAP) {
            emitGroupBody(builder, "", nestedIds, field.mapKeySpec(), field.mapValueSpec());
        } else {
            emitGroupBody(builder, "", nestedIds, field.nestedType().fields().toArray(SbeFieldSpec[]::new));
        }
        builder.append("            </group>\n");
    }

    private String schemaFieldName(final String prefix, final SbeFieldSpec field) {
        return SbeNaming.schemaFieldName(flattenedName(prefix, field));
    }

    private String flattenedName(final String prefix, final SbeFieldSpec field) {
        if (prefix == null || prefix.isEmpty()) {
            return field.name();
        }
        return prefix + Character.toUpperCase(field.name().charAt(0)) + field.name().substring(1);
    }

    private String nestedPrefix(final String prefix, final SbeFieldSpec field) {
        return flattenedName(prefix, field);
    }

    public static String variablePresenceFieldName(final SbeFieldSpec field) {
        return SbeNaming.schemaFieldName(field) + "Present";
    }

    public static String variablePresenceFieldName(final String rawFieldName) {
        return SbeNaming.schemaFieldName(rawFieldName) + "Present";
    }

    private static final class IdSequence {
        private int value = 1;

        private int next() {
            return this.value++;
        }
    }
}