package ru.pathcreator.pyc.rpc.server.error;

import ru.pathcreator.pyc.rpc.core.codex.RpcStatusCodes;
import ru.pathcreator.pyc.rpc.server.handler.RpcServerMethod;

@FunctionalInterface
public interface RpcServerExceptionMapper {

    RpcServerExceptionMapper DEFAULT = (method, request, error) -> {
        if (error instanceof RpcStatusException statusException) {
            return RpcServerErrorResponse.of(
                    statusException.statusCode(),
                    statusException.getMessage() == null ? "" : statusException.getMessage()
            );
        }
        final String base = "RPC method '" + method.name() + "' failed";
        final String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return RpcServerErrorResponse.of(
                    RpcStatusCodes.INTERNAL_SERVER_ERROR,
                    base + " (" + error.getClass().getSimpleName() + ")"
            );
        }
        return RpcServerErrorResponse.of(
                RpcStatusCodes.INTERNAL_SERVER_ERROR,
                base + ": " + message
        );
    };

    RpcServerErrorResponse toErrorResponse(
            RpcServerMethod<?, ?> method,
            Object request,
            Throwable error
    );
}
