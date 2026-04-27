package ru.pathcreator.pyc.rpc.platform.memory;

import ru.pathcreator.pyc.rpc.platform.OperatingSystem;

import java.lang.foreign.MemorySegment;

public interface MemoryLocker {

    boolean isSupported();

    OperatingSystem operatingSystem();

    String reason();

    void lock(MemorySegment segment);

    void unlock(MemorySegment segment);
}