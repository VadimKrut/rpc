package ru.pathcreator.pyc.rpc.admin.snapshot;

import java.util.List;

public record RpcAdminServerSnapshot(
        String id,
        String name,
        String environmentName,
        String profileName,
        String channelId,
        boolean enabled,
        long totalRequests,
        long successes,
        long failures,
        long averageLatencyNs,
        long maxLatencyNs,
        int registeredMethodCount,
        List<RpcAdminMethodSnapshot> methods
) {
}