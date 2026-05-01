package ru.pathcreator.pyc.rpc.admin;

import ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminSnapshot;

public final class RpcAdminSession {

    private final RpcAdmin admin;

    RpcAdminSession(
            final RpcAdmin admin
    ) {
        this.admin = admin;
    }

    public RpcAdminSnapshot snapshot() {
        return this.admin.snapshotInternal();
    }

    public void pauseChannel(
            final String channelId
    ) {
        this.admin.pauseChannelInternal(channelId);
    }

    public void resumeChannel(
            final String channelId
    ) {
        this.admin.resumeChannelInternal(channelId);
    }

    public void disableClient(
            final String clientId
    ) {
        this.admin.disableClientInternal(clientId);
    }

    public void enableClient(
            final String clientId
    ) {
        this.admin.enableClientInternal(clientId);
    }

    public void disableServer(
            final String serverId
    ) {
        this.admin.disableServerInternal(serverId);
    }

    public void enableServer(
            final String serverId
    ) {
        this.admin.enableServerInternal(serverId);
    }

    public void disableService(
            final String serviceId
    ) {
        this.admin.disableServiceInternal(serviceId);
    }

    public void enableService(
            final String serviceId
    ) {
        this.admin.enableServiceInternal(serviceId);
    }

    public void disableMethod(
            final String serverId,
            final int requestMessageTypeId
    ) {
        this.admin.disableMethodInternal(serverId, requestMessageTypeId);
    }

    public void enableMethod(
            final String serverId,
            final int requestMessageTypeId
    ) {
        this.admin.enableMethodInternal(serverId, requestMessageTypeId);
    }
}