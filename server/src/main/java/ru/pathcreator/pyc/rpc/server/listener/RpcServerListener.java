package ru.pathcreator.pyc.rpc.server.listener;

import ru.pathcreator.pyc.rpc.contract.RpcMethodContract;

@FunctionalInterface
public interface RpcServerListener {

    RpcServerListener NOOP = new RpcServerListener() {
        @Override
        public void onSuccess(
                final RpcMethodContract<?, ?> method,
                final long correlationId,
                final long latencyNs,
                final int requestPayloadLength,
                final int responsePayloadLength,
                final int statusCode
        ) {
        }

        @Override
        public void onFailure(
                final RpcMethodContract<?, ?> method,
                final long correlationId,
                final long latencyNs,
                final int requestPayloadLength,
                final int responsePayloadLength,
                final int statusCode,
                final Throwable error
        ) {
        }
    };

    void onSuccess(
            RpcMethodContract<?, ?> method,
            long correlationId,
            long latencyNs,
            int requestPayloadLength,
            int responsePayloadLength,
            int statusCode
    );

    default void onStart(
            final RpcMethodContract<?, ?> method,
            final long correlationId,
            final int requestPayloadLength
    ) {
    }

    default void onFailure(
            final RpcMethodContract<?, ?> method,
            final long correlationId,
            final long latencyNs,
            final int requestPayloadLength,
            final int responsePayloadLength,
            final int statusCode,
            final Throwable error
    ) {
    }
}