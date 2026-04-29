package ru.pathcreator.pyc.rpc.client;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import ru.pathcreator.pyc.rpc.client.call.RpcClientCall;
import ru.pathcreator.pyc.rpc.client.fixture.ClientEchoRequest;
import ru.pathcreator.pyc.rpc.client.fixture.ClientEchoResponse;
import ru.pathcreator.pyc.rpc.client.response.RpcClientResult;
import ru.pathcreator.pyc.rpc.contract.RpcMethodContract;
import ru.pathcreator.pyc.rpc.contract.RpcServiceContract;
import ru.pathcreator.pyc.rpc.encryption.RpcPayloadEncryptionException;
import ru.pathcreator.pyc.rpc.encryption.RpcPayloadEncryptions;
import ru.pathcreator.pyc.rpc.core.RpcChannel;
import ru.pathcreator.pyc.rpc.core.RpcRuntime;
import ru.pathcreator.pyc.rpc.core.codex.RpcEnvelope;
import ru.pathcreator.pyc.rpc.core.codex.RpcStatusCodes;
import ru.pathcreator.pyc.rpc.core.config.RpcChannelConfig;
import ru.pathcreator.pyc.rpc.core.exception.RpcCallTimeoutException;
import ru.pathcreator.pyc.rpc.core.exception.RpcPublishTimeoutException;
import ru.pathcreator.pyc.rpc.core.exception.RpcRemoteException;
import ru.pathcreator.pyc.rpc.server.RpcServer;
import ru.pathcreator.pyc.rpc.server.error.RpcStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

final class RpcClientTest {

    private static final long DEFAULT_TIMEOUT_NS = 5_000_000_000L;
    private static final int REQUEST_MESSAGE_TYPE_ID = 701;
    private static final int RESPONSE_MESSAGE_TYPE_ID = 801;
    private static final AtomicInteger PORTS = new AtomicInteger(29_000);
    private static final AtomicInteger STREAMS = new AtomicInteger(5_000);

    @Test
    void shouldSendTypedRequestAndReceiveTypedResponse() {
        try (ChannelPair pair = openChannels()) {
            final RpcServer server = RpcServer.builder(pair.serverChannel()).build();
            final RpcServiceContract service = RpcServiceContract.of(
                    "client-service",
                    RpcMethodContract.of(
                            "client.echo",
                            ClientEchoRequest.class,
                            ClientEchoResponse.class,
                            REQUEST_MESSAGE_TYPE_ID,
                            RESPONSE_MESSAGE_TYPE_ID
                    )
            );
            final RpcMethodContract<ClientEchoRequest, ClientEchoResponse> method = service.requireMethod(
                    "client.echo",
                    ClientEchoRequest.class,
                    ClientEchoResponse.class
            );
            server.register(method, request -> new ClientEchoResponse(
                    request.requestId(),
                    request.message().toUpperCase(Locale.ROOT),
                    request.amount() + 10
            ));
            awaitConnectionSetup();

            final RpcClient client = RpcClient.builder(pair.clientChannel())
                    .defaultTimeoutNs(DEFAULT_TIMEOUT_NS)
                    .build();

            final ClientEchoRequest request = new ClientEchoRequest(UUID.randomUUID(), "hello", 7);
            final ClientEchoResponse response = client.send(method, request);
            assertEquals(new ClientEchoResponse(request.requestId(), "HELLO", 17), response);
        }
    }

    @Test
    void shouldSupportBoundCallAndClientContext() {
        try (ChannelPair pair = openChannels()) {
            final RpcServer server = RpcServer.builder(pair.serverChannel()).build();
            final RpcMethodContract<ClientEchoRequest, ClientEchoResponse> method = RpcMethodContract.of(
                    "client.bound",
                    ClientEchoRequest.class,
                    ClientEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            server.register(method, request -> new ClientEchoResponse(
                    request.requestId(),
                    request.message().toUpperCase(Locale.ROOT),
                    request.amount()
            ));
            awaitConnectionSetup();

            final AtomicLong seenTimeout = new AtomicLong();
            final RpcClient client = RpcClient.builder(pair.clientChannel())
                    .interceptor((context, request, invocation) -> {
                        seenTimeout.set(context.timeoutNs());
                        return invocation.proceed(context, request);
                    })
                    .build();
            final RpcClientCall<ClientEchoRequest, ClientEchoResponse> call = client.bind(method);

            final ClientEchoResponse response = call.send(
                    new ClientEchoRequest(UUID.randomUUID(), "hello", 7),
                    123_456_789L
            );
            assertEquals("HELLO", response.message());
            assertEquals(123_456_789L, seenTimeout.get());
        }
    }

    @Test
    void shouldExposeRemoteErrorAsStructuredClientResult() {
        try (ChannelPair pair = openChannels()) {
            final RpcServer server = RpcServer.builder(pair.serverChannel()).build();
            final RpcMethodContract<ClientEchoRequest, ClientEchoResponse> method = RpcMethodContract.of(
                    "client.bad-request",
                    ClientEchoRequest.class,
                    ClientEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            server.register(method, request -> {
                throw new RpcStatusException(RpcStatusCodes.BAD_REQUEST, "message must not be blank");
            });
            awaitConnectionSetup();

            final RpcClient client = RpcClient.builder(pair.clientChannel()).build();

            final RpcClientResult<ClientEchoResponse> result = client.exchange(
                    method,
                    new ClientEchoRequest(UUID.randomUUID(), "", 1)
            );

            assertFalse(result.isSuccess());
            assertEquals(RpcStatusCodes.BAD_REQUEST, result.statusCode());
            assertEquals(RESPONSE_MESSAGE_TYPE_ID, result.responseMessageTypeId());
            assertNull(result.response());
            assertEquals("message must not be blank", result.errorMessage());
            assertTrue(result.correlationId() > 0L);
        }
    }

    @Test
    void shouldThrowRpcRemoteExceptionOnConvenienceSend() {
        try (ChannelPair pair = openChannels()) {
            final RpcServer server = RpcServer.builder(pair.serverChannel()).build();
            final RpcMethodContract<ClientEchoRequest, ClientEchoResponse> method = RpcMethodContract.of(
                    "client.method-not-allowed",
                    ClientEchoRequest.class,
                    ClientEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            server.register(method, request -> {
                throw new RpcStatusException(RpcStatusCodes.METHOD_NOT_ALLOWED, "method not allowed");
            });
            awaitConnectionSetup();

            final RpcClient client = RpcClient.builder(pair.clientChannel()).build();

            final RpcRemoteException error = assertThrows(
                    RpcRemoteException.class,
                    () -> client.send(method, new ClientEchoRequest(UUID.randomUUID(), "nope", 1))
            );
            assertEquals(RpcStatusCodes.METHOD_NOT_ALLOWED, error.statusCode());
            assertEquals(RESPONSE_MESSAGE_TYPE_ID, error.responseMessageTypeId());
            assertEquals("method not allowed", error.getMessage());
            assertTrue(error.correlationId() > 0L);
        }
    }

    @Test
    void shouldValidateRequestBeforeSend() {
        try (ChannelPair pair = openChannels()) {
            final AtomicInteger interceptorCalls = new AtomicInteger();
            final RpcClient client = RpcClient.builder(pair.clientChannel())
                    .requestValidator((context, request) -> {
                        final ClientEchoRequest typedRequest = (ClientEchoRequest) request;
                        if (typedRequest.message().isBlank()) {
                            throw new IllegalArgumentException(
                                    context.method().name() + ": message must not be blank"
                            );
                        }
                    })
                    .interceptor((context, request, invocation) -> {
                        interceptorCalls.incrementAndGet();
                        return invocation.proceed(context, request);
                    })
                    .build();
            final RpcMethodContract<ClientEchoRequest, ClientEchoResponse> method = RpcMethodContract.of(
                    "client.validated",
                    ClientEchoRequest.class,
                    ClientEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );

            final IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> client.send(method, new ClientEchoRequest(UUID.randomUUID(), "", 1))
            );
            assertEquals("client.validated: message must not be blank", error.getMessage());
            assertEquals(0, interceptorCalls.get());
        }
    }

    @Test
    void shouldValidateResponseAfterExchange() {
        try (ChannelPair pair = openChannels()) {
            final RpcServer server = RpcServer.builder(pair.serverChannel()).build();
            final RpcMethodContract<ClientEchoRequest, ClientEchoResponse> method = RpcMethodContract.of(
                    "client.response-validated",
                    ClientEchoRequest.class,
                    ClientEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            server.register(method, request -> new ClientEchoResponse(
                    request.requestId(),
                    request.message(),
                    -1
            ));
            awaitConnectionSetup();

            final RpcClient client = RpcClient.builder(pair.clientChannel())
                    .responseValidator((context, result) -> {
                        final ClientEchoResponse response = (ClientEchoResponse) result.response();
                        if (result.isSuccess() && response.amount() < 0) {
                            throw new IllegalStateException(context.method().name() + ": negative amount");
                        }
                    })
                    .build();

            final IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> client.exchange(method, new ClientEchoRequest(UUID.randomUUID(), "hello", 1))
            );
            assertEquals("client.response-validated: negative amount", error.getMessage());
        }
    }

    @Test
    void shouldApplyInterceptorsAroundTransportInOrder() {
        try (ChannelPair pair = openChannels()) {
            final RpcServer server = RpcServer.builder(pair.serverChannel()).build();
            final RpcMethodContract<ClientEchoRequest, ClientEchoResponse> method = RpcMethodContract.of(
                    "client.intercepted",
                    ClientEchoRequest.class,
                    ClientEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            server.register(method, request -> new ClientEchoResponse(
                    request.requestId(),
                    request.message().toUpperCase(Locale.ROOT),
                    request.amount()
            ));
            awaitConnectionSetup();

            final List<String> events = new ArrayList<>();
            final RpcClient client = RpcClient.builder(pair.clientChannel())
                    .interceptor((context, request, invocation) -> {
                        events.add("first-before");
                        final RpcClientResult<?> result = invocation.proceed(context, request);
                        events.add("first-after");
                        return result;
                    })
                    .interceptor((context, request, invocation) -> {
                        events.add("second-before:" + context.method().name());
                        final RpcClientResult<?> result = invocation.proceed(context, request);
                        events.add("second-after");
                        return result;
                    })
                    .build();

            final ClientEchoResponse response = client.send(
                    method,
                    new ClientEchoRequest(UUID.randomUUID(), "hello", 1)
            );
            assertEquals("HELLO", response.message());
            assertEquals(
                    List.of("first-before", "second-before:client.intercepted", "second-after", "first-after"),
                    events
            );
        }
    }

    @Test
    void shouldPropagateTimeout() {
        try (ChannelPair pair = openChannels()) {
            pair.serverChannel().registerRequestHandler(REQUEST_MESSAGE_TYPE_ID, (offset, length, correlationId, buffer) -> {
            });
            awaitConnectionSetup();

            final RpcClient client = RpcClient.builder(pair.clientChannel())
                    .defaultTimeoutNs(10_000_000L)
                    .build();
            final RpcMethodContract<ClientEchoRequest, ClientEchoResponse> method = RpcMethodContract.of(
                    "client.timeout",
                    ClientEchoRequest.class,
                    ClientEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );

            assertThrows(
                    RpcCallTimeoutException.class,
                    () -> client.send(method, new ClientEchoRequest(UUID.randomUUID(), "slow", 1))
            );
        }
    }

    @Test
    void shouldPropagatePublishTimeout() {
        final RpcRuntime runtime = RpcRuntime.launchEmbedded();
        final int basePort = PORTS.getAndAdd(2);
        final int streamId = STREAMS.getAndIncrement();
        final RpcChannel clientChannel = runtime.createChannel(
                RpcChannelConfig.createDefault(
                        "aeron:udp?endpoint=localhost:" + basePort,
                        "aeron:udp?endpoint=localhost:" + (basePort + 1),
                        streamId
                )
        );
        try {
            final RpcClient client = RpcClient.builder(clientChannel)
                    .defaultTimeoutNs(10_000_000L)
                    .build();
            final RpcMethodContract<ClientEchoRequest, ClientEchoResponse> method = RpcMethodContract.of(
                    "client.publish-timeout",
                    ClientEchoRequest.class,
                    ClientEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );

            assertThrows(
                    RpcPublishTimeoutException.class,
                    () -> client.send(method, new ClientEchoRequest(UUID.randomUUID(), "slow", 1))
            );
        } finally {
            runtime.close();
        }
    }

    @Test
    void shouldRejectUnexpectedResponseMessageType() {
        try (ChannelPair pair = openChannels()) {
            pair.serverChannel().registerRequestHandler(REQUEST_MESSAGE_TYPE_ID, (offset, length, correlationId, buffer) -> {
                final UnsafeBuffer responseBuffer = new UnsafeBuffer(new byte[RpcEnvelope.HEADER_LENGTH + Integer.BYTES]);
                responseBuffer.putInt(RpcEnvelope.HEADER_LENGTH, 1);
                pair.serverChannel().reply(Integer.BYTES, RESPONSE_MESSAGE_TYPE_ID + 1, correlationId, responseBuffer);
            });

            final RpcClient client = RpcClient.builder(pair.clientChannel()).build();
            final RpcMethodContract<ClientEchoRequest, ClientEchoResponse> method = RpcMethodContract.of(
                    "client.unexpected-type",
                    ClientEchoRequest.class,
                    ClientEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );

            final IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> client.exchange(method, new ClientEchoRequest(UUID.randomUUID(), "x", 1))
            );
            assertTrue(error.getMessage().contains("unexpected responseMessageTypeId"));
        }
    }

    @Test
    void shouldRejectConflictingContractForSameRequestMessageTypeId() {
        try (ChannelPair pair = openChannels()) {
            final RpcClient client = RpcClient.builder(pair.clientChannel()).build();
            final RpcMethodContract<ClientEchoRequest, ClientEchoResponse> first = RpcMethodContract.of(
                    "client.first",
                    ClientEchoRequest.class,
                    ClientEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            final RpcMethodContract<ClientEchoRequest, ClientEchoResponse> conflicting = RpcMethodContract.of(
                    "client.conflicting",
                    ClientEchoRequest.class,
                    ClientEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID + 1
            );

            client.bind(first);
            final IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> client.bind(conflicting)
            );
            assertEquals(
                    "requestMessageTypeId 701 is already bound to contract 'client.first', cannot reuse it for 'client.conflicting'",
                    error.getMessage()
            );
        }
    }

    @Test
    void shouldRoundTripWithAesGcmPayloadEncryption() {
        try (ChannelPair pair = openChannels()) {
            final byte[] key = filledKey((byte) 0x11, 16);
            final RpcMethodContract<ClientEchoRequest, ClientEchoResponse> method = RpcMethodContract.of(
                    "client.encrypted-aes",
                    ClientEchoRequest.class,
                    ClientEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            final RpcServer server = RpcServer.builder(pair.serverChannel())
                    .payloadEncryption(RpcPayloadEncryptions.aesGcm(key, 0x01020304, 1L))
                    .build();
            server.register(method, request -> new ClientEchoResponse(
                    request.requestId(),
                    request.message().toUpperCase(Locale.ROOT),
                    request.amount() + 3
            ));
            awaitConnectionSetup();

            final RpcClient client = RpcClient.builder(pair.clientChannel())
                    .payloadEncryption(RpcPayloadEncryptions.aesGcm(key, 0x05060708, 1L))
                    .build();

            final ClientEchoResponse response = client.send(
                    method,
                    new ClientEchoRequest(UUID.randomUUID(), "secret", 2)
            );
            assertEquals("SECRET", response.message());
            assertEquals(5, response.amount());
        }
    }

    @Test
    void shouldDecryptRemoteErrorWithChaCha20Poly1305() {
        try (ChannelPair pair = openChannels()) {
            final byte[] key = filledKey((byte) 0x22, 32);
            final RpcMethodContract<ClientEchoRequest, ClientEchoResponse> method = RpcMethodContract.of(
                    "client.encrypted-error",
                    ClientEchoRequest.class,
                    ClientEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            final RpcServer server = RpcServer.builder(pair.serverChannel())
                    .payloadEncryption(RpcPayloadEncryptions.chaCha20Poly1305(key, 0x0A0B0C0D, 5L))
                    .build();
            server.register(method, request -> {
                throw new RpcStatusException(RpcStatusCodes.BAD_REQUEST, "encrypted bad request");
            });
            awaitConnectionSetup();

            final RpcClient client = RpcClient.builder(pair.clientChannel())
                    .payloadEncryption(RpcPayloadEncryptions.chaCha20Poly1305(key, 0x0E0F1011, 5L))
                    .build();

            final RpcClientResult<ClientEchoResponse> result = client.exchange(
                    method,
                    new ClientEchoRequest(UUID.randomUUID(), "bad", 1)
            );
            assertFalse(result.isSuccess());
            assertEquals(RpcStatusCodes.BAD_REQUEST, result.statusCode());
            assertEquals("encrypted bad request", result.errorMessage());
        }
    }

    @Test
    void shouldFailWithGenericErrorWhenEncryptedPayloadCannotBeDecrypted() {
        try (ChannelPair pair = openChannels()) {
            final RpcMethodContract<ClientEchoRequest, ClientEchoResponse> method = RpcMethodContract.of(
                    "client.encrypted-mismatch",
                    ClientEchoRequest.class,
                    ClientEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            final RpcServer server = RpcServer.builder(pair.serverChannel())
                    .payloadEncryption(RpcPayloadEncryptions.aesGcm(filledKey((byte) 0x33, 16), 0x11111111, 9L))
                    .build();
            server.register(method, request -> new ClientEchoResponse(
                    request.requestId(),
                    request.message(),
                    request.amount()
            ));
            awaitConnectionSetup();

            final RpcClient client = RpcClient.builder(pair.clientChannel())
                    .payloadEncryption(RpcPayloadEncryptions.aesGcm(filledKey((byte) 0x44, 16), 0x11111111, 9L))
                    .build();

            final RpcPayloadEncryptionException error = assertThrows(
                    RpcPayloadEncryptionException.class,
                    () -> client.send(method, new ClientEchoRequest(UUID.randomUUID(), "boom", 1))
            );
            assertEquals("RPC payload decryption failed", error.getMessage());
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

    private static byte[] filledKey(
            final byte value,
            final int size
    ) {
        final byte[] key = new byte[size];
        java.util.Arrays.fill(key, value);
        return key;
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
