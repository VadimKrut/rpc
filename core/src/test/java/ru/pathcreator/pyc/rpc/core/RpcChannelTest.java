package ru.pathcreator.pyc.rpc.core;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import ru.pathcreator.pyc.rpc.core.codex.RpcEnvelope;
import ru.pathcreator.pyc.rpc.core.codex.RpcResponseFrame;
import ru.pathcreator.pyc.rpc.core.codex.RpcStatusCodes;
import ru.pathcreator.pyc.rpc.core.config.RpcChannelConfig;
import ru.pathcreator.pyc.rpc.core.exception.RpcCallTimeoutException;
import ru.pathcreator.pyc.rpc.core.exception.RpcPublishTimeoutException;
import ru.pathcreator.pyc.rpc.core.exception.RpcRemoteException;
import ru.pathcreator.pyc.rpc.core.fixture.CoreEchoRequest;
import ru.pathcreator.pyc.rpc.core.fixture.CoreEchoResponse;
import ru.pathcreator.pyc.rpc.core.serialization.RpcCodecSupport;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RpcChannelTest {

    private static final long TIMEOUT_NS = 1_000_000_000L;
    private static final int REQUEST_MESSAGE_TYPE_ID = 501;
    private static final int RESPONSE_MESSAGE_TYPE_ID = 601;
    private static final AtomicInteger PORTS = new AtomicInteger(27_000);
    private static final AtomicInteger STREAMS = new AtomicInteger(4_000);

    @Test
    void shouldRoundTripTypedRequestOverCore() {
        try (ChannelPair pair = openChannels()) {
            pair.serverChannel().registerHandler(
                    CoreEchoRequest.class,
                    CoreEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID,
                    request -> new CoreEchoResponse(request.requestId(), request.message().toUpperCase(), request.amount() + 1)
            );

            final CoreEchoResponse response = pair.clientChannel().send(
                    new CoreEchoRequest(UUID.randomUUID(), "abc", 4),
                    TIMEOUT_NS,
                    CoreEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            assertEquals("ABC", response.message());
            assertEquals(5, response.amount());
        }
    }

    @Test
    void shouldExposeNonSuccessStatusInRawResponse() {
        try (ChannelPair pair = openChannels()) {
            pair.serverChannel().registerRequestHandler(REQUEST_MESSAGE_TYPE_ID, (offset, length, correlationId, buffer) -> {
                final UnsafeBuffer responseBuffer = new UnsafeBuffer(new byte[RpcEnvelope.HEADER_LENGTH + 18]);
                final int payloadLength = responseBuffer.putStringWithoutLengthUtf8(
                        RpcEnvelope.HEADER_LENGTH,
                        "bad request body"
                );
                pair.serverChannel().reply(
                        payloadLength,
                        RESPONSE_MESSAGE_TYPE_ID,
                        RpcStatusCodes.BAD_REQUEST,
                        correlationId,
                        responseBuffer
                );
            });

            final UnsafeBuffer requestBuffer = RpcCodecSupport.encode(
                    new CoreEchoRequest(UUID.randomUUID(), "abc", 4),
                    RpcEnvelope.HEADER_LENGTH,
                    CoreEchoRequest.class
            );
            final RpcResponseFrame response = pair.clientChannel().sendFrame(
                    TIMEOUT_NS,
                    requestBuffer.capacity() - RpcEnvelope.HEADER_LENGTH,
                    REQUEST_MESSAGE_TYPE_ID,
                    requestBuffer
            );

            assertEquals(RpcStatusCodes.BAD_REQUEST, response.statusCode());
            assertEquals("bad request body", payloadText(response));
        }
    }

    @Test
    void shouldExposeSuccessResponseMetadataInFrame() {
        try (ChannelPair pair = openChannels()) {
            pair.serverChannel().registerRequestHandler(REQUEST_MESSAGE_TYPE_ID, (offset, length, correlationId, buffer) -> {
                final UnsafeBuffer responseBuffer = new UnsafeBuffer(new byte[RpcEnvelope.HEADER_LENGTH + Long.BYTES]);
                responseBuffer.putLong(RpcEnvelope.HEADER_LENGTH, 77L, java.nio.ByteOrder.LITTLE_ENDIAN);
                pair.serverChannel().reply(Long.BYTES, RESPONSE_MESSAGE_TYPE_ID, correlationId, responseBuffer);
            });

            final UnsafeBuffer requestBuffer = RpcCodecSupport.encode(
                    new CoreEchoRequest(UUID.randomUUID(), "abc", 4),
                    RpcEnvelope.HEADER_LENGTH,
                    CoreEchoRequest.class
            );
            final RpcResponseFrame response = pair.clientChannel().sendFrame(
                    TIMEOUT_NS,
                    requestBuffer.capacity() - RpcEnvelope.HEADER_LENGTH,
                    REQUEST_MESSAGE_TYPE_ID,
                    requestBuffer
            );

            assertTrue(response.isSuccess());
            assertEquals(RpcStatusCodes.OK, response.statusCode());
            assertEquals(RESPONSE_MESSAGE_TYPE_ID, response.messageTypeId());
            assertEquals(Long.BYTES, response.payloadLength());
            assertFalse(response.correlationId() == 0L);
            assertEquals(77L, response.buffer().getLong(response.payloadOffset(), java.nio.ByteOrder.LITTLE_ENDIAN));
        }
    }

    @Test
    void shouldThrowRpcRemoteExceptionForTypedSendOnRemoteError() {
        try (ChannelPair pair = openChannels()) {
            pair.serverChannel().registerRequestHandler(REQUEST_MESSAGE_TYPE_ID, (offset, length, correlationId, buffer) -> {
                final UnsafeBuffer responseBuffer = new UnsafeBuffer(new byte[RpcEnvelope.HEADER_LENGTH + 18]);
                final int payloadLength = responseBuffer.putStringWithoutLengthUtf8(
                        RpcEnvelope.HEADER_LENGTH,
                        "method not allowed"
                );
                pair.serverChannel().reply(
                        payloadLength,
                        RESPONSE_MESSAGE_TYPE_ID,
                        RpcStatusCodes.METHOD_NOT_ALLOWED,
                        correlationId,
                        responseBuffer
                );
            });

            final RpcRemoteException error = assertThrows(
                    RpcRemoteException.class,
                    () -> pair.clientChannel().send(
                            new CoreEchoRequest(UUID.randomUUID(), "abc", 4),
                            TIMEOUT_NS,
                            CoreEchoResponse.class,
                            REQUEST_MESSAGE_TYPE_ID,
                            RESPONSE_MESSAGE_TYPE_ID
                    )
            );
            assertEquals(RpcStatusCodes.METHOD_NOT_ALLOWED, error.statusCode());
            assertEquals(RESPONSE_MESSAGE_TYPE_ID, error.responseMessageTypeId());
            assertEquals("method not allowed", error.getMessage());
        }
    }

    @Test
    void shouldRejectUnexpectedTypedResponseMessageType() {
        try (ChannelPair pair = openChannels()) {
            pair.serverChannel().registerRequestHandler(REQUEST_MESSAGE_TYPE_ID, (offset, length, correlationId, buffer) -> {
                final UnsafeBuffer responseBuffer = new UnsafeBuffer(new byte[RpcEnvelope.HEADER_LENGTH + Long.BYTES]);
                responseBuffer.putLong(RpcEnvelope.HEADER_LENGTH, 11L, java.nio.ByteOrder.LITTLE_ENDIAN);
                pair.serverChannel().reply(Long.BYTES, RESPONSE_MESSAGE_TYPE_ID + 1, correlationId, responseBuffer);
            });

            final IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> pair.clientChannel().send(
                            new CoreEchoRequest(UUID.randomUUID(), "abc", 4),
                            TIMEOUT_NS,
                            CoreEchoResponse.class,
                            REQUEST_MESSAGE_TYPE_ID,
                            RESPONSE_MESSAGE_TYPE_ID
                    )
            );
            assertTrue(error.getMessage().contains("unexpected responseMessageTypeId"));
        }
    }

    @Test
    void shouldTimeoutWhenNoReplyArrives() {
        try (ChannelPair pair = openChannels()) {
            pair.serverChannel().registerRequestHandler(REQUEST_MESSAGE_TYPE_ID, (offset, length, correlationId, buffer) -> {
            });
            awaitConnectionSetup();

            final UnsafeBuffer requestBuffer = RpcCodecSupport.encode(
                    new CoreEchoRequest(UUID.randomUUID(), "abc", 4),
                    RpcEnvelope.HEADER_LENGTH,
                    CoreEchoRequest.class
            );

            assertThrows(
                    RpcCallTimeoutException.class,
                    () -> pair.clientChannel().sendRaw(
                            10_000_000L,
                            requestBuffer.capacity() - RpcEnvelope.HEADER_LENGTH,
                            REQUEST_MESSAGE_TYPE_ID,
                            requestBuffer
                    )
            );
        }
    }

    @Test
    void shouldTimeoutWhenRequestCannotBePublished() {
        final RpcRuntime runtime = RpcRuntime.launchEmbedded();
        final int basePort = PORTS.getAndAdd(2);
        final int streamId = STREAMS.getAndIncrement();
        final RpcChannel channel = runtime.createChannel(
                RpcChannelConfig.createDefault(
                        "aeron:udp?endpoint=localhost:" + basePort,
                        "aeron:udp?endpoint=localhost:" + (basePort + 1),
                        streamId
                )
        );
        try {
            final UnsafeBuffer requestBuffer = RpcCodecSupport.encode(
                    new CoreEchoRequest(UUID.randomUUID(), "abc", 4),
                    RpcEnvelope.HEADER_LENGTH,
                    CoreEchoRequest.class
            );

            assertThrows(
                    RpcPublishTimeoutException.class,
                    () -> channel.sendRaw(
                            10_000_000L,
                            requestBuffer.capacity() - RpcEnvelope.HEADER_LENGTH,
                            REQUEST_MESSAGE_TYPE_ID,
                            requestBuffer
                    )
            );
        } finally {
            runtime.close();
        }
    }

    private static ChannelPair openChannels() {
        final int basePort = PORTS.getAndAdd(2);
        final int streamId = STREAMS.getAndIncrement();
        final RpcRuntime runtime = RpcRuntime.launchEmbedded();
        final RpcChannel clientChannel = runtime.createChannel(
                RpcChannelConfig.createDefault(
                        "aeron:udp?endpoint=localhost:" + basePort,
                        "aeron:udp?endpoint=localhost:" + (basePort + 1),
                        streamId
                )
        );
        final RpcChannel serverChannel = runtime.createChannel(
                RpcChannelConfig.createDefault(
                        "aeron:udp?endpoint=localhost:" + (basePort + 1),
                        "aeron:udp?endpoint=localhost:" + basePort,
                        streamId
                )
        );
        return new ChannelPair(runtime, clientChannel, serverChannel);
    }

    private static void awaitConnectionSetup() {
        try {
            Thread.sleep(200L);
        } catch (final InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("connection setup interrupted", error);
        }
    }

    private static String payloadText(final RpcResponseFrame response) {
        final int payloadLength = response.payloadLength();
        final byte[] bytes = new byte[payloadLength];
        response.buffer().getBytes(response.payloadOffset(), bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private record ChannelPair(
            RpcRuntime runtime,
            RpcChannel clientChannel,
            RpcChannel serverChannel
    ) implements AutoCloseable {

        @Override
        public void close() {
            this.runtime.close();
        }
    }
}
