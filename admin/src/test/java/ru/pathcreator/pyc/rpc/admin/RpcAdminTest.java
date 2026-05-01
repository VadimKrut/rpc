package ru.pathcreator.pyc.rpc.admin;

import org.junit.jupiter.api.Test;
import ru.pathcreator.pyc.rpc.admin.fixture.AdminEchoRequest;
import ru.pathcreator.pyc.rpc.admin.fixture.AdminEchoResponse;
import ru.pathcreator.pyc.rpc.admin.fixture.AdminEchoService;
import ru.pathcreator.pyc.rpc.admin.fixture.AdminEchoServiceImpl;
import ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminChannelSnapshot;
import ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminClientSnapshot;
import ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminServiceSnapshot;
import ru.pathcreator.pyc.rpc.bootstrap.RpcBootstrap;
import ru.pathcreator.pyc.rpc.bootstrap.RpcBootstrapEnvironment;
import ru.pathcreator.pyc.rpc.client.RpcClient;
import ru.pathcreator.pyc.rpc.client.response.RpcClientResult;
import ru.pathcreator.pyc.rpc.contract.RpcMethodContract;
import ru.pathcreator.pyc.rpc.core.RpcChannel;
import ru.pathcreator.pyc.rpc.core.RpcRuntime;
import ru.pathcreator.pyc.rpc.core.codex.RpcStatusCodes;
import ru.pathcreator.pyc.rpc.core.config.RpcChannelConfig;
import ru.pathcreator.pyc.rpc.core.exception.RpcRemoteException;
import ru.pathcreator.pyc.rpc.server.RpcServer;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class RpcAdminTest {

    private static final long TIMEOUT_NS = 5_000_000_000L;
    private static final String ACCESS_TOKEN = "0123456789abcdef0123456789abcdef";
    private static final int MANUAL_REQUEST_MESSAGE_TYPE_ID = 2101;
    private static final int MANUAL_RESPONSE_MESSAGE_TYPE_ID = 2201;
    private static final AtomicInteger PORTS = new AtomicInteger(31_000);
    private static final AtomicInteger STREAMS = new AtomicInteger(7_000);

    @Test
    void shouldRejectInvalidAccessToken() {
        final RpcAdmin admin = RpcAdmin.builder()
                .accessToken(ACCESS_TOKEN)
                .build();

        assertThrows(RpcAdminAccessDeniedException.class, () -> admin.authenticate("wrong-token"));
    }

    @Test
    void shouldExposeMultiRuntimeSnapshotAndControlPlane() {
        try (Topology topology = openTopology()) {
            final RpcBootstrapEnvironment secureServerEnvironment = RpcBootstrap.environment()
                    .channel("secure", topology.secureServerChannel())
                    .done()
                    .build();
            final RpcBootstrapEnvironment secureClientEnvironment = RpcBootstrap.environment()
                    .channel("secure", topology.secureClientChannel())
                    .done()
                    .build();
            secureServerEnvironment.service(new AdminEchoServiceImpl());
            final AdminEchoService secureClient = secureClientEnvironment.client(AdminEchoService.class);

            final RpcMethodContract<AdminEchoRequest, AdminEchoResponse> manualMethod = RpcMethodContract.of(
                    "manual.echo",
                    AdminEchoRequest.class,
                    AdminEchoResponse.class,
                    MANUAL_REQUEST_MESSAGE_TYPE_ID,
                    MANUAL_RESPONSE_MESSAGE_TYPE_ID
            );
            final RpcServer manualServer = RpcServer.builder(topology.manualServerChannel()).build();
            manualServer.register(manualMethod, request -> new AdminEchoResponse(
                    request.requestId(),
                    request.message() + "-manual",
                    request.amount() + 10
            ));
            final RpcClient manualClient = RpcClient.builder(topology.manualClientChannel())
                    .defaultTimeoutNs(TIMEOUT_NS)
                    .build();

            final RpcAdmin admin = RpcAdmin.builder()
                    .accessToken(ACCESS_TOKEN)
                    .registerRuntime("driver-one", topology.runtimeOne())
                    .registerRuntime("driver-two", topology.runtimeTwo())
                    .registerBootstrapEnvironment("secure-server-env", secureServerEnvironment)
                    .registerBootstrapEnvironment("secure-client-env", secureClientEnvironment)
                    .registerClient("manual-client", manualClient)
                    .registerServer("manual-server", manualServer)
                    .build();

            final RpcAdminSession session = admin.authenticate(ACCESS_TOKEN);
            session.snapshot();

            awaitConnectionSetup();

            final AdminEchoRequest request = new AdminEchoRequest(UUID.randomUUID(), "hello", 2);
            final AdminEchoResponse secureResponse = secureClient.echo(request);
            final AdminEchoResponse manualResponse = manualClient.send(manualMethod, request);
            assertEquals("HELLO", secureResponse.message());
            assertEquals("hello-manual", manualResponse.message());

            final var initialSnapshot = session.snapshot();

            assertEquals(2, initialSnapshot.summary().runtimeCount());
            assertEquals(4, initialSnapshot.channels().size());
            assertEquals(4, initialSnapshot.summary().activeChannelCount());
            assertEquals(2, initialSnapshot.clients().size());
            assertEquals(2, initialSnapshot.servers().size());
            assertEquals(2, initialSnapshot.services().size());
            assertEquals(2, initialSnapshot.runtimes().size());
            assertEquals(4, initialSnapshot.runtimes().stream().mapToInt(runtime -> runtime.channelCount()).sum());

            final String secureChannelId = "channel:" + topology.secureClientChannel().channelId();
            final String manualClientId = findClientId(initialSnapshot, "manual-client");
            final String manualServerId = findServerId(initialSnapshot, "manual-server");
            final String secureServiceId = findServiceId(initialSnapshot, "admin.echo");

            final RpcAdminServiceSnapshot secureService = findService(initialSnapshot, "admin.echo");
            assertTrue(secureService.enabled());
            assertEquals(1, secureService.methods().size());
            assertEquals(1L, secureService.methods().getFirst().successes());

            final RpcAdminClientSnapshot manualClientSnapshot = findClient(initialSnapshot, "manual-client");
            assertEquals(1L, manualClientSnapshot.totalCalls());
            assertEquals(1L, manualClientSnapshot.successes());

            final RpcAdminChannelSnapshot secureChannel = findChannel(initialSnapshot, secureChannelId);
            assertEquals(topology.secureClientChannel().config().streamId(), secureChannel.streamId());
            assertFalse(secureChannel.paused());
            assertTrue(secureChannel.requestsSent() > 0L);

            session.pauseChannel(secureChannelId);
            assertTrue(topology.secureClientChannel().isPaused());
            final IllegalStateException pausedError = assertThrows(
                    IllegalStateException.class,
                    () -> secureClient.echo(new AdminEchoRequest(UUID.randomUUID(), "paused", 1))
            );
            assertEquals("channel paused", pausedError.getMessage());

            session.resumeChannel(secureChannelId);
            assertFalse(topology.secureClientChannel().isPaused());
            assertEquals("RESUMED", secureClient.echo(new AdminEchoRequest(UUID.randomUUID(), "resumed", 1)).message());

            session.disableClient(manualClientId);
            final IllegalStateException disabledClientError = assertThrows(
                    IllegalStateException.class,
                    () -> manualClient.send(manualMethod, new AdminEchoRequest(UUID.randomUUID(), "disabled", 1))
            );
            assertEquals("client disabled", disabledClientError.getMessage());
            session.enableClient(manualClientId);
            assertEquals(
                    "enabled-manual",
                    manualClient.send(manualMethod, new AdminEchoRequest(UUID.randomUUID(), "enabled", 1)).message()
            );

            session.disableService(secureServiceId);
            final RpcRemoteException disabledServiceError = assertThrows(
                    RpcRemoteException.class,
                    () -> secureClient.echo(new AdminEchoRequest(UUID.randomUUID(), "service-off", 1))
            );
            assertEquals(RpcStatusCodes.SERVICE_UNAVAILABLE, disabledServiceError.statusCode());
            session.enableService(secureServiceId);
            assertEquals("SERVICE-ON", secureClient.echo(new AdminEchoRequest(UUID.randomUUID(), "service-on", 1)).message());

            session.disableMethod(manualServerId, MANUAL_REQUEST_MESSAGE_TYPE_ID);
            final RpcClientResult<AdminEchoResponse> disabledMethodResult = manualClient.exchange(
                    manualMethod,
                    new AdminEchoRequest(UUID.randomUUID(), "method-off", 1)
            );
            assertEquals(RpcStatusCodes.SERVICE_UNAVAILABLE, disabledMethodResult.statusCode());
            session.enableMethod(manualServerId, MANUAL_REQUEST_MESSAGE_TYPE_ID);
            assertTrue(manualClient.exchange(manualMethod, new AdminEchoRequest(UUID.randomUUID(), "method-on", 1)).isSuccess());

            session.disableServer(manualServerId);
            final RpcClientResult<AdminEchoResponse> disabledServerResult = manualClient.exchange(
                    manualMethod,
                    new AdminEchoRequest(UUID.randomUUID(), "server-off", 1)
            );
            assertEquals(RpcStatusCodes.SERVICE_UNAVAILABLE, disabledServerResult.statusCode());
            session.enableServer(manualServerId);
            assertTrue(manualClient.exchange(manualMethod, new AdminEchoRequest(UUID.randomUUID(), "server-on", 1)).isSuccess());

            final var finalSnapshot = session.snapshot();
            assertEquals(4, finalSnapshot.channels().size());
            assertFalse(findChannel(finalSnapshot, secureChannelId).paused());
        }
    }

    private static String findClientId(
            final ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminSnapshot snapshot,
            final String name
    ) {
        return findClient(snapshot, name).id();
    }

    private static String findServerId(
            final ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminSnapshot snapshot,
            final String name
    ) {
        return snapshot.servers().stream()
                .filter(server -> name.equals(server.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("server not found: " + name))
                .id();
    }

    private static String findServiceId(
            final ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminSnapshot snapshot,
            final String name
    ) {
        return findService(snapshot, name).id();
    }

    private static RpcAdminClientSnapshot findClient(
            final ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminSnapshot snapshot,
            final String name
    ) {
        return snapshot.clients().stream()
                .filter(client -> name.equals(client.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("client not found: " + name));
    }

    private static RpcAdminServiceSnapshot findService(
            final ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminSnapshot snapshot,
            final String name
    ) {
        return snapshot.services().stream()
                .filter(service -> name.equals(service.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("service not found: " + name));
    }

    private static RpcAdminChannelSnapshot findChannel(
            final ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminSnapshot snapshot,
            final String id
    ) {
        return snapshot.channels().stream()
                .filter(channel -> id.equals(channel.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("channel not found: " + id));
    }

    private static Topology openTopology() {
        final int secureBasePort = PORTS.getAndAdd(2);
        final int secureStreamId = STREAMS.getAndIncrement();
        final RpcRuntime runtimeOne = RpcRuntime.launchEmbedded();
        final RpcChannel secureClientChannel = runtimeOne.createChannel(
                RpcChannelConfig.createDefault(
                        "aeron:udp?endpoint=localhost:" + secureBasePort,
                        "aeron:udp?endpoint=localhost:" + (secureBasePort + 1),
                        secureStreamId
                )
        );
        final RpcChannel secureServerChannel = runtimeOne.createChannel(
                RpcChannelConfig.createDefault(
                        "aeron:udp?endpoint=localhost:" + (secureBasePort + 1),
                        "aeron:udp?endpoint=localhost:" + secureBasePort,
                        secureStreamId
                )
        );

        final int manualBasePort = PORTS.getAndAdd(2);
        final int manualStreamId = STREAMS.getAndIncrement();
        final RpcRuntime runtimeTwo = RpcRuntime.launchEmbedded();
        final RpcChannel manualClientChannel = runtimeTwo.createChannel(
                RpcChannelConfig.createDefault(
                        "aeron:udp?endpoint=localhost:" + manualBasePort,
                        "aeron:udp?endpoint=localhost:" + (manualBasePort + 1),
                        manualStreamId
                )
        );
        final RpcChannel manualServerChannel = runtimeTwo.createChannel(
                RpcChannelConfig.createDefault(
                        "aeron:udp?endpoint=localhost:" + (manualBasePort + 1),
                        "aeron:udp?endpoint=localhost:" + manualBasePort,
                        manualStreamId
                )
        );
        return new Topology(
                runtimeOne,
                secureClientChannel,
                secureServerChannel,
                runtimeTwo,
                manualClientChannel,
                manualServerChannel
        );
    }

    private static void awaitConnectionSetup() {
        try {
            Thread.sleep(200L);
        } catch (final InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("connection setup interrupted", error);
        }
    }

    private record Topology(
            RpcRuntime runtimeOne,
            RpcChannel secureClientChannel,
            RpcChannel secureServerChannel,
            RpcRuntime runtimeTwo,
            RpcChannel manualClientChannel,
            RpcChannel manualServerChannel
    ) implements AutoCloseable {

        @Override
        public void close() {
            this.runtimeTwo.close();
            this.runtimeOne.close();
        }
    }
}