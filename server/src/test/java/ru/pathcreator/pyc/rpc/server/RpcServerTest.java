package ru.pathcreator.pyc.rpc.server;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import ru.pathcreator.pyc.rpc.core.RpcChannel;
import ru.pathcreator.pyc.rpc.core.RpcRuntime;
import ru.pathcreator.pyc.rpc.core.codex.RpcEnvelope;
import ru.pathcreator.pyc.rpc.core.codex.RpcStatusCodes;
import ru.pathcreator.pyc.rpc.core.config.RpcChannelConfig;
import ru.pathcreator.pyc.rpc.server.error.RpcServerErrorResponse;
import ru.pathcreator.pyc.rpc.server.error.RpcStatusException;
import ru.pathcreator.pyc.rpc.server.fixture.ServerEchoRequest;
import ru.pathcreator.pyc.rpc.server.fixture.ServerEchoResponse;
import ru.pathcreator.pyc.rpc.server.handler.RpcServerMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

final class RpcServerTest {

    private static final long TIMEOUT_NS = 5_000_000_000L;
    private static final int REQUEST_MESSAGE_TYPE_ID = 101;
    private static final int RESPONSE_MESSAGE_TYPE_ID = 201;
    private static final AtomicInteger PORTS = new AtomicInteger(25_000);
    private static final AtomicInteger STREAMS = new AtomicInteger(3_000);

    @Test
    void shouldServeTypedRequestAndResponse() {
        try (ChannelPair pair = openChannels()) {
            final RpcServer server = RpcServer.builder(pair.serverChannel()).build();
            final RpcServerMethod<ServerEchoRequest, ServerEchoResponse> method = RpcServerMethod.of(
                    "echo.uppercase",
                    ServerEchoRequest.class,
                    ServerEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            server.register(method, request -> new ServerEchoResponse(
                    request.requestId(),
                    request.message().toUpperCase(Locale.ROOT),
                    request.amount() * 2
            ));

            final ServerEchoRequest request = new ServerEchoRequest(
                    UUID.randomUUID(),
                    "hello",
                    21
            );
            final ServerEchoResponse response = pair.clientChannel().send(
                    request,
                    TIMEOUT_NS,
                    ServerEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            assertEquals(
                    new ServerEchoResponse(request.requestId(), "HELLO", 42),
                    response
            );
        }
    }

    @Test
    void shouldExposeRequestContextToContextAwareHandler() {
        try (ChannelPair pair = openChannels()) {
            final RpcServer server = RpcServer.builder(pair.serverChannel()).build();
            final RpcServerMethod<ServerEchoRequest, ServerEchoResponse> method = RpcServerMethod.of(
                    "echo.context",
                    ServerEchoRequest.class,
                    ServerEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            final AtomicLong seenCorrelationId = new AtomicLong();
            final AtomicInteger seenPayloadLength = new AtomicInteger();
            final AtomicInteger seenResponseTypeId = new AtomicInteger();
            server.register(method, (context, request) -> {
                seenCorrelationId.set(context.correlationId());
                seenPayloadLength.set(context.requestPayloadLength());
                seenResponseTypeId.set(context.responseMessageTypeId());
                return new ServerEchoResponse(request.requestId(), context.method().name(), request.amount());
            });

            final ServerEchoRequest request = new ServerEchoRequest(UUID.randomUUID(), "ctx", 7);
            final ServerEchoResponse response = pair.clientChannel().send(
                    request,
                    TIMEOUT_NS,
                    ServerEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            assertEquals("echo.context", response.message());
            assertTrue(seenCorrelationId.get() > 0L);
            assertEquals(RESPONSE_MESSAGE_TYPE_ID, seenResponseTypeId.get());
            assertTrue(seenPayloadLength.get() > 0);
        }
    }

    @Test
    void shouldReplyWithMappedErrorWhenHandlerFails() {
        try (ChannelPair pair = openChannels()) {
            final RpcServer server = RpcServer.builder(pair.serverChannel())
                    .exceptionMapper((method, request, error) -> RpcServerErrorResponse.of(
                            RpcStatusCodes.BAD_GATEWAY,
                            method.name() + ": " + error.getMessage()
                    ))
                    .build();
            final RpcServerMethod<ServerEchoRequest, ServerEchoResponse> method = RpcServerMethod.of(
                    "echo.failure",
                    ServerEchoRequest.class,
                    ServerEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            server.register(method, request -> {
                throw new IllegalArgumentException("boom for " + request.message());
            });

            final UnsafeBuffer requestBuffer = pair.encodeRequest(new ServerEchoRequest(UUID.randomUUID(), "fail", 1));
            final UnsafeBuffer response = pair.clientChannel().sendRaw(
                    TIMEOUT_NS,
                    requestBuffer.capacity() - RpcEnvelope.HEADER_LENGTH,
                    REQUEST_MESSAGE_TYPE_ID,
                    requestBuffer
            );
            assertEquals(RpcStatusCodes.BAD_GATEWAY, RpcEnvelope.statusCode(0, response));
            assertEquals("echo.failure: boom for fail", readPayloadText(response));
        }
    }

    @Test
    void shouldAllowHandlerToThrowExplicitStatus() {
        try (ChannelPair pair = openChannels()) {
            final RpcServer server = RpcServer.builder(pair.serverChannel()).build();
            final RpcServerMethod<ServerEchoRequest, ServerEchoResponse> method = RpcServerMethod.of(
                    "echo.bad-request",
                    ServerEchoRequest.class,
                    ServerEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            server.register(method, request -> {
                throw new RpcStatusException(RpcStatusCodes.BAD_REQUEST, "message must not be blank");
            });

            final UnsafeBuffer requestBuffer = pair.encodeRequest(new ServerEchoRequest(UUID.randomUUID(), "", 1));
            final UnsafeBuffer response = pair.clientChannel().sendRaw(
                    TIMEOUT_NS,
                    requestBuffer.capacity() - RpcEnvelope.HEADER_LENGTH,
                    REQUEST_MESSAGE_TYPE_ID,
                    requestBuffer
            );
            assertEquals(RpcStatusCodes.BAD_REQUEST, RpcEnvelope.statusCode(0, response));
            assertEquals("message must not be blank", readPayloadText(response));
        }
    }

    @Test
    void shouldValidateRequestBeforeHandler() {
        try (ChannelPair pair = openChannels()) {
            final AtomicInteger handlerCalls = new AtomicInteger();
            final RpcServer server = RpcServer.builder(pair.serverChannel())
                    .requestValidator((context, request) -> {
                        final ServerEchoRequest typedRequest = (ServerEchoRequest) request;
                        if (typedRequest.message().isBlank()) {
                            throw new RpcStatusException(
                                    RpcStatusCodes.BAD_REQUEST,
                                    context.method().name() + ": message must not be blank"
                            );
                        }
                    })
                    .build();
            final RpcServerMethod<ServerEchoRequest, ServerEchoResponse> method = RpcServerMethod.of(
                    "echo.validated",
                    ServerEchoRequest.class,
                    ServerEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            server.register(method, request -> {
                handlerCalls.incrementAndGet();
                return new ServerEchoResponse(request.requestId(), request.message(), request.amount());
            });

            final UnsafeBuffer requestBuffer = pair.encodeRequest(new ServerEchoRequest(UUID.randomUUID(), "", 1));
            final UnsafeBuffer response = pair.clientChannel().sendRaw(
                    TIMEOUT_NS,
                    requestBuffer.capacity() - RpcEnvelope.HEADER_LENGTH,
                    REQUEST_MESSAGE_TYPE_ID,
                    requestBuffer
            );
            assertEquals(RpcStatusCodes.BAD_REQUEST, RpcEnvelope.statusCode(0, response));
            assertEquals("echo.validated: message must not be blank", readPayloadText(response));
            assertEquals(0, handlerCalls.get());
        }
    }

    @Test
    void shouldApplyInterceptorsAroundHandlerInOrder() {
        try (ChannelPair pair = openChannels()) {
            final List<String> events = new ArrayList<>();
            final RpcServer server = RpcServer.builder(pair.serverChannel())
                    .interceptor((context, request, invocation) -> {
                        events.add("first-before");
                        final Object response = invocation.proceed(context, request);
                        events.add("first-after");
                        return response;
                    })
                    .interceptor((context, request, invocation) -> {
                        events.add("second-before:" + context.method().name());
                        final Object response = invocation.proceed(context, request);
                        events.add("second-after");
                        return response;
                    })
                    .build();
            final RpcServerMethod<ServerEchoRequest, ServerEchoResponse> method = RpcServerMethod.of(
                    "echo.intercepted",
                    ServerEchoRequest.class,
                    ServerEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            server.register(method, request -> {
                events.add("handler");
                return new ServerEchoResponse(request.requestId(), request.message().toUpperCase(Locale.ROOT), request.amount());
            });

            final ServerEchoResponse response = pair.clientChannel().send(
                    new ServerEchoRequest(UUID.randomUUID(), "hello", 1),
                    TIMEOUT_NS,
                    ServerEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );

            assertEquals("HELLO", response.message());
            assertEquals(
                    List.of("first-before", "second-before:echo.intercepted", "handler", "second-after", "first-after"),
                    events
            );
        }
    }

    @Test
    void shouldRejectDuplicateRequestMessageTypeRegistration() {
        try (ChannelPair pair = openChannels()) {
            final RpcServer server = RpcServer.builder(pair.serverChannel()).build();
            final RpcServerMethod<ServerEchoRequest, ServerEchoResponse> method = RpcServerMethod.of(
                    "echo.duplicate",
                    ServerEchoRequest.class,
                    ServerEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            server.register(method, request -> new ServerEchoResponse(request.requestId(), request.message(), request.amount()));

            final IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> server.register(method, request -> new ServerEchoResponse(
                            request.requestId(),
                            request.message(),
                            request.amount()
                    ))
            );
            assertEquals("requestMessageTypeId already registered: " + REQUEST_MESSAGE_TYPE_ID, error.getMessage());
        }
    }

    @Test
    void shouldReleaseRequestMessageTypeReservationWhenRegistrationFails() {
        try (ChannelPair pair = openChannels()) {
            final RpcServer server = RpcServer.builder(pair.serverChannel()).build();
            final RpcServerMethod<Object, ServerEchoResponse> brokenMethod = RpcServerMethod.of(
                    "echo.broken",
                    Object.class,
                    ServerEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            assertThrows(
                    RuntimeException.class,
                    () -> server.register(
                            brokenMethod,
                            request -> new ServerEchoResponse(UUID.randomUUID(), "broken", 0)
                    )
            );

            final RpcServerMethod<ServerEchoRequest, ServerEchoResponse> validMethod = RpcServerMethod.of(
                    "echo.recovered",
                    ServerEchoRequest.class,
                    ServerEchoResponse.class,
                    REQUEST_MESSAGE_TYPE_ID,
                    RESPONSE_MESSAGE_TYPE_ID
            );
            assertDoesNotThrow(
                    () -> server.register(
                            validMethod,
                            request -> new ServerEchoResponse(request.requestId(), request.message(), request.amount())
                    )
            );
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

    private static String readPayloadText(final UnsafeBuffer response) {
        final int payloadLength = RpcEnvelope.payloadLength(0, response);
        final byte[] bytes = new byte[payloadLength];
        response.getBytes(RpcEnvelope.HEADER_LENGTH, bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private record ChannelPair(
            RpcRuntime runtime,
            RpcChannel clientChannel,
            RpcChannel serverChannel
    ) implements AutoCloseable {

        private UnsafeBuffer encodeRequest(final ServerEchoRequest request) {
            return ru.pathcreator.pyc.rpc.core.serialization.RpcCodecSupport.encode(
                    request,
                    RpcEnvelope.HEADER_LENGTH,
                    ServerEchoRequest.class
            );
        }

        @Override
        public void close() {
            this.runtime.close();
        }
    }
}