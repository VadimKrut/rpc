package ru.pathcreator.pyc.rpc.observability.client;

public record RpcClientMethodMetricsSnapshot(
        String methodName,
        int requestMessageTypeId,
        int responseMessageTypeId,
        long calls,
        long successes,
        long remoteErrors,
        long timeouts,
        long localFailures,
        long inFlight,
        long maxInFlight,
        long totalLatencyNs,
        long maxLatencyNs,
        long totalResponsePayloadBytes,
        long remoteClientErrors,
        long remoteServerErrors,
        long remoteOtherErrors
) {
}