package ru.pathcreator.pyc.rpc.bootstrap.fixture;

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
        return List.of(
                new BootstrapEchoRequestCodec(),
                new BootstrapEchoResponseCodec()
        );
    }

    private static final class BootstrapEchoRequestCodec implements SerializationCodec<BootstrapEchoRequest> {

        @Override
        public Class<BootstrapEchoRequest> javaType() {
            return BootstrapEchoRequest.class;
        }

        @Override
        public SerializationFormat kind() {
            return SerializationFormat.SBE;
        }

        @Override
        public int measure(final BootstrapEchoRequest value) {
            return 16 + Integer.BYTES + value.message().getBytes(StandardCharsets.UTF_8).length + Integer.BYTES;
        }

        @Override
        public int encode(final BootstrapEchoRequest value, final MutableDirectBuffer buffer, final int offset) {
            putUuid(buffer, offset, value.requestId());
            final byte[] message = value.message().getBytes(StandardCharsets.UTF_8);
            buffer.putInt(offset + 16, message.length, ByteOrder.LITTLE_ENDIAN);
            buffer.putBytes(offset + 20, message);
            buffer.putInt(offset + 20 + message.length, value.amount(), ByteOrder.LITTLE_ENDIAN);
            return 20 + message.length + Integer.BYTES;
        }

        @Override
        public BootstrapEchoRequest decode(final DirectBuffer buffer, final int offset, final int length) {
            final UUID requestId = readUuid(buffer, offset);
            final int messageLength = buffer.getInt(offset + 16, ByteOrder.LITTLE_ENDIAN);
            final byte[] message = new byte[messageLength];
            buffer.getBytes(offset + 20, message);
            final int amount = buffer.getInt(offset + 20 + messageLength, ByteOrder.LITTLE_ENDIAN);
            return new BootstrapEchoRequest(requestId, new String(message, StandardCharsets.UTF_8), amount);
        }
    }

    private static final class BootstrapEchoResponseCodec implements SerializationCodec<BootstrapEchoResponse> {

        @Override
        public Class<BootstrapEchoResponse> javaType() {
            return BootstrapEchoResponse.class;
        }

        @Override
        public SerializationFormat kind() {
            return SerializationFormat.SBE;
        }

        @Override
        public int measure(final BootstrapEchoResponse value) {
            return 16 + Integer.BYTES + value.message().getBytes(StandardCharsets.UTF_8).length + Integer.BYTES;
        }

        @Override
        public int encode(final BootstrapEchoResponse value, final MutableDirectBuffer buffer, final int offset) {
            putUuid(buffer, offset, value.requestId());
            final byte[] message = value.message().getBytes(StandardCharsets.UTF_8);
            buffer.putInt(offset + 16, message.length, ByteOrder.LITTLE_ENDIAN);
            buffer.putBytes(offset + 20, message);
            buffer.putInt(offset + 20 + message.length, value.amount(), ByteOrder.LITTLE_ENDIAN);
            return 20 + message.length + Integer.BYTES;
        }

        @Override
        public BootstrapEchoResponse decode(final DirectBuffer buffer, final int offset, final int length) {
            final UUID requestId = readUuid(buffer, offset);
            final int messageLength = buffer.getInt(offset + 16, ByteOrder.LITTLE_ENDIAN);
            final byte[] message = new byte[messageLength];
            buffer.getBytes(offset + 20, message);
            final int amount = buffer.getInt(offset + 20 + messageLength, ByteOrder.LITTLE_ENDIAN);
            return new BootstrapEchoResponse(requestId, new String(message, StandardCharsets.UTF_8), amount);
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
