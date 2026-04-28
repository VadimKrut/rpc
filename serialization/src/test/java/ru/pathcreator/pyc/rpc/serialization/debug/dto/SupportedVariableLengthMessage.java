package ru.pathcreator.pyc.rpc.serialization.debug.dto;

import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.annotation.SbeSerializable;

@SbeSerializable
public record SupportedVariableLengthMessage(
        long id,
        String name,
        byte[] payload
) {
}