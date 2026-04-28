package ru.pathcreator.pyc.rpc.core.serialization;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import ru.pathcreator.pyc.rpc.codec.SerializationCodec;
import ru.pathcreator.pyc.rpc.codec.SerializationCodecRegistry;

import java.nio.ByteBuffer;

public final class RpcCodecSupport {

    private static final SerializationCodecRegistry REGISTRY = SerializationCodecRegistry.load();

    private RpcCodecSupport() {
    }

    public static <T> SerializationCodec<T> codecFor(
            final Class<T> type
    ) {
        return REGISTRY.codecFor(type);
    }

    public static <T> UnsafeBuffer encode(
            final T value,
            final Class<T> type
    ) {
        return encode(value, 0, type);
    }

    public static <T> UnsafeBuffer encode(
            final T value,
            final int offset,
            final Class<T> type
    ) {
        final int payloadLength = measure(value, type);
        final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(offset + payloadLength));
        encodeInto(value, offset, type, buffer);
        return buffer;
    }

    public static <T> int measure(
            final T value,
            final Class<T> type
    ) {
        return codecFor(type).measure(value);
    }

    public static <T> int encodeInto(
            final T value,
            final int offset,
            final Class<T> type,
            final MutableDirectBuffer buffer
    ) {
        return codecFor(type).encode(value, buffer, offset);
    }

    public static <T> T decode(
            final int offset,
            final int length,
            final Class<T> type,
            final DirectBuffer buffer
    ) {
        return codecFor(type).decode(buffer, offset, length);
    }
}