package ru.pathcreator.pyc.rpc.platform.affinity;

import ru.pathcreator.pyc.rpc.platform.OperatingSystem;
import ru.pathcreator.pyc.rpc.platform.ffi.NativeForeign;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

final class LinuxCpuAffinity implements CpuAffinity {

    private static final long CPU_SET_BYTES = 128L;
    private static final Linker LINKER = NativeForeign.linker();
    private static final SymbolLookup LIBC = NativeForeign.libraryLookup("libc.so.6", "libc.so");
    private static final Optional<MethodHandle> SCHED_SET_AFFINITY = NativeForeign.downcallOptional(
            LIBC,
            FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS
            ),
            "sched_setaffinity"
    );
    private static final Optional<MethodHandle> PTHREAD_SELF = NativeForeign.downcallOptional(
            LIBC,
            FunctionDescriptor.of(ValueLayout.JAVA_LONG),
            "pthread_self"
    );
    private static final Optional<MethodHandle> PTHREAD_SET_AFFINITY = NativeForeign.downcallOptional(
            LIBC,
            FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS
            ),
            "pthread_setaffinity_np"
    );

    @Override
    public boolean isSupported() {
        return SCHED_SET_AFFINITY.isPresent() || (PTHREAD_SELF.isPresent() && PTHREAD_SET_AFFINITY.isPresent());
    }

    @Override
    public OperatingSystem operatingSystem() {
        return OperatingSystem.LINUX;
    }

    @Override
    public String reason() {
        if (SCHED_SET_AFFINITY.isPresent()) {
            return "Linux affinity via sched_setaffinity";
        }
        if (PTHREAD_SELF.isPresent() && PTHREAD_SET_AFFINITY.isPresent()) {
            return "Linux affinity via pthread_setaffinity_np";
        }
        return "Linux affinity API is not available";
    }

    @Override
    public void pinCurrentThread(final int cpuId) {
        if (cpuId < 0) {
            throw new IllegalArgumentException("cpuId must be >= 0");
        }
        final long byteIndex = cpuId / 8L;
        if (byteIndex >= CPU_SET_BYTES) {
            throw new IllegalArgumentException("cpuId is too large for cpu_set_t mask: " + cpuId);
        }
        if (!this.isSupported()) {
            throw new UnsupportedOperationException(this.reason());
        }
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment cpuSet = arena.allocate(CPU_SET_BYTES, 8L);
            final byte bitMask = (byte) (1 << (cpuId % 8));
            cpuSet.set(ValueLayout.JAVA_BYTE, byteIndex, bitMask);
            if (SCHED_SET_AFFINITY.isPresent()) {
                final int rc = (int) SCHED_SET_AFFINITY.orElseThrow().invokeExact(0, CPU_SET_BYTES, cpuSet);
                if (rc != 0) {
                    throw new IllegalStateException("sched_setaffinity failed, rc=" + rc + ", cpuId=" + cpuId);
                }
                return;
            }
            final long pthread = (long) PTHREAD_SELF.orElseThrow().invokeExact();
            final int rc = (int) PTHREAD_SET_AFFINITY.orElseThrow().invokeExact(pthread, CPU_SET_BYTES, cpuSet);
            if (rc != 0) {
                throw new IllegalStateException("pthread_setaffinity_np failed, rc=" + rc + ", cpuId=" + cpuId);
            }
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new IllegalStateException("Linux affinity invocation failed for cpuId=" + cpuId, e);
        }
    }
}