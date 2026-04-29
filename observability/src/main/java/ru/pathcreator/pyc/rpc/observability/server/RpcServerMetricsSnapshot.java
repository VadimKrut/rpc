package ru.pathcreator.pyc.rpc.observability.server;

import java.util.List;

public record RpcServerMetricsSnapshot(
        List<RpcServerMethodMetricsSnapshot> methods
) {
}