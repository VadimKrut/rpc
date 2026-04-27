package ru.pathcreator.pyc.rpc.platform.scheduler;

import ru.pathcreator.pyc.rpc.platform.OperatingSystem;
import ru.pathcreator.pyc.rpc.platform.ffi.NativeForeign;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

final class WindowsThreadScheduler implements ThreadScheduler {

    private static final int THREAD_PRIORITY_BELOW_NORMAL = -1;
    private static final int THREAD_PRIORITY_NORMAL = 0;
    private static final int THREAD_PRIORITY_ABOVE_NORMAL = 1;
    private static final int THREAD_PRIORITY_HIGHEST = 2;
    private static final int THREAD_PRIORITY_TIME_CRITICAL = 15;
    private static final SymbolLookup KERNEL32 = NativeForeign.libraryLookup("kernel32", "Kernel32", "Kernel32.dll");
    private static final MethodHandle GET_CURRENT_THREAD = NativeForeign.downcall(
            KERNEL32,
            FunctionDescriptor.of(ValueLayout.ADDRESS),
            "GetCurrentThread"
    );
    private static final MethodHandle SET_THREAD_PRIORITY = NativeForeign.downcall(
            KERNEL32,
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
            "SetThreadPriority"
    );
    private static final MethodHandle GET_LAST_ERROR = NativeForeign.downcall(
            KERNEL32,
            FunctionDescriptor.of(ValueLayout.JAVA_INT),
            "GetLastError"
    );

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public OperatingSystem operatingSystem() {
        return OperatingSystem.WINDOWS;
    }

    @Override
    public String reason() {
        return "Windows scheduling via SetThreadPriority";
    }

    @Override
    public void setCurrentThreadScheduling(final SchedulingPolicy policy, final int priority) {
        final int nativePriority = mapPriority(policy, priority);
        try {
            final MemorySegment threadHandle = (MemorySegment) GET_CURRENT_THREAD.invokeExact();
            final int ok = (int) SET_THREAD_PRIORITY.invokeExact(threadHandle, nativePriority);
            if (ok == 0) {
                final int error = (int) GET_LAST_ERROR.invokeExact();
                throw new IllegalStateException(
                        "SetThreadPriority failed, error=" + error + ", policy=" + policy + ", priority=" + priority
                );
            }
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new IllegalStateException("SetThreadPriority invocation failed", e);
        }
    }

    private static int mapPriority(final SchedulingPolicy policy, final int priority) {
        return switch (policy) {
            case NORMAL -> priority <= 0 ? THREAD_PRIORITY_BELOW_NORMAL : THREAD_PRIORITY_NORMAL;
            case FIFO -> priority >= 50 ? THREAD_PRIORITY_HIGHEST : THREAD_PRIORITY_ABOVE_NORMAL;
            case ROUND_ROBIN -> priority >= 50 ? THREAD_PRIORITY_TIME_CRITICAL : THREAD_PRIORITY_ABOVE_NORMAL;
        };
    }
}