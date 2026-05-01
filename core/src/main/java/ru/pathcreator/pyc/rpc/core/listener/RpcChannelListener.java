package ru.pathcreator.pyc.rpc.core.listener;

public interface RpcChannelListener {

    RpcChannelListener NOOP = new RpcChannelListener() {
    };

    default void onRequestSent(
            final int messageTypeId,
            final int payloadLength,
            final long correlationId
    ) {
    }

    default void onRequestReceived(
            final int messageTypeId,
            final int payloadLength,
            final long correlationId
    ) {
    }

    default void onResponseSent(
            final int messageTypeId,
            final int payloadLength,
            final int statusCode,
            final long correlationId
    ) {
    }

    default void onResponseReceived(
            final int messageTypeId,
            final int payloadLength,
            final int statusCode,
            final long correlationId
    ) {
    }

    default void onPublishTimeout(
            final int messageTypeId,
            final int payloadLength,
            final long correlationId
    ) {
    }

    default void onCallTimeout(
            final long correlationId
    ) {
    }

    default void onPaused(
    ) {
    }

    default void onResumed(
    ) {
    }

    default void onClosed(
    ) {
    }
}