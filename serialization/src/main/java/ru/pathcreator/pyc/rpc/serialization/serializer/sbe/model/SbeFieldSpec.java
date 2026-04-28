package ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model;

import java.lang.reflect.Field;

public record SbeFieldSpec(
        Field field,
        String ownerTypeName,
        String name,
        Class<?> javaType,
        SbeFieldKind kind,
        Integer fixedLength,
        SbeTypeSpec nestedType,
        SbeFieldSpec elementSpec,
        SbeFieldSpec mapKeySpec,
        SbeFieldSpec mapValueSpec,
        boolean optional
) {
    public SbeFieldSpec(
            final Field field,
            final String name,
            final Class<?> javaType,
            final SbeFieldKind kind,
            final Integer fixedLength,
            final SbeTypeSpec nestedType
    ) {
        this(
                field,
                field.getDeclaringClass().getName(),
                name,
                javaType,
                kind,
                fixedLength,
                nestedType,
                null,
                null,
                null,
                false
        );
    }

    public static SbeFieldSpec synthetic(
            final String ownerTypeName,
            final String name,
            final Class<?> javaType,
            final SbeFieldKind kind,
            final Integer fixedLength,
            final SbeTypeSpec nestedType
    ) {
        return new SbeFieldSpec(
                null,
                ownerTypeName,
                name,
                javaType,
                kind,
                fixedLength,
                nestedType,
                null,
                null,
                null,
                false
        );
    }

    public SbeFieldSpec withOptional(final boolean optional) {
        return new SbeFieldSpec(field, ownerTypeName, name, javaType, kind, fixedLength, nestedType, elementSpec, mapKeySpec, mapValueSpec, optional);
    }

    public SbeFieldSpec withElementSpec(final SbeFieldSpec elementSpec) {
        return new SbeFieldSpec(field, ownerTypeName, name, javaType, kind, fixedLength, nestedType, elementSpec, mapKeySpec, mapValueSpec, optional);
    }

    public SbeFieldSpec withMapSpecs(final SbeFieldSpec keySpec, final SbeFieldSpec valueSpec) {
        return new SbeFieldSpec(field, ownerTypeName, name, javaType, kind, fixedLength, nestedType, elementSpec, keySpec, valueSpec, optional);
    }

    public boolean logicalFixed() {
        return switch (kind) {
            case UUID,
                 INSTANT,
                 LOCAL_DATE,
                 LOCAL_TIME,
                 LOCAL_DATE_TIME,
                 OFFSET_DATE_TIME,
                 OFFSET_TIME,
                 DURATION,
                 PERIOD,
                 YEAR,
                 YEAR_MONTH,
                 MONTH_DAY,
                 ZONE_OFFSET -> true;
            default -> false;
        };
    }

    public boolean variableLength() {
        return switch (kind) {
            case STRING, BYTES, BIG_INTEGER, BIG_DECIMAL -> true;
            default -> false;
        };
    }

    public boolean repeatingGroup() {
        return kind == SbeFieldKind.REPEATING_GROUP
               || kind == SbeFieldKind.REPEATING_SCALAR
               || kind == SbeFieldKind.MAP;
    }

    public boolean fixedLengthCompatible() {
        return !variableLength() && !repeatingGroup() && kind != SbeFieldKind.UNSUPPORTED;
    }

    public boolean fixedLengthArray() {
        return kind == SbeFieldKind.FIXED_STRING || kind == SbeFieldKind.FIXED_BYTES;
    }

    public boolean synthetic() {
        return field == null;
    }
}