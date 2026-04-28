package ru.pathcreator.pyc.rpc.core.exception;

public final class RpcRemoteException extends RuntimeException {

    private final int statusCode;
    private final int responseMessageTypeId;
    private final long correlationId;

    public RpcRemoteException(
            final int statusCode,
            final int responseMessageTypeId,
            final long correlationId,
            final String message
    ) {
        super(message);
        this.statusCode = statusCode;
        this.responseMessageTypeId = responseMessageTypeId;
        this.correlationId = correlationId;
    }

    public int statusCode() {
        return this.statusCode;
    }

    public int responseMessageTypeId() {
        return this.responseMessageTypeId;
    }

    public long correlationId() {
        return this.correlationId;
    }
}