package ru.pathcreator.pyc.rpc.spring.fixture;

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
    public java.util.Collection<SerializationCodec<?>> codecs() {
        return List.of(new SpringEchoRequestCodec(), new SpringEchoResponseCodec());
    }

    private static final class SpringEchoRequestCodec implements SerializationCodec<SpringEchoRequest> {

        @Override
        public Class<SpringEchoRequest> javaType() {
            return SpringEchoRequest.class;
        }

        @Override
        public SerializationFormat kind() {
            return SerializationFormat.SBE;
        }

        @Override
        public int measure(final SpringEchoRequest value) {
            return 16 + Integer.BYTES + value.message().getBytes(StandardCharsets.UTF_8).length;
        }

        @Override
        public int encode(final SpringEchoRequest value, final MutableDirectBuffer buffer, final int offset) {
            putUuid(buffer, offset, value.requestId());
            final byte[] message = value.message().getBytes(StandardCharsets.UTF_8);
            buffer.putInt(offset + 16, message.length, ByteOrder.LITTLE_ENDIAN);
            buffer.putBytes(offset + 20, message);
            return 20 + message.length;
        }

        @Override
        public SpringEchoRequest decode(final DirectBuffer buffer, final int offset, final int length) {
            final UUID requestId = readUuid(buffer, offset);
            final int messageLength = buffer.getInt(offset + 16, ByteOrder.LITTLE_ENDIAN);
            final byte[] message = new byte[messageLength];
            buffer.getBytes(offset + 20, message);
            return new SpringEchoRequest(requestId, new String(message, StandardCharsets.UTF_8));
        }
    }

    private static final class SpringEchoResponseCodec implements SerializationCodec<SpringEchoResponse> {

        @Override
        public Class<SpringEchoResponse> javaType() {
            return SpringEchoResponse.class;
        }

        @Override
        public SerializationFormat kind() {
            return SerializationFormat.SBE;
        }

        @Override
        public int measure(final SpringEchoResponse value) {
            return 16 + Integer.BYTES + value.message().getBytes(StandardCharsets.UTF_8).length;
        }

        @Override
        public int encode(final SpringEchoResponse value, final MutableDirectBuffer buffer, final int offset) {
            putUuid(buffer, offset, value.requestId());
            final byte[] message = value.message().getBytes(StandardCharsets.UTF_8);
            buffer.putInt(offset + 16, message.length, ByteOrder.LITTLE_ENDIAN);
            buffer.putBytes(offset + 20, message);
            return 20 + message.length;
        }

        @Override
        public SpringEchoResponse decode(final DirectBuffer buffer, final int offset, final int length) {
            final UUID requestId = readUuid(buffer, offset);
            final int messageLength = buffer.getInt(offset + 16, ByteOrder.LITTLE_ENDIAN);
            final byte[] message = new byte[messageLength];
            buffer.getBytes(offset + 20, message);
            return new SpringEchoResponse(requestId, new String(message, StandardCharsets.UTF_8));
        }
    }

    private static void putUuid(final MutableDirectBuffer buffer, final int offset, final UUID value) {
        buffer.putLong(offset, value.getMostSignificantBits(), ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(offset + 8, value.getLeastSignificantBits(), ByteOrder.LITTLE_ENDIAN);
    }

    private static UUID readUuid(final DirectBuffer buffer, final int offset) {
        return new UUID(
                buffer.getLong(offset, ByteOrder.LITTLE_ENDIAN),
                buffer.getLong(offset + 8, ByteOrder.LITTLE_ENDIAN)
        );
    }
}