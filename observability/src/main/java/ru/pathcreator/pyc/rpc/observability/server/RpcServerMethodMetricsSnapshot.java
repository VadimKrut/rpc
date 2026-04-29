package ru.pathcreator.pyc.rpc.observability.server;

public record RpcServerMethodMetricsSnapshot(
        String methodName,
        int requestMessageTypeId,
        int responseMessageTypeId,
        long requests,
        long successes,
        long failures,
        long clientErrors,
        long serverErrors,
        long otherErrors,
        long inFlight,
        long maxInFlight,
        long totalLatencyNs,
        long maxLatencyNs,
        long totalRequestPayloadBytes,
        long totalResponsePayloadBytes
) {
}