package ru.pathcreator.pyc.rpc.client.fixture;

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
        return List.of(new ClientEchoRequestCodec(), new ClientEchoResponseCodec());
    }

    private abstract static class BaseCodec {

        protected static int writeUuid(
                final UUID value,
                final MutableDirectBuffer buffer,
                final int offset
        ) {
            buffer.putLong(offset, value.getMostSignificantBits(), ByteOrder.LITTLE_ENDIAN);
            buffer.putLong(offset + Long.BYTES, value.getLeastSignificantBits(), ByteOrder.LITTLE_ENDIAN);
            return Long.BYTES * 2;
        }

        protected static UUID readUuid(
                final DirectBuffer buffer,
                final int offset
        ) {
            return new UUID(
                    buffer.getLong(offset, ByteOrder.LITTLE_ENDIAN),
                    buffer.getLong(offset + Long.BYTES, ByteOrder.LITTLE_ENDIAN)
            );
        }

        protected static int writeString(
                final String value,
                final MutableDirectBuffer buffer,
                final int offset
        ) {
            final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            buffer.putInt(offset, bytes.length, ByteOrder.LITTLE_ENDIAN);
            buffer.putBytes(offset + Integer.BYTES, bytes);
            return Integer.BYTES + bytes.length;
        }

        protected static String readString(
                final DirectBuffer buffer,
                final int offset
        ) {
            final int length = buffer.getInt(offset, ByteOrder.LITTLE_ENDIAN);
            final byte[] bytes = new byte[length];
            buffer.getBytes(offset + Integer.BYTES, bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static final class ClientEchoRequestCodec extends BaseCodec implements SerializationCodec<ClientEchoRequest> {

        @Override
        public Class<ClientEchoRequest> javaType() {
            return ClientEchoRequest.class;
        }

        @Override
        public SerializationFormat kind() {
            return SerializationFormat.SBE;
        }

        @Override
        public int measure(final ClientEchoRequest value) {
            return Long.BYTES * 2
                   + Integer.BYTES
                   + value.message().getBytes(StandardCharsets.UTF_8).length
                   + Integer.BYTES;
        }

        @Override
        public int encode(
                final ClientEchoRequest value,
                final MutableDirectBuffer buffer,
                final int offset
        ) {
            int cursor = offset;
            cursor += writeUuid(value.requestId(), buffer, cursor);
            cursor += writeString(value.message(), buffer, cursor);
            buffer.putInt(cursor, value.amount(), ByteOrder.LITTLE_ENDIAN);
            return cursor + Integer.BYTES - offset;
        }

        @Override
        public ClientEchoRequest decode(
                final DirectBuffer buffer,
                final int offset,
                final int length
        ) {
            int cursor = offset;
            final UUID requestId = readUuid(buffer, cursor);
            cursor += Long.BYTES * 2;
            final String message = readString(buffer, cursor);
            cursor += Integer.BYTES + message.getBytes(StandardCharsets.UTF_8).length;
            final int amount = buffer.getInt(cursor, ByteOrder.LITTLE_ENDIAN);
            return new ClientEchoRequest(requestId, message, amount);
        }
    }

    private static final class ClientEchoResponseCodec extends BaseCodec implements SerializationCodec<ClientEchoResponse> {

        @Override
        public Class<ClientEchoResponse> javaType() {
            return ClientEchoResponse.class;
        }

        @Override
        public SerializationFormat kind() {
            return SerializationFormat.SBE;
        }

        @Override
        public int measure(final ClientEchoResponse value) {
            return Long.BYTES * 2
                   + Integer.BYTES
                   + value.message().getBytes(StandardCharsets.UTF_8).length
                   + Integer.BYTES;
        }

        @Override
        public int encode(
                final ClientEchoResponse value,
                final MutableDirectBuffer buffer,
                final int offset
        ) {
            int cursor = offset;
            cursor += writeUuid(value.requestId(), buffer, cursor);
            cursor += writeString(value.message(), buffer, cursor);
            buffer.putInt(cursor, value.amount(), ByteOrder.LITTLE_ENDIAN);
            return cursor + Integer.BYTES - offset;
        }

        @Override
        public ClientEchoResponse decode(
                final DirectBuffer buffer,
                final int offset,
                final int length
        ) {
            int cursor = offset;
            final UUID requestId = readUuid(buffer, cursor);
            cursor += Long.BYTES * 2;
            final String message = readString(buffer, cursor);
            cursor += Integer.BYTES + message.getBytes(StandardCharsets.UTF_8).length;
            final int amount = buffer.getInt(cursor, ByteOrder.LITTLE_ENDIAN);
            return new ClientEchoResponse(requestId, message, amount);
        }
    }
}