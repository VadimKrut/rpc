package ru.pathcreator.pyc.rpc.core.serialization;

import org.agrona.DirectBuffer;
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
        final SerializationCodec<T> codec = codecFor(type);
        final int payloadLength = codec.measure(value);
        final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(offset + payloadLength));
        codec.encode(value, buffer, offset);
        return buffer;
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