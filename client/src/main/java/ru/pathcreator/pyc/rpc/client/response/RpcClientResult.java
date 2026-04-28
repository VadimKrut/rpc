package ru.pathcreator.pyc.rpc.client.response;

import ru.pathcreator.pyc.rpc.core.codex.RpcStatusCodes;
import ru.pathcreator.pyc.rpc.core.exception.RpcRemoteException;

public record RpcClientResult<R>(
        int statusCode,
        int responseMessageTypeId,
        long correlationId,
        int payloadLength,
        R response,
        String errorMessage
) {

    public boolean isSuccess() {
        return RpcStatusCodes.isSuccess(this.statusCode);
    }

    public boolean isClientError() {
        return this.statusCode >= 400 && this.statusCode < 500;
    }

    public boolean isServerError() {
        return this.statusCode >= 500 && this.statusCode < 600;
    }

    public R requireSuccess() {
        if (!this.isSuccess()) {
            throw new RpcRemoteException(
                    this.statusCode,
                    this.responseMessageTypeId,
                    this.correlationId,
                    this.errorMessage == null ? "" : this.errorMessage
            );
        }
        return this.response;
    }
}