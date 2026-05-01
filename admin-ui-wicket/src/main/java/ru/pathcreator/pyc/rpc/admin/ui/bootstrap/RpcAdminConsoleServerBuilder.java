package ru.pathcreator.pyc.rpc.admin.ui.bootstrap;

import ru.pathcreator.pyc.rpc.admin.RpcAdmin;
import ru.pathcreator.pyc.rpc.admin.RpcAdminBuilder;
import ru.pathcreator.pyc.rpc.admin.ui.application.AdminConsoleFacade;
import ru.pathcreator.pyc.rpc.bootstrap.RpcBootstrapEnvironment;
import ru.pathcreator.pyc.rpc.client.RpcClient;
import ru.pathcreator.pyc.rpc.core.RpcChannel;
import ru.pathcreator.pyc.rpc.core.RpcRuntime;
import ru.pathcreator.pyc.rpc.server.RpcServer;

import java.util.Objects;

public final class RpcAdminConsoleServerBuilder {

    private RpcAdmin admin;
    private final RpcAdminBuilder adminBuilder = RpcAdmin.builder();
    private RpcAdminConsoleSettings settings = RpcAdminConsoleSettings.builder().build();

    public RpcAdminConsoleServerBuilder admin(final RpcAdmin admin) {
        this.admin = Objects.requireNonNull(admin, "admin");
        return this;
    }

    public RpcAdminConsoleServerBuilder settings(final RpcAdminConsoleSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        return this;
    }

    public RpcAdminConsoleServerBuilder accessToken(final String accessToken) {
        this.adminBuilder.accessToken(accessToken);
        return this;
    }

    public RpcAdminConsoleServerBuilder registerRuntime(final String name, final RpcRuntime runtime) {
        this.adminBuilder.registerRuntime(name, runtime);
        return this;
    }

    public RpcAdminConsoleServerBuilder registerChannel(final String name, final RpcChannel channel) {
        this.adminBuilder.registerChannel(name, channel);
        return this;
    }

    public RpcAdminConsoleServerBuilder registerClient(final String name, final RpcClient client) {
        this.adminBuilder.registerClient(name, client);
        return this;
    }

    public RpcAdminConsoleServerBuilder registerServer(final String name, final RpcServer server) {
        this.adminBuilder.registerServer(name, server);
        return this;
    }

    public RpcAdminConsoleServerBuilder registerBootstrapEnvironment(final String name,
                                                                     final RpcBootstrapEnvironment environment) {
        this.adminBuilder.registerBootstrapEnvironment(name, environment);
        return this;
    }

    public RpcAdminConsoleServer build() {
        final RpcAdmin resolvedAdmin = this.admin == null ? this.adminBuilder.build() : this.admin;
        return new RpcAdminConsoleServer(
                new AdminConsoleFacade(resolvedAdmin),
                this.settings
        );
    }
}