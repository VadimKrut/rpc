package ru.pathcreator.pyc.rpc.server.handler;

public record RpcServerMethod<Q, R>(
        String name,
        Class<Q> requestType,
        Class<R> responseType,
        int requestMessageTypeId,
        int responseMessageTypeId
) {

    public RpcServerMethod {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (requestType == null) {
            throw new IllegalArgumentException("requestType must not be null");
        }
        if (responseType == null) {
            throw new IllegalArgumentException("responseType must not be null");
        }
        if (requestMessageTypeId <= 0) {
            throw new IllegalArgumentException("requestMessageTypeId must be > 0");
        }
        if (responseMessageTypeId <= 0) {
            throw new IllegalArgumentException("responseMessageTypeId must be > 0");
        }
    }

    public static <Q, R> RpcServerMethod<Q, R> of(
            final String name,
            final Class<Q> requestType,
            final Class<R> responseType,
            final int requestMessageTypeId,
            final int responseMessageTypeId
    ) {
        return new RpcServerMethod<>(
                name,
                requestType,
                responseType,
                requestMessageTypeId,
                responseMessageTypeId
        );
    }
}
