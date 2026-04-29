package ru.pathcreator.pyc.rpc.observability;

import java.util.concurrent.atomic.AtomicLong;

public final class InternalMetricsSupport {

    private InternalMetricsSupport() {
    }

    public static void updateMax(
            final AtomicLong currentMax,
            final long candidate
    ) {
        long observed = currentMax.get();
        while (candidate > observed && !currentMax.compareAndSet(observed, candidate)) {
            observed = currentMax.get();
        }
    }
}