package ru.pathcreator.pyc.rpc.core.codex;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import java.nio.ByteOrder;

public final class RpcEnvelope {

    public static final int OFFSET_MESSAGE_TYPE_ID = 0;
    public static final int OFFSET_CORRELATION_ID = OFFSET_MESSAGE_TYPE_ID + Integer.BYTES;
    public static final int OFFSET_FLAGS = OFFSET_CORRELATION_ID + Long.BYTES;
    public static final int OFFSET_PAYLOAD_LENGTH = OFFSET_FLAGS + Integer.BYTES;
    public static final int HEADER_LENGTH = OFFSET_PAYLOAD_LENGTH + Integer.BYTES;

    public static final int FLAG_RESPONSE = 1;
    public static final int FLAG_ERROR = 1 << 1;

    private RpcEnvelope() {
    }

    public static void encode(
            final int flags,
            final int payloadLength,
            final int messageTypeId,
            final long correlationId,
            final MutableDirectBuffer buffer
    ) {
        buffer.putInt(OFFSET_MESSAGE_TYPE_ID, messageTypeId, ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(OFFSET_CORRELATION_ID, correlationId, ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(OFFSET_FLAGS, flags, ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(OFFSET_PAYLOAD_LENGTH, payloadLength, ByteOrder.LITTLE_ENDIAN);
    }

    public static int messageTypeId(
            final int offset,
            final DirectBuffer buffer
    ) {
        return buffer.getInt(offset + OFFSET_MESSAGE_TYPE_ID, ByteOrder.LITTLE_ENDIAN);
    }

    public static long correlationId(
            final int offset,
            final DirectBuffer buffer
    ) {
        return buffer.getLong(offset + OFFSET_CORRELATION_ID, ByteOrder.LITTLE_ENDIAN);
    }

    public static int flags(
            final int offset,
            final DirectBuffer buffer
    ) {
        return buffer.getInt(offset + OFFSET_FLAGS, ByteOrder.LITTLE_ENDIAN);
    }

    public static int payloadLength(
            final int offset,
            final DirectBuffer buffer
    ) {
        return buffer.getInt(offset + OFFSET_PAYLOAD_LENGTH, ByteOrder.LITTLE_ENDIAN);
    }
}