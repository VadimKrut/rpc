package ru.pathcreator.pyc.rpc.core.exception;

public final class RpcCallTimeoutException extends RuntimeException {

    public RpcCallTimeoutException(
            final long correlationId
    ) {
        super("RPC timeout, correlationId=" + correlationId);
    }
}