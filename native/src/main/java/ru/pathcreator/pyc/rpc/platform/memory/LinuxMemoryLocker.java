package ru.pathcreator.pyc.rpc.platform.memory;

import ru.pathcreator.pyc.rpc.platform.OperatingSystem;
import ru.pathcreator.pyc.rpc.platform.ffi.NativeForeign;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

final class LinuxMemoryLocker implements MemoryLocker {

    private static final SymbolLookup LIBC = NativeForeign.libraryLookup("libc.so.6", "libc.so");
    private static final MethodHandle MLOCK = NativeForeign.downcall(
            LIBC,
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
            "mlock"
    );
    private static final MethodHandle MUNLOCK = NativeForeign.downcall(
            LIBC,
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
            "munlock"
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
        return "Linux memory locking via mlock/munlock";
    }

    @Override
    public void lock(final MemorySegment segment) {
        invoke(MLOCK, "mlock", segment);
    }

    @Override
    public void unlock(final MemorySegment segment) {
        invoke(MUNLOCK, "munlock", segment);
    }

    private static void invoke(final MethodHandle handle, final String operation, final MemorySegment segment) {
        if (segment == null || segment.byteSize() == 0L) {
            throw new IllegalArgumentException("segment must not be null or empty");
        }
        try {
            final int rc = (int) handle.invokeExact(segment, segment.byteSize());
            if (rc != 0) {
                throw new IllegalStateException(operation + " failed, rc=" + rc + ", size=" + segment.byteSize());
            }
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new IllegalStateException(operation + " invocation failed", e);
        }
    }
}