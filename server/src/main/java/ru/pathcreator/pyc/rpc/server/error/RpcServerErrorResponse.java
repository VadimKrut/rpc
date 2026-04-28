package ru.pathcreator.pyc.rpc.server.error;

import ru.pathcreator.pyc.rpc.core.codex.RpcStatusCodes;

public record RpcServerErrorResponse(
        int statusCode,
        String message
) {

    public RpcServerErrorResponse {
        if (RpcStatusCodes.isSuccess(statusCode)) {
            throw new IllegalArgumentException("statusCode must be non-success");
        }
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
    }

    public static RpcServerErrorResponse of(
            final int statusCode,
            final String message
    ) {
        return new RpcServerErrorResponse(statusCode, message);
    }
}
