package ru.pathcreator.pyc.rpc.admin.snapshot;

import java.io.Serializable;
import java.util.List;

public record RpcAdminSnapshot(
        long createdAtEpochMs,
        RpcAdminSummarySnapshot summary,
        List<RpcAdminRuntimeSnapshot> runtimes,
        List<RpcAdminChannelSnapshot> channels,
        List<RpcAdminClientSnapshot> clients,
        List<RpcAdminServerSnapshot> servers,
        List<RpcAdminServiceSnapshot> services
) implements Serializable {
}