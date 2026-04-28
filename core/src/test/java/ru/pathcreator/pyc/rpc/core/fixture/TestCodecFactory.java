package ru.pathcreator.pyc.rpc.core.fixture;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import ru.pathcreator.pyc.rpc.codec.SerializationCodec;
import ru.pathcreator.pyc.rpc.codec.SerializationCodecFactory;
import ru.pathcreator.pyc.rpc.codec.SerializationFormat;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public final class TestCodecFactory implements SerializationCodecFactory {

    @Override
    public List<SerializationCodec<?>> codecs() {
        return List.of(new CoreEchoRequestCodec(), new CoreEchoResponseCodec());
    }

    private abstract static class AbstractEchoCodec<T> implements SerializationCodec<T> {

        @Override
        public SerializationFormat kind() {
            return SerializationFormat.SBE;
        }

        protected static int measureString(final String value) {
            final byte[] bytes = stringBytes(value);
            return Integer.BYTES + bytes.length;
        }

        protected static int writeUuid(final UUID value, final MutableDirectBuffer buffer, final int offset) {
            buffer.putLong(offset, value.getMostSignificantBits(), ByteOrder.LITTLE_ENDIAN);
            buffer.putLong(offset + Long.BYTES, value.getLeastSignificantBits(), ByteOrder.LITTLE_ENDIAN);
            return Long.BYTES * 2;
        }

        protected static UUID readUuid(final DirectBuffer buffer, final int offset) {
            return new UUID(
                    buffer.getLong(offset, ByteOrder.LITTLE_ENDIAN),
                    buffer.getLong(offset + Long.BYTES, ByteOrder.LITTLE_ENDIAN)
            );
        }

        protected static int writeString(final String value, final MutableDirectBuffer buffer, final int offset) {
            final byte[] bytes = stringBytes(value);
            buffer.putInt(offset, bytes.length, ByteOrder.LITTLE_ENDIAN);
            buffer.putBytes(offset + Integer.BYTES, bytes);
            return Integer.BYTES + bytes.length;
        }

        protected static String readString(final DirectBuffer buffer, final int offset) {
            final int length = buffer.getInt(offset, ByteOrder.LITTLE_ENDIAN);
            final byte[] bytes = new byte[length];
            buffer.getBytes(offset + Integer.BYTES, bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        private static byte[] stringBytes(final String value) {
            return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static final class CoreEchoRequestCodec extends AbstractEchoCodec<CoreEchoRequest> {

        @Override
        public Class<CoreEchoRequest> javaType() {
            return CoreEchoRequest.class;
        }

        @Override
        public int measure(final CoreEchoRequest value) {
            return Long.BYTES * 2 + measureString(value.message()) + Integer.BYTES;
        }

        @Override
        public int encode(final CoreEchoRequest value, final MutableDirectBuffer buffer, final int offset) {
            int cursor = offset;
            cursor += writeUuid(value.requestId(), buffer, cursor);
            cursor += writeString(value.message(), buffer, cursor);
            buffer.putInt(cursor, value.amount(), ByteOrder.LITTLE_ENDIAN);
            cursor += Integer.BYTES;
            return cursor - offset;
        }

        @Override
        public CoreEchoRequest decode(final DirectBuffer buffer, final int offset, final int length) {
            int cursor = offset;
            final UUID requestId = readUuid(buffer, cursor);
            cursor += Long.BYTES * 2;
            final String message = readString(buffer, cursor);
            cursor += measureString(message);
            final int amount = buffer.getInt(cursor, ByteOrder.LITTLE_ENDIAN);
            return new CoreEchoRequest(requestId, message, amount);
        }
    }

    private static final class CoreEchoResponseCodec extends AbstractEchoCodec<CoreEchoResponse> {

        @Override
        public Class<CoreEchoResponse> javaType() {
            return CoreEchoResponse.class;
        }

        @Override
        public int measure(final CoreEchoResponse value) {
            return Long.BYTES * 2 + measureString(value.message()) + Integer.BYTES;
        }

        @Override
        public int encode(final CoreEchoResponse value, final MutableDirectBuffer buffer, final int offset) {
            int cursor = offset;
            cursor += writeUuid(value.requestId(), buffer, cursor);
            cursor += writeString(value.message(), buffer, cursor);
            buffer.putInt(cursor, value.amount(), ByteOrder.LITTLE_ENDIAN);
            cursor += Integer.BYTES;
            return cursor - offset;
        }

        @Override
        public CoreEchoResponse decode(final DirectBuffer buffer, final int offset, final int length) {
            int cursor = offset;
            final UUID requestId = readUuid(buffer, cursor);
            cursor += Long.BYTES * 2;
            final String message = readString(buffer, cursor);
            cursor += measureString(message);
            final int amount = buffer.getInt(cursor, ByteOrder.LITTLE_ENDIAN);
            return new CoreEchoResponse(requestId, message, amount);
        }
    }
}
