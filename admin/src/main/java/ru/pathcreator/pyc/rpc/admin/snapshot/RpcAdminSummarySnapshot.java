package ru.pathcreator.pyc.rpc.admin.snapshot;

import java.io.Serializable;

public record RpcAdminSummarySnapshot(
        int runtimeCount,
        int channelCount,
        int activeChannelCount,
        int pausedChannelCount,
        int closedChannelCount,
        int clientCount,
        int enabledClientCount,
        int serverCount,
        int enabledServerCount,
        int serviceCount,
        int enabledServiceCount
) implements Serializable {
}