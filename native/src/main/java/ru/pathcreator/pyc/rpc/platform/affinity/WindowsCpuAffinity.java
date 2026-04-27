package ru.pathcreator.pyc.rpc.platform.affinity;

import ru.pathcreator.pyc.rpc.platform.OperatingSystem;
import ru.pathcreator.pyc.rpc.platform.ffi.NativeForeign;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

final class WindowsCpuAffinity implements CpuAffinity {

    private static final Linker LINKER = NativeForeign.linker();
    private static final SymbolLookup KERNEL32 = NativeForeign.libraryLookup("kernel32", "Kernel32", "Kernel32.dll");
    private static final MethodHandle GET_CURRENT_THREAD = LINKER.downcallHandle(
            NativeForeign.requireAny(KERNEL32, "GetCurrentThread"),
            FunctionDescriptor.of(ValueLayout.ADDRESS)
    );
    private static final MethodHandle SET_THREAD_AFFINITY_MASK = LINKER.downcallHandle(
            NativeForeign.requireAny(KERNEL32, "SetThreadAffinityMask"),
            FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG
            )
    );
    private static final MethodHandle GET_LAST_ERROR = LINKER.downcallHandle(
            NativeForeign.requireAny(KERNEL32, "GetLastError"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT)
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
        return "Windows affinity via SetThreadAffinityMask";
    }

    @Override
    public void pinCurrentThread(final int cpuId) {
        if (cpuId < 0) {
            throw new IllegalArgumentException("cpuId must be >= 0");
        }
        if (cpuId >= Long.SIZE) {
            throw new IllegalArgumentException("Windows affinity mask implementation supports cpuId < 64, actual=" + cpuId);
        }
        final long mask = 1L << cpuId;
        try {
            final MemorySegment threadHandle = (MemorySegment) GET_CURRENT_THREAD.invokeExact();
            final long previousMask = (long) SET_THREAD_AFFINITY_MASK.invokeExact(threadHandle, mask);
            if (previousMask == 0L) {
                final int error = (int) GET_LAST_ERROR.invokeExact();
                throw new IllegalStateException(
                        "SetThreadAffinityMask failed, cpuId=" + cpuId + ", error=" + error
                );
            }
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new IllegalStateException("SetThreadAffinityMask invocation failed for cpuId=" + cpuId, e);
        }
    }
}