package ru.pathcreator.pyc.rpc.server.error;

import ru.pathcreator.pyc.rpc.core.codex.RpcStatusCodes;

public final class RpcStatusException extends RuntimeException {

    private final int statusCode;

    public RpcStatusException(
            final int statusCode,
            final String message
    ) {
        super(message);
        if (RpcStatusCodes.isSuccess(statusCode)) {
            throw new IllegalArgumentException("statusCode must be non-success");
        }
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return this.statusCode;
    }
}
