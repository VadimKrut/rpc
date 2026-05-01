package ru.pathcreator.pyc.rpc.admin;

import ru.pathcreator.pyc.rpc.admin.snapshot.*;
import ru.pathcreator.pyc.rpc.bootstrap.RpcBootstrapEnvironment;
import ru.pathcreator.pyc.rpc.client.RpcClient;
import ru.pathcreator.pyc.rpc.client.listener.RpcClientListener;
import ru.pathcreator.pyc.rpc.contract.RpcMethodContract;
import ru.pathcreator.pyc.rpc.core.RpcChannel;
import ru.pathcreator.pyc.rpc.core.RpcRuntime;
import ru.pathcreator.pyc.rpc.core.listener.RpcChannelListener;
import ru.pathcreator.pyc.rpc.server.RpcServer;
import ru.pathcreator.pyc.rpc.server.listener.RpcServerListener;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class RpcAdmin {

    private final Object lock = new Object();
    private final byte[] accessTokenBytes;
    private final Map<String, RpcRuntime> runtimes;
    private final Map<String, RpcChannel> channels;
    private final Map<String, RpcClient> clients;
    private final Map<String, RpcServer> servers;
    private final Map<String, RpcBootstrapEnvironment> environments;
    private final IdentityHashMap<RpcChannel, ChannelTelemetry> channelTelemetry = new IdentityHashMap<>();
    private final IdentityHashMap<RpcClient, ClientTelemetry> clientTelemetry = new IdentityHashMap<>();
    private final IdentityHashMap<RpcServer, ServerTelemetry> serverTelemetry = new IdentityHashMap<>();

    RpcAdmin(
            final byte[] accessTokenBytes,
            final Map<String, RpcRuntime> runtimes,
            final Map<String, RpcChannel> channels,
            final Map<String, RpcClient> clients,
            final Map<String, RpcServer> servers,
            final Map<String, RpcBootstrapEnvironment> environments
    ) {
        this.accessTokenBytes = accessTokenBytes;
        this.runtimes = runtimes;
        this.channels = channels;
        this.clients = clients;
        this.servers = servers;
        this.environments = environments;
    }

    public static RpcAdminBuilder builder() {
        return new RpcAdminBuilder();
    }

    public RpcAdminSession authenticate(
            final String accessToken
    ) {
        if (!MessageDigest.isEqual(this.accessTokenBytes, accessToken.getBytes(StandardCharsets.UTF_8))) {
            throw new RpcAdminAccessDeniedException();
        }
        return new RpcAdminSession(this);
    }

    RpcAdminSnapshot snapshotInternal() {
        synchronized (this.lock) {
            final Discovery discovery = this.discover();
            final long now = System.currentTimeMillis();
            final List<RpcAdminRuntimeSnapshot> runtimeSnapshots = buildRuntimeSnapshots(discovery);
            final List<RpcAdminChannelSnapshot> channelSnapshots = buildChannelSnapshots(discovery);
            final List<RpcAdminClientSnapshot> clientSnapshots = buildClientSnapshots(discovery);
            final List<RpcAdminServerSnapshot> serverSnapshots = buildServerSnapshots(discovery);
            final List<RpcAdminServiceSnapshot> serviceSnapshots = buildServiceSnapshots(discovery);
            return new RpcAdminSnapshot(
                    now,
                    buildSummary(channelSnapshots, clientSnapshots, serverSnapshots, serviceSnapshots, runtimeSnapshots.size()),
                    runtimeSnapshots,
                    channelSnapshots,
                    clientSnapshots,
                    serverSnapshots,
                    serviceSnapshots
            );
        }
    }

    void pauseChannelInternal(
            final String channelId
    ) {
        synchronized (this.lock) {
            this.requireChannel(this.discover(), channelId).channel.pause();
        }
    }

    void resumeChannelInternal(
            final String channelId
    ) {
        synchronized (this.lock) {
            this.requireChannel(this.discover(), channelId).channel.resume();
        }
    }

    void disableClientInternal(
            final String clientId
    ) {
        synchronized (this.lock) {
            this.requireClient(this.discover(), clientId).client.disable();
        }
    }

    void enableClientInternal(
            final String clientId
    ) {
        synchronized (this.lock) {
            this.requireClient(this.discover(), clientId).client.enable();
        }
    }

    void disableServerInternal(
            final String serverId
    ) {
        synchronized (this.lock) {
            this.requireServer(this.discover(), serverId).server.disable();
        }
    }

    void enableServerInternal(
            final String serverId
    ) {
        synchronized (this.lock) {
            this.requireServer(this.discover(), serverId).server.enable();
        }
    }

    void disableServiceInternal(
            final String serviceId
    ) {
        synchronized (this.lock) {
            final ServiceView service = this.requireService(this.discover(), serviceId);
            if (service.server == null) {
                throw new IllegalStateException("service is not backed by a controllable server: " + serviceId);
            }
            for (final int requestMessageTypeId : service.requestMessageTypeIds) {
                service.server.server.disableMethod(requestMessageTypeId);
            }
        }
    }

    void enableServiceInternal(
            final String serviceId
    ) {
        synchronized (this.lock) {
            final ServiceView service = this.requireService(this.discover(), serviceId);
            if (service.server == null) {
                throw new IllegalStateException("service is not backed by a controllable server: " + serviceId);
            }
            for (final int requestMessageTypeId : service.requestMessageTypeIds) {
                service.server.server.enableMethod(requestMessageTypeId);
            }
        }
    }

    void disableMethodInternal(
            final String serverId,
            final int requestMessageTypeId
    ) {
        synchronized (this.lock) {
            this.requireServer(this.discover(), serverId).server.disableMethod(requestMessageTypeId);
        }
    }

    void enableMethodInternal(
            final String serverId,
            final int requestMessageTypeId
    ) {
        synchronized (this.lock) {
            this.requireServer(this.discover(), serverId).server.enableMethod(requestMessageTypeId);
        }
    }

    private Discovery discover() {
        final Map<String, RuntimeView> runtimeViews = new LinkedHashMap<>();
        final Map<String, ChannelView> channelViews = new LinkedHashMap<>();
        final Map<String, ClientView> clientViews = new LinkedHashMap<>();
        final Map<String, ServerView> serverViews = new LinkedHashMap<>();
        final Map<String, ServiceView> serviceViews = new LinkedHashMap<>();
        final IdentityHashMap<RpcChannel, ChannelView> channelsByInstance = new IdentityHashMap<>();
        final IdentityHashMap<RpcClient, ClientView> clientsByInstance = new IdentityHashMap<>();
        final IdentityHashMap<RpcServer, ServerView> serversByInstance = new IdentityHashMap<>();

        for (final Map.Entry<String, RpcRuntime> entry : this.runtimes.entrySet()) {
            final String runtimeName = entry.getKey();
            final RpcRuntime runtime = entry.getValue();
            final RuntimeView runtimeView = new RuntimeView("runtime:" + runtimeName, runtimeName, runtime);
            runtimeViews.put(runtimeView.id, runtimeView);
            for (final RpcChannel channel : runtime.channels()) {
                registerChannelView(
                        channelViews,
                        channelsByInstance,
                        "channel:" + channel.channelId(),
                        runtimeName + "/channel-" + channel.channelId(),
                        runtimeView.id,
                        runtimeName,
                        null,
                        null,
                        channel
                );
                this.attachChannelListener(channel);
            }
        }

        for (final Map.Entry<String, RpcBootstrapEnvironment> entry : this.environments.entrySet()) {
            final String environmentName = entry.getKey();
            final RpcBootstrapEnvironment environment = entry.getValue();
            for (final String profileName : environment.profileNames()) {
                final RpcChannel channel = environment.channel(profileName);
                final ChannelView channelView = registerChannelView(
                        channelViews,
                        channelsByInstance,
                        "channel:" + channel.channelId(),
                        environmentName + "/" + profileName,
                        null,
                        null,
                        environmentName,
                        profileName,
                        channel
                );
                this.attachChannelListener(channel);

                final RpcClient client = environment.clientIfPresent(profileName);
                if (client != null) {
                    registerClientView(
                            clientViews,
                            clientsByInstance,
                            "client:" + environmentName + ":" + profileName,
                            environmentName + "/" + profileName,
                            environmentName,
                            profileName,
                            channelView.id,
                            client
                    );
                    this.attachClientListener(client);
                }

                final RpcServer server = environment.serverIfPresent(profileName);
                if (server != null) {
                    registerServerView(
                            serverViews,
                            serversByInstance,
                            "server:" + environmentName + ":" + profileName,
                            environmentName + "/" + profileName,
                            environmentName,
                            profileName,
                            channelView.id,
                            server
                    );
                    this.attachServerListener(server);
                }
            }
            for (final RpcBootstrapEnvironment.RegisteredServiceSnapshot service : environment.registeredServices()) {
                final ServerView serverView = environment.serverIfPresent(service.profileName()) == null
                        ? null
                        : serversByInstance.get(environment.serverIfPresent(service.profileName()));
                final ChannelView channelView = channelsByInstance.get(environment.channel(service.profileName()));
                final List<Integer> requestIds = new ArrayList<>(service.methods().size());
                for (final RpcBootstrapEnvironment.RegisteredMethodSnapshot method : service.methods()) {
                    requestIds.add(method.requestMessageTypeId());
                }
                final String serviceId = "service:" + environmentName + ":" + service.profileName() + ":" + service.serviceName();
                serviceViews.put(serviceId, new ServiceView(
                        serviceId,
                        service.serviceName(),
                        environmentName,
                        service.profileName(),
                        channelView == null ? null : channelView.id,
                        serverView,
                        requestIds,
                        service
                ));
            }
        }

        for (final Map.Entry<String, RpcChannel> entry : this.channels.entrySet()) {
            registerChannelView(
                    channelViews,
                    channelsByInstance,
                    "channel:" + entry.getValue().channelId(),
                    entry.getKey(),
                    null,
                    null,
                    null,
                    null,
                    entry.getValue()
            );
            this.attachChannelListener(entry.getValue());
        }

        for (final Map.Entry<String, RpcClient> entry : this.clients.entrySet()) {
            final RpcClient client = entry.getValue();
            final ChannelView channelView = channelsByInstance.get(client.channel());
            registerClientView(
                    clientViews,
                    clientsByInstance,
                    "client:" + entry.getKey(),
                    entry.getKey(),
                    null,
                    null,
                    channelView == null ? null : channelView.id,
                    client
            );
            this.attachClientListener(client);
        }

        for (final Map.Entry<String, RpcServer> entry : this.servers.entrySet()) {
            final RpcServer server = entry.getValue();
            final ChannelView channelView = channelsByInstance.get(server.channel());
            registerServerView(
                    serverViews,
                    serversByInstance,
                    "server:" + entry.getKey(),
                    entry.getKey(),
                    null,
                    null,
                    channelView == null ? null : channelView.id,
                    server
            );
            this.attachServerListener(server);
        }

        for (final ServerView server : serverViews.values()) {
            if (serviceViews.values().stream().noneMatch(service -> service.server == server)) {
                final List<Integer> requestIds = new ArrayList<>();
                for (final RpcServer.RegisteredMethodSnapshot method : server.server.registeredMethods()) {
                    requestIds.add(method.contract().requestMessageTypeId());
                }
                final String serviceId = "service:" + server.id;
                serviceViews.putIfAbsent(serviceId, new ServiceView(
                        serviceId,
                        server.name,
                        server.environmentName,
                        server.profileName,
                        server.channelId,
                        server,
                        requestIds,
                        null
                ));
            }
        }

        return new Discovery(runtimeViews, channelViews, clientViews, serverViews, serviceViews);
    }

    private List<RpcAdminRuntimeSnapshot> buildRuntimeSnapshots(
            final Discovery discovery
    ) {
        final List<RpcAdminRuntimeSnapshot> snapshots = new ArrayList<>(discovery.runtimes.size());
        for (final RuntimeView runtime : discovery.runtimes.values()) {
            final List<String> channelIds = new ArrayList<>();
            for (final ChannelView channel : discovery.channels.values()) {
                if (runtime.id.equals(channel.runtimeId)) {
                    channelIds.add(channel.id);
                }
            }
            snapshots.add(
                    new RpcAdminRuntimeSnapshot(
                            runtime.id,
                            runtime.name,
                            runtime.runtime.runtimeId(),
                            runtime.runtime.aeronDirectoryName(),
                            runtime.runtime.isClosed(),
                            channelIds.size(),
                            List.copyOf(channelIds)
                    )
            );
        }
        return List.copyOf(snapshots);
    }

    private List<RpcAdminChannelSnapshot> buildChannelSnapshots(
            final Discovery discovery
    ) {
        final List<RpcAdminChannelSnapshot> snapshots = new ArrayList<>(discovery.channels.size());
        for (final ChannelView channel : discovery.channels.values()) {
            final ChannelTelemetry telemetry = this.channelTelemetry.get(channel.channel);
            snapshots.add(
                    new RpcAdminChannelSnapshot(
                            channel.id,
                            channel.name,
                            channel.runtimeId,
                            channel.runtimeName,
                            channel.environmentName,
                            channel.profileName,
                            channel.channel.config().publicationChannel(),
                            channel.channel.config().subscriptionChannel(),
                            channel.channel.config().streamId(),
                            channel.channel.isPaused(),
                            channel.channel.isClosed(),
                            channel.channel.currentWaiters(),
                            channel.channel.waitersCapacity(),
                            channel.channel.estimatedOwnedMemoryBytes(),
                            telemetry.requestsSent.sum(),
                            telemetry.requestsReceived.sum(),
                            telemetry.responsesSent.sum(),
                            telemetry.responsesReceived.sum(),
                            telemetry.bytesOut.sum(),
                            telemetry.bytesIn.sum(),
                            telemetry.publishTimeouts.sum(),
                            telemetry.callTimeouts.sum(),
                            telemetry.lastActivityAtEpochMs.get()
                    )
            );
        }
        return List.copyOf(snapshots);
    }

    private List<RpcAdminClientSnapshot> buildClientSnapshots(
            final Discovery discovery
    ) {
        final List<RpcAdminClientSnapshot> snapshots = new ArrayList<>(discovery.clients.size());
        for (final ClientView client : discovery.clients.values()) {
            final ClientTelemetry telemetry = this.clientTelemetry.get(client.client);
            snapshots.add(
                    new RpcAdminClientSnapshot(
                            client.id,
                            client.name,
                            client.environmentName,
                            client.profileName,
                            client.channelId,
                            client.client.isEnabled(),
                            client.client.defaultTimeoutNs(),
                            telemetry.totalCalls.sum(),
                            telemetry.successes.sum(),
                            telemetry.remoteErrors.sum(),
                            telemetry.failures.sum(),
                            telemetry.timeouts.sum(),
                            average(telemetry.totalLatencyNs.sum(), telemetry.totalCalls.sum()),
                            telemetry.maxLatencyNs.get(),
                            methodSnapshots(telemetry.methods)
                    )
            );
        }
        return List.copyOf(snapshots);
    }

    private List<RpcAdminServerSnapshot> buildServerSnapshots(
            final Discovery discovery
    ) {
        final List<RpcAdminServerSnapshot> snapshots = new ArrayList<>(discovery.servers.size());
        for (final ServerView server : discovery.servers.values()) {
            final ServerTelemetry telemetry = this.serverTelemetry.get(server.server);
            snapshots.add(
                    new RpcAdminServerSnapshot(
                            server.id,
                            server.name,
                            server.environmentName,
                            server.profileName,
                            server.channelId,
                            server.server.isEnabled(),
                            telemetry.totalRequests.sum(),
                            telemetry.successes.sum(),
                            telemetry.failures.sum(),
                            average(telemetry.totalLatencyNs.sum(), telemetry.totalRequests.sum()),
                            telemetry.maxLatencyNs.get(),
                            server.server.registeredMethods().size(),
                            methodSnapshots(telemetry.methods, server.server.registeredMethods())
                    )
            );
        }
        return List.copyOf(snapshots);
    }

    private List<RpcAdminServiceSnapshot> buildServiceSnapshots(
            final Discovery discovery
    ) {
        final List<RpcAdminServiceSnapshot> snapshots = new ArrayList<>(discovery.services.size());
        for (final ServiceView service : discovery.services.values()) {
            final ServerTelemetry telemetry = service.server == null ? null : this.serverTelemetry.get(service.server.server);
            final List<RpcAdminMethodSnapshot> methods = new ArrayList<>();
            boolean enabled = true;
            if (service.server != null) {
                final Map<Integer, Boolean> enabledByMethod = new LinkedHashMap<>();
                for (final RpcServer.RegisteredMethodSnapshot method : service.server.server.registeredMethods()) {
                    enabledByMethod.put(method.contract().requestMessageTypeId(), method.enabled());
                }
                if (service.metadata != null) {
                    for (final RpcBootstrapEnvironment.RegisteredMethodSnapshot method : service.metadata.methods()) {
                        final MethodStats stats = telemetry == null ? null : telemetry.methods.get(method.requestMessageTypeId());
                        final boolean methodEnabled = enabledByMethod.getOrDefault(method.requestMessageTypeId(), true);
                        enabled &= methodEnabled;
                        methods.add(toSnapshot(method.methodName(), method.requestMessageTypeId(), method.responseMessageTypeId(), methodEnabled, stats));
                    }
                } else {
                    for (final RpcServer.RegisteredMethodSnapshot method : service.server.server.registeredMethods()) {
                        final MethodStats stats = telemetry == null ? null : telemetry.methods.get(method.contract().requestMessageTypeId());
                        enabled &= method.enabled();
                        methods.add(toSnapshot(
                                method.contract().name(),
                                method.contract().requestMessageTypeId(),
                                method.contract().responseMessageTypeId(),
                                method.enabled(),
                                stats
                        ));
                    }
                }
            }
            snapshots.add(
                    new RpcAdminServiceSnapshot(
                            service.id,
                            service.name,
                            service.environmentName,
                            service.profileName,
                            service.channelId,
                            service.server == null ? null : service.server.id,
                            enabled,
                            List.copyOf(methods)
                    )
            );
        }
        return List.copyOf(snapshots);
    }

    private RpcAdminSummarySnapshot buildSummary(
            final List<RpcAdminChannelSnapshot> channels,
            final List<RpcAdminClientSnapshot> clients,
            final List<RpcAdminServerSnapshot> servers,
            final List<RpcAdminServiceSnapshot> services,
            final int runtimeCount
    ) {
        int activeChannels = 0;
        int pausedChannels = 0;
        int closedChannels = 0;
        for (final RpcAdminChannelSnapshot channel : channels) {
            if (channel.closed()) {
                closedChannels++;
            } else if (channel.paused()) {
                pausedChannels++;
            } else {
                activeChannels++;
            }
        }
        int enabledClients = 0;
        for (final RpcAdminClientSnapshot client : clients) {
            if (client.enabled()) {
                enabledClients++;
            }
        }
        int enabledServers = 0;
        for (final RpcAdminServerSnapshot server : servers) {
            if (server.enabled()) {
                enabledServers++;
            }
        }
        int enabledServices = 0;
        for (final RpcAdminServiceSnapshot service : services) {
            if (service.enabled()) {
                enabledServices++;
            }
        }
        return new RpcAdminSummarySnapshot(
                runtimeCount,
                channels.size(),
                activeChannels,
                pausedChannels,
                closedChannels,
                clients.size(),
                enabledClients,
                servers.size(),
                enabledServers,
                services.size(),
                enabledServices
        );
    }

    private static ChannelView registerChannelView(
            final Map<String, ChannelView> channelViews,
            final IdentityHashMap<RpcChannel, ChannelView> channelsByInstance,
            final String suggestedId,
            final String suggestedName,
            final String runtimeId,
            final String runtimeName,
            final String environmentName,
            final String profileName,
            final RpcChannel channel
    ) {
        final ChannelView existing = channelsByInstance.get(channel);
        final ChannelView next;
        if (existing == null) {
            next = new ChannelView(
                    suggestedId,
                    suggestedName,
                    runtimeId,
                    runtimeName,
                    environmentName,
                    profileName,
                    channel
            );
        } else {
            next = new ChannelView(
                    existing.id,
                    preferredName(existing.name, suggestedName),
                    firstNonNull(existing.runtimeId, runtimeId),
                    firstNonNull(existing.runtimeName, runtimeName),
                    firstNonNull(existing.environmentName, environmentName),
                    firstNonNull(existing.profileName, profileName),
                    channel
            );
            channelViews.remove(existing.id);
        }
        channelViews.put(next.id, next);
        channelsByInstance.put(channel, next);
        return next;
    }

    private static ClientView registerClientView(
            final Map<String, ClientView> clientViews,
            final IdentityHashMap<RpcClient, ClientView> clientsByInstance,
            final String suggestedId,
            final String suggestedName,
            final String environmentName,
            final String profileName,
            final String channelId,
            final RpcClient client
    ) {
        final ClientView existing = clientsByInstance.get(client);
        final ClientView next;
        if (existing == null) {
            next = new ClientView(suggestedId, suggestedName, environmentName, profileName, channelId, client);
        } else {
            next = new ClientView(
                    existing.id,
                    preferredName(existing.name, suggestedName),
                    firstNonNull(existing.environmentName, environmentName),
                    firstNonNull(existing.profileName, profileName),
                    firstNonNull(existing.channelId, channelId),
                    client
            );
            clientViews.remove(existing.id);
        }
        clientViews.put(next.id, next);
        clientsByInstance.put(client, next);
        return next;
    }

    private static ServerView registerServerView(
            final Map<String, ServerView> serverViews,
            final IdentityHashMap<RpcServer, ServerView> serversByInstance,
            final String suggestedId,
            final String suggestedName,
            final String environmentName,
            final String profileName,
            final String channelId,
            final RpcServer server
    ) {
        final ServerView existing = serversByInstance.get(server);
        final ServerView next;
        if (existing == null) {
            next = new ServerView(suggestedId, suggestedName, environmentName, profileName, channelId, server);
        } else {
            next = new ServerView(
                    existing.id,
                    preferredName(existing.name, suggestedName),
                    firstNonNull(existing.environmentName, environmentName),
                    firstNonNull(existing.profileName, profileName),
                    firstNonNull(existing.channelId, channelId),
                    server
            );
            serverViews.remove(existing.id);
        }
        serverViews.put(next.id, next);
        serversByInstance.put(server, next);
        return next;
    }

    private static String preferredName(
            final String current,
            final String candidate
    ) {
        if (candidate == null || candidate.isBlank()) {
            return current;
        }
        if (current == null || current.isBlank()) {
            return candidate;
        }
        return candidate.contains("/") ? candidate : current;
    }

    private static <T> T firstNonNull(
            final T first,
            final T second
    ) {
        return first != null ? first : second;
    }

    private void attachChannelListener(
            final RpcChannel channel
    ) {
        if (this.channelTelemetry.containsKey(channel)) {
            return;
        }
        final ChannelTelemetry telemetry = new ChannelTelemetry();
        channel.addListener(telemetry.listener);
        this.channelTelemetry.put(channel, telemetry);
    }

    private void attachClientListener(
            final RpcClient client
    ) {
        if (this.clientTelemetry.containsKey(client)) {
            return;
        }
        final ClientTelemetry telemetry = new ClientTelemetry();
        client.addListener(telemetry.listener);
        this.clientTelemetry.put(client, telemetry);
    }

    private void attachServerListener(
            final RpcServer server
    ) {
        if (this.serverTelemetry.containsKey(server)) {
            return;
        }
        final ServerTelemetry telemetry = new ServerTelemetry();
        server.addListener(telemetry.listener);
        this.serverTelemetry.put(server, telemetry);
    }

    private ChannelView requireChannel(
            final Discovery discovery,
            final String channelId
    ) {
        return requirePresent(discovery.channels.get(requireId(channelId, "channelId")), "channelId", channelId);
    }

    private ClientView requireClient(
            final Discovery discovery,
            final String clientId
    ) {
        return requirePresent(discovery.clients.get(requireId(clientId, "clientId")), "clientId", clientId);
    }

    private ServerView requireServer(
            final Discovery discovery,
            final String serverId
    ) {
        return requirePresent(discovery.servers.get(requireId(serverId, "serverId")), "serverId", serverId);
    }

    private ServiceView requireService(
            final Discovery discovery,
            final String serviceId
    ) {
        return requirePresent(discovery.services.get(requireId(serviceId, "serviceId")), "serviceId", serviceId);
    }

    private static String requireId(
            final String value,
            final String name
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static <T> T requirePresent(
            final T value,
            final String name,
            final String id
    ) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is not registered: " + id);
        }
        return value;
    }

    private static long average(
            final long total,
            final long count
    ) {
        return count == 0L ? 0L : total / count;
    }

    private static List<RpcAdminMethodSnapshot> methodSnapshots(
            final ConcurrentHashMap<Integer, MethodStats> methods
    ) {
        final List<RpcAdminMethodSnapshot> snapshots = new ArrayList<>(methods.size());
        for (final Map.Entry<Integer, MethodStats> entry : methods.entrySet()) {
            snapshots.add(toSnapshot("request-" + entry.getKey(), entry.getKey(), 0, true, entry.getValue()));
        }
        return List.copyOf(snapshots);
    }

    private static List<RpcAdminMethodSnapshot> methodSnapshots(
            final ConcurrentHashMap<Integer, MethodStats> methods,
            final List<RpcServer.RegisteredMethodSnapshot> registeredMethods
    ) {
        final List<RpcAdminMethodSnapshot> snapshots = new ArrayList<>(registeredMethods.size());
        for (final RpcServer.RegisteredMethodSnapshot method : registeredMethods) {
            snapshots.add(
                    toSnapshot(
                            method.contract().name(),
                            method.contract().requestMessageTypeId(),
                            method.contract().responseMessageTypeId(),
                            method.enabled(),
                            methods.get(method.contract().requestMessageTypeId())
                    )
            );
        }
        return List.copyOf(snapshots);
    }

    private static RpcAdminMethodSnapshot toSnapshot(
            final String name,
            final int requestMessageTypeId,
            final int responseMessageTypeId,
            final boolean enabled,
            final MethodStats stats
    ) {
        if (stats == null) {
            return new RpcAdminMethodSnapshot(name, requestMessageTypeId, responseMessageTypeId, enabled, 0L, 0L, 0L, 0L, 0L, 0L, 0L, null);
        }
        final long totalCalls = stats.totalCalls.sum();
        return new RpcAdminMethodSnapshot(
                name,
                requestMessageTypeId,
                responseMessageTypeId,
                enabled,
                totalCalls,
                stats.successes.sum(),
                stats.remoteErrors.sum(),
                stats.failures.sum(),
                stats.timeouts.sum(),
                average(stats.totalLatencyNs.sum(), totalCalls),
                stats.maxLatencyNs.get(),
                stats.lastFailure
        );
    }

    private record Discovery(
            Map<String, RuntimeView> runtimes,
            Map<String, ChannelView> channels,
            Map<String, ClientView> clients,
            Map<String, ServerView> servers,
            Map<String, ServiceView> services
    ) {
    }

    private record RuntimeView(
            String id,
            String name,
            RpcRuntime runtime
    ) {
    }

    private record ChannelView(
            String id,
            String name,
            String runtimeId,
            String runtimeName,
            String environmentName,
            String profileName,
            RpcChannel channel
    ) {
    }

    private record ClientView(
            String id,
            String name,
            String environmentName,
            String profileName,
            String channelId,
            RpcClient client
    ) {
    }

    private record ServerView(
            String id,
            String name,
            String environmentName,
            String profileName,
            String channelId,
            RpcServer server
    ) {
    }

    private record ServiceView(
            String id,
            String name,
            String environmentName,
            String profileName,
            String channelId,
            ServerView server,
            List<Integer> requestMessageTypeIds,
            RpcBootstrapEnvironment.RegisteredServiceSnapshot metadata
    ) {
    }

    private static final class ChannelTelemetry {
        private final LongAdder requestsSent = new LongAdder();
        private final LongAdder requestsReceived = new LongAdder();
        private final LongAdder responsesSent = new LongAdder();
        private final LongAdder responsesReceived = new LongAdder();
        private final LongAdder bytesOut = new LongAdder();
        private final LongAdder bytesIn = new LongAdder();
        private final LongAdder publishTimeouts = new LongAdder();
        private final LongAdder callTimeouts = new LongAdder();
        private final AtomicLong lastActivityAtEpochMs = new AtomicLong();
        private final RpcChannelListener listener = new RpcChannelListener() {
            @Override
            public void onRequestSent(final int messageTypeId, final int payloadLength, final long correlationId) {
                requestsSent.increment();
                bytesOut.add(payloadLength);
                touch();
            }

            @Override
            public void onRequestReceived(final int messageTypeId, final int payloadLength, final long correlationId) {
                requestsReceived.increment();
                bytesIn.add(payloadLength);
                touch();
            }

            @Override
            public void onResponseSent(final int messageTypeId, final int payloadLength, final int statusCode, final long correlationId) {
                responsesSent.increment();
                bytesOut.add(payloadLength);
                touch();
            }

            @Override
            public void onResponseReceived(final int messageTypeId, final int payloadLength, final int statusCode, final long correlationId) {
                responsesReceived.increment();
                bytesIn.add(payloadLength);
                touch();
            }

            @Override
            public void onPublishTimeout(final int messageTypeId, final int payloadLength, final long correlationId) {
                publishTimeouts.increment();
                touch();
            }

            @Override
            public void onCallTimeout(final long correlationId) {
                callTimeouts.increment();
                touch();
            }

            private void touch() {
                lastActivityAtEpochMs.set(System.currentTimeMillis());
            }
        };
    }

    private static final class ClientTelemetry {
        private final LongAdder totalCalls = new LongAdder();
        private final LongAdder successes = new LongAdder();
        private final LongAdder remoteErrors = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder timeouts = new LongAdder();
        private final LongAdder totalLatencyNs = new LongAdder();
        private final AtomicLong maxLatencyNs = new AtomicLong();
        private final ConcurrentHashMap<Integer, MethodStats> methods = new ConcurrentHashMap<>();
        private final RpcClientListener listener = new RpcClientListener() {
            @Override
            public void onSuccess(
                    final RpcMethodContract<?, ?> method,
                    final long timeoutNs,
                    final long latencyNs,
                    final int responsePayloadLength,
                    final int statusCode
            ) {
                final MethodStats stats = recordMethod(method, latencyNs, thisTelemetry());
                stats.successes.increment();
                successes.increment();
                totalCalls.increment();
                totalLatencyNs.add(latencyNs);
                updateMax(maxLatencyNs, latencyNs);
            }

            @Override
            public void onRemoteError(
                    final RpcMethodContract<?, ?> method,
                    final long timeoutNs,
                    final long latencyNs,
                    final int responsePayloadLength,
                    final int statusCode
            ) {
                final MethodStats stats = recordMethod(method, latencyNs, thisTelemetry());
                stats.remoteErrors.increment();
                remoteErrors.increment();
                totalCalls.increment();
                totalLatencyNs.add(latencyNs);
                updateMax(maxLatencyNs, latencyNs);
            }

            @Override
            public void onFailure(
                    final RpcMethodContract<?, ?> method,
                    final long timeoutNs,
                    final long latencyNs,
                    final Throwable error
            ) {
                final MethodStats stats = recordMethod(method, latencyNs, thisTelemetry());
                stats.failures.increment();
                stats.lastFailure = error.getMessage();
                if (error instanceof ru.pathcreator.pyc.rpc.core.exception.RpcCallTimeoutException
                    || error instanceof ru.pathcreator.pyc.rpc.core.exception.RpcPublishTimeoutException) {
                    stats.timeouts.increment();
                    timeouts.increment();
                }
                failures.increment();
                totalCalls.increment();
                totalLatencyNs.add(latencyNs);
                updateMax(maxLatencyNs, latencyNs);
            }

            private ClientTelemetry thisTelemetry() {
                return ClientTelemetry.this;
            }
        };
    }

    private static final class ServerTelemetry {
        private final LongAdder totalRequests = new LongAdder();
        private final LongAdder successes = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder totalLatencyNs = new LongAdder();
        private final AtomicLong maxLatencyNs = new AtomicLong();
        private final ConcurrentHashMap<Integer, MethodStats> methods = new ConcurrentHashMap<>();
        private final RpcServerListener listener = new RpcServerListener() {
            @Override
            public void onSuccess(
                    final RpcMethodContract<?, ?> method,
                    final long correlationId,
                    final long latencyNs,
                    final int requestPayloadLength,
                    final int responsePayloadLength,
                    final int statusCode
            ) {
                final MethodStats stats = recordMethod(method, latencyNs, thisTelemetry());
                stats.successes.increment();
                successes.increment();
                totalRequests.increment();
                totalLatencyNs.add(latencyNs);
                updateMax(maxLatencyNs, latencyNs);
            }

            @Override
            public void onFailure(
                    final RpcMethodContract<?, ?> method,
                    final long correlationId,
                    final long latencyNs,
                    final int requestPayloadLength,
                    final int responsePayloadLength,
                    final int statusCode,
                    final Throwable error
            ) {
                final MethodStats stats = recordMethod(method, latencyNs, thisTelemetry());
                stats.failures.increment();
                stats.lastFailure = error.getMessage();
                failures.increment();
                totalRequests.increment();
                totalLatencyNs.add(latencyNs);
                updateMax(maxLatencyNs, latencyNs);
            }

            private ServerTelemetry thisTelemetry() {
                return ServerTelemetry.this;
            }
        };
    }

    private static final class MethodStats {
        private final LongAdder totalCalls = new LongAdder();
        private final LongAdder successes = new LongAdder();
        private final LongAdder remoteErrors = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder timeouts = new LongAdder();
        private final LongAdder totalLatencyNs = new LongAdder();
        private final AtomicLong maxLatencyNs = new AtomicLong();
        private volatile String lastFailure;
    }

    private static MethodStats recordMethod(
            final RpcMethodContract<?, ?> method,
            final long latencyNs,
            final ConcurrentHashMap<Integer, MethodStats> methods
    ) {
        final MethodStats stats = methods.computeIfAbsent(method.requestMessageTypeId(), ignored -> new MethodStats());
        stats.totalCalls.increment();
        stats.totalLatencyNs.add(latencyNs);
        updateMax(stats.maxLatencyNs, latencyNs);
        return stats;
    }

    private static MethodStats recordMethod(
            final RpcMethodContract<?, ?> method,
            final long latencyNs,
            final ClientTelemetry telemetry
    ) {
        return recordMethod(method, latencyNs, telemetry.methods);
    }

    private static MethodStats recordMethod(
            final RpcMethodContract<?, ?> method,
            final long latencyNs,
            final ServerTelemetry telemetry
    ) {
        return recordMethod(method, latencyNs, telemetry.methods);
    }

    private static void updateMax(
            final AtomicLong target,
            final long value
    ) {
        long current;
        do {
            current = target.get();
            if (value <= current) {
                return;
            }
        } while (!target.compareAndSet(current, value));
    }
}