package ru.pathcreator.pyc.rpc.core.codex;

import org.agrona.concurrent.UnsafeBuffer;

public record RpcResponseFrame(
        int messageTypeId,
        long correlationId,
        int statusCode,
        int payloadLength,
        UnsafeBuffer buffer
) {

    public int payloadOffset() {
        return RpcEnvelope.HEADER_LENGTH;
    }

    public boolean isSuccess() {
        return RpcStatusCodes.isSuccess(this.statusCode);
    }
}