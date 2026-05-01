package ru.pathcreator.pyc.rpc.admin.ui.application;

import ru.pathcreator.pyc.rpc.admin.RpcAdmin;
import ru.pathcreator.pyc.rpc.admin.RpcAdminSession;
import ru.pathcreator.pyc.rpc.admin.snapshot.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AdminConsoleFacade {

    private final RpcAdmin admin;
    private final ProcessTelemetryService telemetryService;

    public AdminConsoleFacade(final RpcAdmin admin) {
        this(admin, new ProcessTelemetryService());
    }

    public AdminConsoleFacade(final RpcAdmin admin,
                              final ProcessTelemetryService telemetryService) {
        this.admin = Objects.requireNonNull(admin, "admin");
        this.telemetryService = Objects.requireNonNull(telemetryService, "telemetryService");
    }

    public RpcAdminSession authenticate(final String accessToken) {
        return this.admin.authenticate(accessToken);
    }

    public AdminConsoleDashboard dashboard(final RpcAdminSession session) {
        final RpcAdminSnapshot snapshot = session.snapshot();
        return new AdminConsoleDashboard(
                snapshot,
                this.telemetryService.capture(),
                this.buildMethodRows(snapshot)
        );
    }

    public void pauseChannel(final RpcAdminSession session, final String channelId) {
        session.pauseChannel(channelId);
    }

    public void resumeChannel(final RpcAdminSession session, final String channelId) {
        session.resumeChannel(channelId);
    }

    public void enableClient(final RpcAdminSession session, final String clientId) {
        session.enableClient(clientId);
    }

    public void disableClient(final RpcAdminSession session, final String clientId) {
        session.disableClient(clientId);
    }

    public void enableServer(final RpcAdminSession session, final String serverId) {
        session.enableServer(serverId);
    }

    public void disableServer(final RpcAdminSession session, final String serverId) {
        session.disableServer(serverId);
    }

    public void enableService(final RpcAdminSession session, final String serviceId) {
        session.enableService(serviceId);
    }

    public void disableService(final RpcAdminSession session, final String serviceId) {
        session.disableService(serviceId);
    }

    public void enableMethod(final RpcAdminSession session,
                             final String serviceId,
                             final int requestMessageTypeId) {
        session.enableMethod(serviceId, requestMessageTypeId);
    }

    public void disableMethod(final RpcAdminSession session,
                              final String serviceId,
                              final int requestMessageTypeId) {
        session.disableMethod(serviceId, requestMessageTypeId);
    }

    private List<MethodRow> buildMethodRows(final RpcAdminSnapshot snapshot) {
        final List<MethodRow> rows = new ArrayList<>();
        for (final RpcAdminServiceSnapshot service : snapshot.services()) {
            for (final RpcAdminMethodSnapshot method : service.methods()) {
                rows.add(MethodRow.serviceRow(service, method));
            }
        }
        for (final RpcAdminServerSnapshot server : snapshot.servers()) {
            for (final RpcAdminMethodSnapshot method : server.methods()) {
                rows.add(MethodRow.serverRow(server, method));
            }
        }
        for (final RpcAdminClientSnapshot client : snapshot.clients()) {
            for (final RpcAdminMethodSnapshot method : client.methods()) {
                rows.add(MethodRow.clientRow(client, method));
            }
        }
        return rows;
    }
}