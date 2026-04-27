package ru.pathcreator.pyc.rpc.platform.scheduler;

import ru.pathcreator.pyc.rpc.platform.OperatingSystem;

public interface ThreadScheduler {

    boolean isSupported();

    OperatingSystem operatingSystem();

    String reason();

    void setCurrentThreadScheduling(SchedulingPolicy policy, int priority);
}