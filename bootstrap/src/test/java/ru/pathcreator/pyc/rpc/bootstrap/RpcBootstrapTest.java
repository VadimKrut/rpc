package ru.pathcreator.pyc.rpc.bootstrap;

import org.junit.jupiter.api.Test;
import ru.pathcreator.pyc.rpc.bootstrap.fixture.BootstrapEchoRequest;
import ru.pathcreator.pyc.rpc.bootstrap.fixture.BootstrapEchoResponse;
import ru.pathcreator.pyc.rpc.bootstrap.fixture.BootstrapEchoService;
import ru.pathcreator.pyc.rpc.bootstrap.fixture.ConflictingSecureService;
import ru.pathcreator.pyc.rpc.bootstrap.fixture.DuplicateAcrossProfileService;
import ru.pathcreator.pyc.rpc.bootstrap.fixture.InvalidBootstrapService;
import ru.pathcreator.pyc.rpc.bootstrap.fixture.PlainEchoService;
import ru.pathcreator.pyc.rpc.bootstrap.fixture.SecondaryService;
import ru.pathcreator.pyc.rpc.bootstrap.introspection.RpcBootstrapIntrospector;
import ru.pathcreator.pyc.rpc.bootstrap.model.RpcAnnotatedService;
import ru.pathcreator.pyc.rpc.bootstrap.prometheus.RpcPrometheusExporter;
import ru.pathcreator.pyc.rpc.core.RpcChannel;
import ru.pathcreator.pyc.rpc.core.RpcRuntime;
import ru.pathcreator.pyc.rpc.core.config.RpcChannelConfig;
import ru.pathcreator.pyc.rpc.encryption.RpcPayloadEncryption;
import ru.pathcreator.pyc.rpc.encryption.RpcPayloadEncryptions;
import ru.pathcreator.pyc.rpc.observability.client.RpcClientMetrics;
import ru.pathcreator.pyc.rpc.observability.server.RpcServerMetrics;
import ru.pathcreator.pyc.rpc.server.RpcServer;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RpcBootstrapTest {

    private static final AtomicInteger PORTS = new AtomicInteger(33_000);
    private static final AtomicInteger STREAMS = new AtomicInteger(7_000);

    @Test
    void shouldIntrospectAnnotatedService() {
        final RpcAnnotatedService<BootstrapEchoService> service = RpcBootstrapIntrospector.introspect(BootstrapEchoService.class);
        assertEquals("bootstrap.echo", service.contract().name());
        assertEquals("secure", service.channelName());
        assertEquals(1, service.methods().size());
        assertEquals("bootstrap.echo.echo", service.methods().getFirst().contract().name());
        assertEquals(123_000_000L, service.methods().getFirst().timeoutNs());
    }

    @Test
    void shouldRejectInvalidAnnotatedService() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RpcBootstrapIntrospector.introspect(InvalidBootstrapService.class)
        );
    }

    @Test
    void shouldServeClientAndServerFromAnnotatedService() {
        try (ChannelPair pair = openChannels()) {
            final RpcServerMetrics serverMetrics = new RpcServerMetrics();
            final RpcServer server = RpcBootstrap.server(pair.serverChannel())
                    .metrics(serverMetrics)
                    .service((BootstrapEchoService) request -> new BootstrapEchoResponse(
                            request.requestId(),
                            request.message().toUpperCase(Locale.ROOT),
                            request.amount() + 1
                    ))
                    .build();
            awaitConnectionSetup();

            final RpcClientMetrics clientMetrics = new RpcClientMetrics();
            final AtomicLong seenTimeout = new AtomicLong();
            final BootstrapEchoService client = RpcBootstrap.client(pair.clientChannel())
                    .metrics(clientMetrics)
                    .interceptor((context, request, invocation) -> {
                        seenTimeout.set(context.timeoutNs());
                        return invocation.proceed(context, request);
                    })
                    .buildService(BootstrapEchoService.class);

            final BootstrapEchoResponse response = client.echo(
                    new BootstrapEchoRequest(UUID.randomUUID(), "hello", 9)
            );
            assertEquals("HELLO", response.message());
            assertEquals(10, response.amount());
            assertEquals(123_000_000L, seenTimeout.get());
            assertTrue(RpcPrometheusExporter.clientMetrics(clientMetrics.snapshot()).contains("rpc_client_calls_total"));
            assertTrue(RpcPrometheusExporter.serverMetrics(serverMetrics.snapshot()).contains("rpc_server_requests_total"));
        }
    }

    @Test
    void shouldRequireExplicitTypeWhenImplementationExposesMultipleServices() {
        try (ChannelPair pair = openChannels()) {
            final MultiServiceImplementation implementation = new MultiServiceImplementation();
            assertThrows(
                    IllegalArgumentException.class,
                    () -> RpcBootstrap.server(pair.serverChannel()).service(implementation)
            );
        }
    }

    @Test
    void shouldRouteServicesByAnnotatedChannelProfiles() {
        final byte[] secureKey = new byte[32];
        Arrays.fill(secureKey, (byte) 7);
        final RpcPayloadEncryption clientSecureEncryption = RpcPayloadEncryptions.chaCha20Poly1305()
                .key(secureKey, false)
                .noncePrefix(0x01020304)
                .initialCounter(1L)
                .build();
        final RpcPayloadEncryption serverSecureEncryption = RpcPayloadEncryptions.chaCha20Poly1305()
                .key(secureKey, false)
                .noncePrefix(0x01020305)
                .initialCounter(1L)
                .build();
        try (ChannelPair securePair = openChannels(); ChannelPair plainPair = openChannels()) {
            final RpcBootstrapEnvironment serverEnvironment = RpcBootstrap.environment()
                    .channel("secure", securePair.serverChannel())
                    .serverPayloadEncryption(serverSecureEncryption)
                    .serverMetrics(new RpcServerMetrics())
                    .done()
                    .channel("plain", plainPair.serverChannel())
                    .serverMetrics(new RpcServerMetrics())
                    .done()
                    .build();
            serverEnvironment
                    .service((BootstrapEchoService) request -> new BootstrapEchoResponse(
                            request.requestId(),
                            "SECURE:" + request.message(),
                            request.amount() + 10
                    ))
                    .service((PlainEchoService) request -> new BootstrapEchoResponse(
                            request.requestId(),
                            "PLAIN:" + request.message(),
                            request.amount() + 20
                    ));
            awaitConnectionSetup();

            final RpcBootstrapEnvironment clientEnvironment = RpcBootstrap.environment()
                    .channel("secure", securePair.clientChannel())
                    .clientPayloadEncryption(clientSecureEncryption)
                    .clientMetrics(new RpcClientMetrics())
                    .done()
                    .channel("plain", plainPair.clientChannel())
                    .clientMetrics(new RpcClientMetrics())
                    .done()
                    .build();
            final BootstrapEchoService secureClient = clientEnvironment.client(BootstrapEchoService.class);
            final PlainEchoService plainClient = clientEnvironment.client(PlainEchoService.class);

            final BootstrapEchoResponse secureResponse = secureClient.echo(
                    new BootstrapEchoRequest(UUID.randomUUID(), "hello", 1)
            );
            final BootstrapEchoResponse plainResponse = plainClient.echo(
                    new BootstrapEchoRequest(UUID.randomUUID(), "world", 2)
            );
            assertEquals("SECURE:hello", secureResponse.message());
            assertEquals(11, secureResponse.amount());
            assertEquals("PLAIN:world", plainResponse.message());
            assertEquals(22, plainResponse.amount());
        }
    }

    @Test
    void shouldRejectDuplicateMethodIdsInsideSameProfile() {
        try (ChannelPair pair = openChannels()) {
            final RpcBootstrapEnvironment environment = RpcBootstrap.environment()
                    .channel("secure", pair.serverChannel())
                    .done()
                    .build();
            environment.service((BootstrapEchoService) request -> new BootstrapEchoResponse(
                    request.requestId(),
                    request.message(),
                    request.amount()
            ));
            assertThrows(
                    IllegalStateException.class,
                    () -> environment.service((ConflictingSecureService) request -> new BootstrapEchoResponse(
                            request.requestId(),
                            request.message(),
                            request.amount()
                    ))
            );
        }
    }

    @Test
    void shouldAllowSameMethodIdsAcrossDifferentProfiles() {
        try (ChannelPair securePair = openChannels(); ChannelPair plainPair = openChannels()) {
            final RpcBootstrapEnvironment environment = RpcBootstrap.environment()
                    .channel("secure", securePair.serverChannel())
                    .done()
                    .channel("plain", plainPair.serverChannel())
                    .done()
                    .build();
            environment
                    .service((BootstrapEchoService) request -> new BootstrapEchoResponse(
                            request.requestId(),
                            request.message(),
                            request.amount()
                    ))
                    .service((DuplicateAcrossProfileService) request -> new BootstrapEchoResponse(
                            request.requestId(),
                            request.message(),
                            request.amount()
                    ));
            assertTrue(environment.hasProfile("secure"));
            assertTrue(environment.hasProfile("plain"));
        }
    }

    @Test
    void shouldValidateServicesBeforeRegistration() {
        try (ChannelPair securePair = openChannels(); ChannelPair plainPair = openChannels()) {
            final RpcBootstrapEnvironment environment = RpcBootstrap.environment()
                    .channel("secure", securePair.serverChannel())
                    .done()
                    .channel("plain", plainPair.serverChannel())
                    .done()
                    .build();
            environment.validate(BootstrapEchoService.class, DuplicateAcrossProfileService.class);
            assertThrows(
                    IllegalStateException.class,
                    () -> environment.validate(BootstrapEchoService.class, ConflictingSecureService.class)
            );
        }
    }

    @Test
    void shouldRejectDuplicateServiceRegistrationInSameProfile() {
        try (ChannelPair pair = openChannels()) {
            final RpcBootstrapEnvironment environment = RpcBootstrap.environment()
                    .channel("secure", pair.serverChannel())
                    .done()
                    .build();
            environment.service((BootstrapEchoService) request -> new BootstrapEchoResponse(
                    request.requestId(),
                    request.message(),
                    request.amount()
            ));
            assertThrows(
                    IllegalStateException.class,
                    () -> environment.service((BootstrapEchoService) request -> new BootstrapEchoResponse(
                            request.requestId(),
                            request.message(),
                            request.amount()
                    ))
            );
        }
    }

    @Test
    void shouldRegisterMultipleImplementationsInBulk() {
        try (ChannelPair securePair = openChannels(); ChannelPair plainPair = openChannels()) {
            final RpcBootstrapEnvironment serverEnvironment = RpcBootstrap.environment()
                    .channel("secure", securePair.serverChannel())
                    .done()
                    .channel("plain", plainPair.serverChannel())
                    .done()
                    .build();
            serverEnvironment.services(
                    (BootstrapEchoService) request -> new BootstrapEchoResponse(
                            request.requestId(),
                            "A:" + request.message(),
                            request.amount()
                    ),
                    (PlainEchoService) request -> new BootstrapEchoResponse(
                            request.requestId(),
                            "B:" + request.message(),
                            request.amount()
                    )
            );
            awaitConnectionSetup();

            final RpcBootstrapEnvironment clientEnvironment = RpcBootstrap.environment()
                    .channel("secure", securePair.clientChannel())
                    .done()
                    .channel("plain", plainPair.clientChannel())
                    .done()
                    .build();
            assertEquals(
                    "A:x",
                    clientEnvironment.client(BootstrapEchoService.class)
                            .echo(new BootstrapEchoRequest(UUID.randomUUID(), "x", 1))
                            .message()
            );
            assertEquals(
                    "B:y",
                    clientEnvironment.client(PlainEchoService.class)
                            .echo(new BootstrapEchoRequest(UUID.randomUUID(), "y", 1))
                            .message()
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

        @Override
        public void close() {
            this.runtime.close();
        }
    }

    private static final class MultiServiceImplementation implements BootstrapEchoService, SecondaryService {

        @Override
        public BootstrapEchoResponse echo(
                final BootstrapEchoRequest request
        ) {
            return new BootstrapEchoResponse(request.requestId(), request.message(), request.amount());
        }

        @Override
        public BootstrapEchoResponse mirror(
                final BootstrapEchoRequest request
        ) {
            return new BootstrapEchoResponse(request.requestId(), request.message(), request.amount());
        }
    }
}
