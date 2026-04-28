package ru.pathcreator.pyc.rpc.serialization.debug.dto;

import ru.pathcreator.pyc.rpc.serialization.annotation.RpcFixedLength;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.annotation.SbeSerializable;

@SbeSerializable
public record SupportedFixedLengthMessage(
        long id,
        @RpcFixedLength(16) String symbol,
        @RpcFixedLength(32) byte[] payload
) {
}