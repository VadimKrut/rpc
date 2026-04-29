package ru.pathcreator.pyc.rpc.observability.client;

import java.util.List;

public record RpcClientMetricsSnapshot(
        List<RpcClientMethodMetricsSnapshot> methods
) {
}