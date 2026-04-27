package ru.pathcreator.pyc.rpc.platform.memory;

import ru.pathcreator.pyc.rpc.platform.OperatingSystem;

import java.lang.foreign.MemorySegment;

public final class MemoryLockerProvider {

    private static final MemoryLocker CURRENT = createCurrent();

    private MemoryLockerProvider() {
    }

    public static MemoryLocker current() {
        return CURRENT;
    }

    private static MemoryLocker createCurrent() {
        return switch (OperatingSystem.current()) {
            case LINUX -> new LinuxMemoryLocker();
            case WINDOWS -> new WindowsMemoryLocker();
            default -> new UnsupportedMemoryLocker(OperatingSystem.current());
        };
    }

    private record UnsupportedMemoryLocker(OperatingSystem operatingSystem) implements MemoryLocker {

        @Override
        public boolean isSupported() {
            return false;
        }

        @Override
        public String reason() {
            return "Native memory locking is not implemented yet for " + this.operatingSystem;
        }

        @Override
        public void lock(final MemorySegment segment) {
            throw new UnsupportedOperationException(this.reason());
        }

        @Override
        public void unlock(final MemorySegment segment) {
            throw new UnsupportedOperationException(this.reason());
        }
    }
}