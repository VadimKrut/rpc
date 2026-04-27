package ru.pathcreator.pyc.rpc.core.generator;

import java.util.concurrent.locks.LockSupport;

public final class YieldThenParkIdleStrategy {

    private static final long[] PARK_SEQUENCE_NS = {1L, 10L, 50L, 1_000L, 10_000L, 100_000L};

    private int parkIndex;
    private int idleCounter;
    private final int maxYields;

    public YieldThenParkIdleStrategy(
            final int maxYields
    ) {
        this.maxYields = maxYields;
    }

    public void idle() {
        if (this.idleCounter < this.maxYields) {
            this.idleCounter += 1;
            Thread.yield();
            return;
        }
        LockSupport.parkNanos(PARK_SEQUENCE_NS[Math.min(this.parkIndex, PARK_SEQUENCE_NS.length - 1)]);
        if (this.parkIndex < PARK_SEQUENCE_NS.length - 1) {
            this.parkIndex += 1;
        }
    }

    public void reset() {
        this.idleCounter = 0;
        this.parkIndex = 0;
    }
}