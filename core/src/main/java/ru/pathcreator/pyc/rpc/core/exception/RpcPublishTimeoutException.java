package ru.pathcreator.pyc.rpc.core.exception;

public final class RpcPublishTimeoutException extends RuntimeException {

    public RpcPublishTimeoutException(
            final long correlationId
    ) {
        super("RPC publish timeout, correlationId=" + correlationId);
    }
}
