package ru.pathcreator.pyc.rpc.observability.fixture;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import ru.pathcreator.pyc.rpc.codec.SerializationCodec;
import ru.pathcreator.pyc.rpc.codec.SerializationCodecFactory;
import ru.pathcreator.pyc.rpc.codec.SerializationFormat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public final class TestCodecFactory implements SerializationCodecFactory {

    @Override
    public List<SerializationCodec<?>> codecs() {
        return List.of(new MetricsEchoRequestCodec(), new MetricsEchoResponseCodec());
    }

    private abstract static class BaseCodec {

        protected static int sizeOf(
                final String value
        ) {
            return Integer.BYTES + value.getBytes(StandardCharsets.UTF_8).length;
        }

        protected static int putString(
                final MutableDirectBuffer buffer,
                final int offset,
                final String value
        ) {
            final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            buffer.putInt(offset, bytes.length);
            buffer.putBytes(offset + Integer.BYTES, bytes);
            return Integer.BYTES + bytes.length;
        }

        protected static String readString(
                final DirectBuffer buffer,
                final int offset
        ) {
            final int length = buffer.getInt(offset);
            final byte[] bytes = new byte[length];
            buffer.getBytes(offset + Integer.BYTES, bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        protected static int stringLength(
                final DirectBuffer buffer,
                final int offset
        ) {
            return Integer.BYTES + buffer.getInt(offset);
        }

        protected static void putUuid(
                final MutableDirectBuffer buffer,
                final int offset,
                final UUID value
        ) {
            buffer.putLong(offset, value.getMostSignificantBits());
            buffer.putLong(offset + Long.BYTES, value.getLeastSignificantBits());
        }

        protected static UUID readUuid(
                final DirectBuffer buffer,
                final int offset
        ) {
            return new UUID(buffer.getLong(offset), buffer.getLong(offset + Long.BYTES));
        }
    }

    private static final class MetricsEchoRequestCodec extends BaseCodec implements SerializationCodec<MetricsEchoRequest> {

        @Override
        public Class<MetricsEchoRequest> javaType() {
            return MetricsEchoRequest.class;
        }

        @Override
        public SerializationFormat kind() {
            return SerializationFormat.SBE;
        }

        @Override
        public int measure(
                final MetricsEchoRequest value
        ) {
            return Long.BYTES * 2 + sizeOf(value.message()) + Integer.BYTES;
        }

        @Override
        public int encode(
                final MetricsEchoRequest value,
                final MutableDirectBuffer buffer,
                final int offset
        ) {
            putUuid(buffer, offset, value.requestId());
            int position = offset + Long.BYTES * 2;
            position += putString(buffer, position, value.message());
            buffer.putInt(position, value.amount());
            return measure(value);
        }

        @Override
        public MetricsEchoRequest decode(
                final DirectBuffer buffer,
                final int offset,
                final int length
        ) {
            final UUID requestId = readUuid(buffer, offset);
            final int messageOffset = offset + Long.BYTES * 2;
            final String message = readString(buffer, messageOffset);
            final int amount = buffer.getInt(messageOffset + stringLength(buffer, messageOffset));
            return new MetricsEchoRequest(requestId, message, amount);
        }
    }

    private static final class MetricsEchoResponseCodec extends BaseCodec implements SerializationCodec<MetricsEchoResponse> {

        @Override
        public Class<MetricsEchoResponse> javaType() {
            return MetricsEchoResponse.class;
        }

        @Override
        public SerializationFormat kind() {
            return SerializationFormat.SBE;
        }

        @Override
        public int measure(
                final MetricsEchoResponse value
        ) {
            return Long.BYTES * 2 + sizeOf(value.message()) + Integer.BYTES;
        }

        @Override
        public int encode(
                final MetricsEchoResponse value,
                final MutableDirectBuffer buffer,
                final int offset
        ) {
            putUuid(buffer, offset, value.requestId());
            int position = offset + Long.BYTES * 2;
            position += putString(buffer, position, value.message());
            buffer.putInt(position, value.amount());
            return measure(value);
        }

        @Override
        public MetricsEchoResponse decode(
                final DirectBuffer buffer,
                final int offset,
                final int length
        ) {
            final UUID requestId = readUuid(buffer, offset);
            final int messageOffset = offset + Long.BYTES * 2;
            final String message = readString(buffer, messageOffset);
            final int amount = buffer.getInt(messageOffset + stringLength(buffer, messageOffset));
            return new MetricsEchoResponse(requestId, message, amount);
        }
    }
}
