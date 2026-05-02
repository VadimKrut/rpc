package ru.pathcreator.pyc.rpc.admin.ui.application;

import ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminClientSnapshot;
import ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminMethodSnapshot;
import ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminServerSnapshot;
import ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminServiceSnapshot;

import java.io.Serializable;

public record MethodRow(
        MethodOwnerType ownerType,
        String ownerId,
        String ownerName,
        String environmentName,
        String profileName,
        String channelId,
        boolean operationAvailable,
        RpcAdminMethodSnapshot method
) implements Serializable {

    public static MethodRow serviceRow(final RpcAdminServiceSnapshot service,
                                       final RpcAdminMethodSnapshot method) {
        return new MethodRow(
                MethodOwnerType.SERVICE,
                service.id(),
                service.name(),
                service.environmentName(),
                service.profileName(),
                service.channelId(),
                true,
                method
        );
    }

    public static MethodRow serverRow(final RpcAdminServerSnapshot server,
                                      final RpcAdminMethodSnapshot method) {
        return new MethodRow(
                MethodOwnerType.SERVER,
                server.id(),
                server.name(),
                server.environmentName(),
                server.profileName(),
                server.channelId(),
                false,
                method
        );
    }

    public static MethodRow clientRow(final RpcAdminClientSnapshot client,
                                      final RpcAdminMethodSnapshot method) {
        return new MethodRow(
                MethodOwnerType.CLIENT,
                client.id(),
                client.name(),
                client.environmentName(),
                client.profileName(),
                client.channelId(),
                false,
                method
        );
    }
}