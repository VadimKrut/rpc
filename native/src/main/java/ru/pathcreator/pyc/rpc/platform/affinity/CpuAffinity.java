package ru.pathcreator.pyc.rpc.platform.affinity;

import ru.pathcreator.pyc.rpc.platform.OperatingSystem;

public interface CpuAffinity {

    boolean isSupported();

    OperatingSystem operatingSystem();

    String reason();

    void pinCurrentThread(int cpuId);
}