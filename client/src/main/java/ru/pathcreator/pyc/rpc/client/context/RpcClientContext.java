package ru.pathcreator.pyc.rpc.client.context;

import ru.pathcreator.pyc.rpc.contract.RpcMethodContract;

public record RpcClientContext(
        RpcMethodContract<?, ?> method,
        long timeoutNs
) {
}