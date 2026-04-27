package ru.pathcreator.pyc.rpc.platform.scheduler;

import ru.pathcreator.pyc.rpc.platform.OperatingSystem;
import ru.pathcreator.pyc.rpc.platform.ffi.NativeForeign;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

final class LinuxThreadScheduler implements ThreadScheduler {

    private static final int SCHED_OTHER = 0;
    private static final int SCHED_FIFO = 1;
    private static final int SCHED_RR = 2;
    private static final StructLayout SCHED_PARAM = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("sched_priority")
    );
    private static final SymbolLookup LIBC = NativeForeign.libraryLookup("libc.so.6", "libc.so");
    private static final MethodHandle SCHED_SET_SCHEDULER = NativeForeign.downcall(
            LIBC,
            FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS
            ),
            "sched_setscheduler"
    );

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public OperatingSystem operatingSystem() {
        return OperatingSystem.LINUX;
    }

    @Override
    public String reason() {
        return "Linux scheduling via sched_setscheduler";
    }

    @Override
    public void setCurrentThreadScheduling(final SchedulingPolicy policy, final int priority) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment param = arena.allocate(SCHED_PARAM);
            param.set(ValueLayout.JAVA_INT, 0L, normalizePriority(policy, priority));
            final int rc = (int) SCHED_SET_SCHEDULER.invokeExact(0, nativePolicy(policy), param);
            if (rc != 0) {
                throw new IllegalStateException(
                        "sched_setscheduler failed, rc=" + rc + ", policy=" + policy + ", priority=" + priority
                );
            }
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new IllegalStateException("sched_setscheduler invocation failed", e);
        }
    }

    private static int nativePolicy(final SchedulingPolicy policy) {
        return switch (policy) {
            case NORMAL -> SCHED_OTHER;
            case FIFO -> SCHED_FIFO;
            case ROUND_ROBIN -> SCHED_RR;
        };
    }

    private static int normalizePriority(final SchedulingPolicy policy, final int priority) {
        if (policy == SchedulingPolicy.NORMAL) {
            return 0;
        }
        if (priority < 1 || priority > 99) {
            throw new IllegalArgumentException("Linux realtime priority must be in [1..99], actual=" + priority);
        }
        return priority;
    }
}