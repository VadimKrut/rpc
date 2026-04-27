package ru.pathcreator.pyc.rpc.platform.memory;

import ru.pathcreator.pyc.rpc.platform.OperatingSystem;
import ru.pathcreator.pyc.rpc.platform.ffi.NativeForeign;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

final class WindowsMemoryLocker implements MemoryLocker {

    private static final SymbolLookup KERNEL32 = NativeForeign.libraryLookup("kernel32", "Kernel32", "Kernel32.dll");
    private static final MethodHandle VIRTUAL_LOCK = NativeForeign.downcall(
            KERNEL32,
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
            "VirtualLock"
    );
    private static final MethodHandle VIRTUAL_UNLOCK = NativeForeign.downcall(
            KERNEL32,
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
            "VirtualUnlock"
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
        return "Windows memory locking via VirtualLock/VirtualUnlock";
    }

    @Override
    public void lock(final MemorySegment segment) {
        invoke(VIRTUAL_LOCK, "VirtualLock", segment);
    }

    @Override
    public void unlock(final MemorySegment segment) {
        invoke(VIRTUAL_UNLOCK, "VirtualUnlock", segment);
    }

    private void invoke(final MethodHandle handle, final String operation, final MemorySegment segment) {
        if (segment == null || segment.byteSize() == 0L) {
            throw new IllegalArgumentException("segment must not be null or empty");
        }
        try {
            final int ok = (int) handle.invokeExact(segment, segment.byteSize());
            if (ok == 0) {
                final int error = (int) GET_LAST_ERROR.invokeExact();
                throw new IllegalStateException(operation + " failed, error=" + error + ", size=" + segment.byteSize());
            }
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Throwable e) {
            throw new IllegalStateException(operation + " invocation failed", e);
        }
    }
}