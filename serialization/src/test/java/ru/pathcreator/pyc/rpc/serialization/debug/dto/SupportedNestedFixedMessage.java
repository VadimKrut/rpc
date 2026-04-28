package ru.pathcreator.pyc.rpc.serialization.debug.dto;

import ru.pathcreator.pyc.rpc.serialization.annotation.RpcFixedLength;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.annotation.SbeSerializable;

@SbeSerializable
public record SupportedNestedFixedMessage(
        long id,
        Header header,
        Body body
) {
    public record Header(
            int type,
            long createdAt,
            SupportedSide side
    ) {
    }

    public record Body(
            @RpcFixedLength(12) String venue,
            @RpcFixedLength(20) byte[] token,
            int quantity
    ) {
    }
}