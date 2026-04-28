package ru.pathcreator.pyc.rpc.codec;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public interface SerializationCodec<T> {
    Class<T> javaType();

    SerializationFormat kind();

    int measure(T value);

    int encode(T value, MutableDirectBuffer buffer, int offset);

    T decode(DirectBuffer buffer, int offset, int length);

    default byte[] encodeToBytes(final T value) {
        final byte[] payload = new byte[measure(value)];
        encode(value, new UnsafeBuffer(payload), 0);
        return payload;
    }

    default int encode(final T value, final byte[] buffer, final int offset) {
        return encode(value, new UnsafeBuffer(buffer), offset);
    }

    default T decode(final byte[] buffer, final int offset, final int length) {
        return decode(new UnsafeBuffer(buffer), offset, length);
    }
}