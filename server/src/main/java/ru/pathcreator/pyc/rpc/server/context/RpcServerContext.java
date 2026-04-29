package ru.pathcreator.pyc.rpc.server.context;

import ru.pathcreator.pyc.rpc.contract.RpcMethodContract;

public record RpcServerContext(
        RpcMethodContract<?, ?> method,
        long correlationId,
        int requestMessageTypeId,
        int responseMessageTypeId,
        int requestPayloadLength
) {
}