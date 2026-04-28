package ru.pathcreator.pyc.rpc.client.context;

import ru.pathcreator.pyc.rpc.client.method.RpcClientMethod;

public record RpcClientContext(
        RpcClientMethod<?, ?> method,
        long timeoutNs
) {
}