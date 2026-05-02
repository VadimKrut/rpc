package ru.pathcreator.pyc.rpc.admin.ui.application;

import java.io.Serializable;
import java.time.Duration;

public record ProcessTelemetrySnapshot(
        double processCpuLoadPercent,
        double systemCpuLoadPercent,
        long heapUsedBytes,
        long heapCommittedBytes,
        int liveThreadCount,
        Duration uptime
) implements Serializable {
}