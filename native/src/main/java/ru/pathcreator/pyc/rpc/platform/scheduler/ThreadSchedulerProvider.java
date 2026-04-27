package ru.pathcreator.pyc.rpc.platform.scheduler;

import ru.pathcreator.pyc.rpc.platform.OperatingSystem;

public final class ThreadSchedulerProvider {

    private static final ThreadScheduler CURRENT = createCurrent();

    private ThreadSchedulerProvider() {
    }

    public static ThreadScheduler current() {
        return CURRENT;
    }

    private static ThreadScheduler createCurrent() {
        return switch (OperatingSystem.current()) {
            case LINUX -> new LinuxThreadScheduler();
            case WINDOWS -> new WindowsThreadScheduler();
            default -> new UnsupportedThreadScheduler(OperatingSystem.current());
        };
    }

    private record UnsupportedThreadScheduler(OperatingSystem operatingSystem) implements ThreadScheduler {

        @Override
        public boolean isSupported() {
            return false;
        }

        @Override
        public String reason() {
            return "Native thread scheduling is not implemented yet for " + this.operatingSystem;
        }

        @Override
        public void setCurrentThreadScheduling(final SchedulingPolicy policy, final int priority) {
            throw new UnsupportedOperationException(this.reason() + ", policy=" + policy + ", priority=" + priority);
        }
    }
}