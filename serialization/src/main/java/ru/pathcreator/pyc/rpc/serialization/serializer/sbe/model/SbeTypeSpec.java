package ru.pathcreator.pyc.rpc.serialization.serializer.sbe.model;

import java.util.List;

public record SbeTypeSpec(
        Class<?> javaType,
        String schemaName,
        SbeInstantiationStyle instantiationStyle,
        List<SbeFieldSpec> fields
) {
}