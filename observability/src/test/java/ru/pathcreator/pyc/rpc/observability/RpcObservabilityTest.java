package ru.pathcreator.pyc.rpc.observability;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import ru.pathcreator.pyc.rpc.client.RpcClient;
import ru.pathcreator.pyc.rpc.contract.RpcMethodContract;
import ru.pathcreator.pyc.rpc.core.RpcChannel;
import ru.pathcreator.pyc.rpc.core.RpcRuntime;
import ru.pathcreator.pyc.rpc.core.codex.RpcEnvelope;
import ru.pathcreator.pyc.rpc.core.codex.RpcResponseFrame;
import ru.pathcreator.pyc.rpc.core.codex.RpcStatusCodes;
import ru.pathcreator.pyc.rpc.core.config.RpcChannelConfig;
import ru.pathcreator.pyc.rpc.core.exception.RpcCallTimeoutException;
import ru.pathcreator.pyc.rpc.core.serialization.RpcCodecSupport;
import ru.pathcreator.pyc.rpc.observability.client.RpcClientMethodMetricsSnapshot;
import ru.pathcreator.pyc.rpc.observability.client.RpcClientMetrics;
import ru.pathcreator.pyc.rpc.observability.fixture.MetricsEchoRequest;
import ru.pathcreator.pyc.rpc.observability.fixture.MetricsEchoResponse;
import ru.pathcreator.pyc.rpc.observability.server.RpcServerMethodMetricsSnapshot;
import ru.pathcreator.pyc.rpc.observability.server.RpcServerMetrics;
import ru.pathcreator.pyc.rpc.server.RpcServer;
import ru.pathcreator.pyc.rpc.server.error.RpcStatusException;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RpcObservabilityTest {

    private static final long TIMEOUT_NS = 5_000_000_000L;
    private static final int BASE_REQUEST_MESSAGE_TYPE_ID = 901;
    private static final int BASE_RESPONSE_MESSAGE_TYPE_ID = 951;
    private static final AtomicInteger PORTS = new AtomicInteger(31_000);
    private static final AtomicInteger STREAMS = new AtomicInteger(6_000);

    @Test
    void shouldCollectClientMetricsForSuccessRemoteErrorAndTimeout() {
        try (ChannelPair pair = openChannels()) {
            final RpcMethodContract<MetricsEchoRequest, MetricsEchoResponse> echoMethod = RpcMethodContract.of(
                    "metrics.echo",
                    MetricsEchoRequest.class,
                    MetricsEchoResponse.class,
                    BASE_REQUEST_MESSAGE_TYPE_ID,
                    BASE_RESPONSE_MESSAGE_TYPE_ID
            );
            final RpcMethodContract<MetricsEchoRequest, MetricsEchoResponse> timeoutMethod = RpcMethodContract.of(
                    "metrics.timeout",
                    MetricsEchoRequest.class,
                    MetricsEchoResponse.class,
                    BASE_REQUEST_MESSAGE_TYPE_ID + 1,
                    BASE_RESPONSE_MESSAGE_TYPE_ID + 1
            );

            final RpcServer server = RpcServer.builder(pair.serverChannel()).build();
            server.register(echoMethod, request -> {
                if ("bad".equals(request.message())) {
                    throw new RpcStatusException(RpcStatusCodes.BAD_REQUEST, "bad request");
                }
                return new MetricsEchoResponse(request.requestId(), request.message().toUpperCase(), request.amount() + 1);
            });
            pair.serverChannel().registerRequestHandler(timeoutMethod.requestMessageTypeId(), (offset, length, correlationId, buffer) -> {
            });
            awaitConnectionSetup();

            final RpcClientMetrics metrics = new RpcClientMetrics();
            final RpcClient client = metrics.attach(
                    RpcClient.builder(pair.clientChannel()).defaultTimeoutNs(TIMEOUT_NS)
            ).build();

            client.send(echoMethod, new MetricsEchoRequest(UUID.randomUUID(), "ok", 1));
            client.exchange(echoMethod, new MetricsEchoRequest(UUID.randomUUID(), "bad", 1));
            assertThrows(
                    RpcCallTimeoutException.class,
                    () -> client.send(timeoutMethod, new MetricsEchoRequest(UUID.randomUUID(), "slow", 1), 20_000_000L)
            );

            final RpcClientMethodMetricsSnapshot echo = metrics.snapshot().methods().stream()
                    .filter(item -> item.methodName().equals("metrics.echo"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(2L, echo.calls());
            assertEquals(1L, echo.successes());
            assertEquals(1L, echo.remoteErrors());
            assertEquals(0L, echo.timeouts());
            assertEquals(0L, echo.localFailures());
            assertEquals(1L, echo.remoteClientErrors());

            final RpcClientMethodMetricsSnapshot timeout = metrics.snapshot().methods().stream()
                    .filter(item -> item.methodName().equals("metrics.timeout"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(1L, timeout.calls());
            assertEquals(0L, timeout.successes());
            assertEquals(0L, timeout.remoteErrors());
            assertEquals(1L, timeout.timeouts());
        }
    }

    @Test
    void shouldCollectServerMetricsForSuccessAndClientError() {
        try (ChannelPair pair = openChannels()) {
            final RpcMethodContract<MetricsEchoRequest, MetricsEchoResponse> method = RpcMethodContract.of(
                    "metrics.server",
                    MetricsEchoRequest.class,
                    MetricsEchoResponse.class,
                    BASE_REQUEST_MESSAGE_TYPE_ID,
                    BASE_RESPONSE_MESSAGE_TYPE_ID
            );

            final RpcServerMetrics metrics = new RpcServerMetrics();
            final RpcServer server = metrics.attach(
                    RpcServer.builder(pair.serverChannel())
            ).build();
            server.register(method, request -> {
                if (request.amount() < 0) {
                    throw new RpcStatusException(RpcStatusCodes.BAD_REQUEST, "negative amount");
                }
                return new MetricsEchoResponse(request.requestId(), request.message().toUpperCase(), request.amount());
            });
            awaitConnectionSetup();

            final MetricsEchoResponse success = pair.exchange(method, new MetricsEchoRequest(UUID.randomUUID(), "ok", 2));
            assertEquals("OK", success.message());

            final RpcResponseFrame error = pair.exchangeFrame(method, new MetricsEchoRequest(UUID.randomUUID(), "bad", -1));
            assertEquals(RpcStatusCodes.BAD_REQUEST, error.statusCode());

            final RpcServerMethodMetricsSnapshot snapshot = metrics.snapshot().methods().stream()
                    .filter(item -> item.methodName().equals("metrics.server"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(2L, snapshot.requests());
            assertEquals(1L, snapshot.successes());
            assertEquals(1L, snapshot.failures());
            assertEquals(1L, snapshot.clientErrors());
            assertEquals(0L, snapshot.serverErrors());
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

    private record ChannelPair(
            RpcRuntime runtime,
            RpcChannel clientChannel,
            RpcChannel serverChannel
    ) implements AutoCloseable {

        private RpcResponseFrame exchangeFrame(
                final RpcMethodContract<MetricsEchoRequest, MetricsEchoResponse> method,
                final MetricsEchoRequest request
        ) {
            final UnsafeBuffer requestBuffer = this.encodeRequest(request);
            return this.clientChannel.sendFrame(
                    TIMEOUT_NS,
                    requestBuffer.capacity() - RpcEnvelope.HEADER_LENGTH,
                    method.requestMessageTypeId(),
                    requestBuffer
            );
        }

        private MetricsEchoResponse exchange(
                final RpcMethodContract<MetricsEchoRequest, MetricsEchoResponse> method,
                final MetricsEchoRequest request
        ) {
            final RpcResponseFrame frame = this.exchangeFrame(method, request);
            assertEquals(RpcStatusCodes.OK, frame.statusCode());
            return RpcCodecSupport.decode(
                    frame.payloadOffset(),
                    frame.payloadLength(),
                    MetricsEchoResponse.class,
                    frame.buffer()
            );
        }

        private UnsafeBuffer encodeRequest(
                final MetricsEchoRequest request
        ) {
            return RpcCodecSupport.encode(request, RpcEnvelope.HEADER_LENGTH, MetricsEchoRequest.class);
        }

        @Override
        public void close() {
            this.runtime.close();
        }
    }
}