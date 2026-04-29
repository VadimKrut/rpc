package ru.pathcreator.pyc.rpc.client.listener;

import ru.pathcreator.pyc.rpc.contract.RpcMethodContract;

@FunctionalInterface
public interface RpcClientListener {

    RpcClientListener NOOP = new RpcClientListener() {
        @Override
        public void onSuccess(
                final RpcMethodContract<?, ?> method,
                final long timeoutNs,
                final long latencyNs,
                final int responsePayloadLength,
                final int statusCode
        ) {
        }

        @Override
        public void onRemoteError(
                final RpcMethodContract<?, ?> method,
                final long timeoutNs,
                final long latencyNs,
                final int responsePayloadLength,
                final int statusCode
        ) {
        }

        @Override
        public void onFailure(
                final RpcMethodContract<?, ?> method,
                final long timeoutNs,
                final long latencyNs,
                final Throwable error
        ) {
        }
    };

    void onSuccess(
            RpcMethodContract<?, ?> method,
            long timeoutNs,
            long latencyNs,
            int responsePayloadLength,
            int statusCode
    );

    default void onStart(
            final RpcMethodContract<?, ?> method,
            final long timeoutNs
    ) {
    }

    default void onRemoteError(
            final RpcMethodContract<?, ?> method,
            final long timeoutNs,
            final long latencyNs,
            final int responsePayloadLength,
            final int statusCode
    ) {
    }

    default void onFailure(
            final RpcMethodContract<?, ?> method,
            final long timeoutNs,
            final long latencyNs,
            final Throwable error
    ) {
    }
}