package ru.pathcreator.pyc.rpc.admin.snapshot;

import java.io.Serializable;
import java.util.List;

public record RpcAdminServiceSnapshot(
        String id,
        String name,
        String environmentName,
        String profileName,
        String channelId,
        String serverId,
        boolean enabled,
        List<RpcAdminMethodSnapshot> methods
) implements Serializable {
}