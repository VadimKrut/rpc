package ru.pathcreator.pyc.rpc.admin.snapshot;

import java.util.List;

public record RpcAdminClientSnapshot(
        String id,
        String name,
        String environmentName,
        String profileName,
        String channelId,
        boolean enabled,
        long defaultTimeoutNs,
        long totalCalls,
        long successes,
        long remoteErrors,
        long failures,
        long timeouts,
        long averageLatencyNs,
        long maxLatencyNs,
        List<RpcAdminMethodSnapshot> methods
) {
}