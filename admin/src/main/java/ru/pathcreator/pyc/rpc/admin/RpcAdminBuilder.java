package ru.pathcreator.pyc.rpc.admin;

import ru.pathcreator.pyc.rpc.bootstrap.RpcBootstrapEnvironment;
import ru.pathcreator.pyc.rpc.client.RpcClient;
import ru.pathcreator.pyc.rpc.core.RpcChannel;
import ru.pathcreator.pyc.rpc.core.RpcRuntime;
import ru.pathcreator.pyc.rpc.server.RpcServer;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RpcAdminBuilder {

    private byte[] accessTokenBytes;
    private final Map<String, RpcRuntime> runtimes = new LinkedHashMap<>();
    private final Map<String, RpcChannel> channels = new LinkedHashMap<>();
    private final Map<String, RpcClient> clients = new LinkedHashMap<>();
    private final Map<String, RpcServer> servers = new LinkedHashMap<>();
    private final Map<String, RpcBootstrapEnvironment> environments = new LinkedHashMap<>();

    RpcAdminBuilder() {
    }

    public RpcAdminBuilder accessToken(
            final String accessToken
    ) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
        this.accessTokenBytes = accessToken.getBytes(StandardCharsets.UTF_8);
        return this;
    }

    public RpcAdminBuilder accessToken(
            final byte[] accessTokenBytes
    ) {
        if (accessTokenBytes == null || accessTokenBytes.length == 0) {
            throw new IllegalArgumentException("accessTokenBytes must not be empty");
        }
        this.accessTokenBytes = accessTokenBytes.clone();
        return this;
    }

    public RpcAdminBuilder registerRuntime(
            final String name,
            final RpcRuntime runtime
    ) {
        this.runtimes.put(requireName(name), require(runtime, "runtime"));
        return this;
    }

    public RpcAdminBuilder registerChannel(
            final String name,
            final RpcChannel channel
    ) {
        this.channels.put(requireName(name), require(channel, "channel"));
        return this;
    }

    public RpcAdminBuilder registerClient(
            final String name,
            final RpcClient client
    ) {
        this.clients.put(requireName(name), require(client, "client"));
        return this;
    }

    public RpcAdminBuilder registerServer(
            final String name,
            final RpcServer server
    ) {
        this.servers.put(requireName(name), require(server, "server"));
        return this;
    }

    public RpcAdminBuilder registerBootstrapEnvironment(
            final String name,
            final RpcBootstrapEnvironment environment
    ) {
        this.environments.put(requireName(name), require(environment, "environment"));
        return this;
    }

    public RpcAdmin build() {
        if (this.accessTokenBytes == null || this.accessTokenBytes.length == 0) {
            throw new IllegalStateException("accessToken must be configured");
        }
        return new RpcAdmin(
                this.accessTokenBytes.clone(),
                Map.copyOf(this.runtimes),
                Map.copyOf(this.channels),
                Map.copyOf(this.clients),
                Map.copyOf(this.servers),
                Map.copyOf(this.environments)
        );
    }

    private static String requireName(
            final String name
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name;
    }

    private static <T> T require(
            final T value,
            final String name
    ) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}