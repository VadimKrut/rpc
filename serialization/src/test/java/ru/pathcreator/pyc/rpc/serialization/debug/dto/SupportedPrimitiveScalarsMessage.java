package ru.pathcreator.pyc.rpc.serialization.debug.dto;

import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.annotation.SbeSerializable;

@SbeSerializable
public record SupportedPrimitiveScalarsMessage(
        byte tiny,
        short shortCode,
        int intCode,
        long longCode,
        float floatPrice,
        double doublePrice,
        boolean active,
        char grade
) {
}