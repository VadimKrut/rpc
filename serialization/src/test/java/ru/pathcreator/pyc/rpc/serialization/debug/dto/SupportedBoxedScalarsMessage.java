package ru.pathcreator.pyc.rpc.serialization.debug.dto;

import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.annotation.SbeSerializable;

@SbeSerializable
public record SupportedBoxedScalarsMessage(
        Byte tiny,
        Short shortCode,
        Integer intCode,
        Long longCode,
        Float floatPrice,
        Double doublePrice,
        Boolean active,
        Character grade,
        SupportedSide side
) {
}