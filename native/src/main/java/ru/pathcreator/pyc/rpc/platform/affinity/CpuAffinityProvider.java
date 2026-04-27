package ru.pathcreator.pyc.rpc.platform.affinity;

import ru.pathcreator.pyc.rpc.platform.OperatingSystem;

public final class CpuAffinityProvider {

    private static final CpuAffinity CURRENT = createCurrent();

    private CpuAffinityProvider() {
    }

    public static CpuAffinity current() {
        return CURRENT;
    }

    private static CpuAffinity createCurrent() {
        return switch (OperatingSystem.current()) {
            case LINUX -> new LinuxCpuAffinity();
            case WINDOWS -> new WindowsCpuAffinity();
            default -> new UnsupportedCpuAffinity(OperatingSystem.current());
        };
    }

    private record UnsupportedCpuAffinity(OperatingSystem operatingSystem) implements CpuAffinity {

        @Override
        public boolean isSupported() {
            return false;
        }

        @Override
        public String reason() {
            return "FFM-based CPU affinity is not implemented yet for " + this.operatingSystem;
        }

        @Override
        public void pinCurrentThread(final int cpuId) {
            throw new UnsupportedOperationException(this.reason() + ", cpuId=" + cpuId);
        }
    }
}