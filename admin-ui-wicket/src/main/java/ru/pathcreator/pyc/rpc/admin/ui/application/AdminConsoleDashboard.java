package ru.pathcreator.pyc.rpc.admin.ui.application;

import ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminSnapshot;

import java.io.Serializable;
import java.util.List;

public record AdminConsoleDashboard(
        RpcAdminSnapshot snapshot,
        ProcessTelemetrySnapshot telemetry,
        List<MethodRow> methodRows
) implements Serializable {
}