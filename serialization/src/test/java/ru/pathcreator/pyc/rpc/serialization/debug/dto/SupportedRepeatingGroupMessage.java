package ru.pathcreator.pyc.rpc.serialization.debug.dto;

import ru.pathcreator.pyc.rpc.serialization.annotation.RpcFixedLength;
import ru.pathcreator.pyc.rpc.serialization.serializer.sbe.annotation.SbeSerializable;

import java.util.List;

@SbeSerializable
public record SupportedRepeatingGroupMessage(
        long id,
        List<Leg> legs
) {
    public record Leg(
            long legId,
            int quantity,
            SupportedSide side,
            @RpcFixedLength(8) String venue
    ) {
    }
}