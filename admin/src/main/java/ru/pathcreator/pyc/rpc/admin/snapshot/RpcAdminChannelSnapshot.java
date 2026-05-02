package ru.pathcreator.pyc.rpc.admin.snapshot;

import java.io.Serializable;

public record RpcAdminChannelSnapshot(
        String id,
        String name,
        String runtimeId,
        String runtimeName,
        String environmentName,
        String profileName,
        String publicationChannel,
        String subscriptionChannel,
        int streamId,
        boolean paused,
        boolean closed,
        int currentWaiters,
        int waitersCapacity,
        long estimatedOwnedMemoryBytes,
        long requestsSent,
        long requestsReceived,
        long responsesSent,
        long responsesReceived,
        long bytesOut,
        long bytesIn,
        long publishTimeouts,
        long callTimeouts,
        long lastActivityAtEpochMs
) implements Serializable {
}