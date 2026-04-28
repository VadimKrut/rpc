package ru.pathcreator.pyc.rpc.server.context;

import ru.pathcreator.pyc.rpc.server.handler.RpcServerMethod;

public record RpcServerContext(
        RpcServerMethod<?, ?> method,
        long correlationId,
        int requestMessageTypeId,
        int responseMessageTypeId,
        int requestPayloadLength
) {
}
