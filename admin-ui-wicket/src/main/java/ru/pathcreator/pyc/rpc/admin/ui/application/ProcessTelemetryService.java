package ru.pathcreator.pyc.rpc.admin.ui.application;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.time.Duration;

public final class ProcessTelemetryService {

    public ProcessTelemetrySnapshot capture() {
        final OperatingSystemMXBean osBean =
                ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        final Runtime runtime = Runtime.getRuntime();
        return new ProcessTelemetrySnapshot(
                this.normalizePercent(osBean == null ? -1D : osBean.getProcessCpuLoad()),
                this.normalizePercent(osBean == null ? -1D : osBean.getCpuLoad()),
                runtime.totalMemory() - runtime.freeMemory(),
                runtime.totalMemory(),
                ManagementFactory.getThreadMXBean().getThreadCount(),
                Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime())
        );
    }

    private double normalizePercent(final double value) {
        if (value < 0D) {
            return -1D;
        }
        return value * 100D;
    }
}