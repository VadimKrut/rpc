package ru.pathcreator.pyc.rpc.admin.snapshot;

public record RpcAdminMethodSnapshot(
        String name,
        int requestMessageTypeId,
        int responseMessageTypeId,
        boolean enabled,
        long totalCalls,
        long successes,
        long remoteErrors,
        long failures,
        long timeouts,
        long averageLatencyNs,
        long maxLatencyNs,
        String lastFailure
) {
}