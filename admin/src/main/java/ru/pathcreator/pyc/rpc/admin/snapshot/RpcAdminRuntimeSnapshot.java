package ru.pathcreator.pyc.rpc.admin.snapshot;

import java.io.Serializable;
import java.util.List;

public record RpcAdminRuntimeSnapshot(
        String id,
        String name,
        String runtimeId,
        String aeronDirectoryName,
        boolean closed,
        int channelCount,
        List<String> channelIds
) implements Serializable {
}