package ru.pathcreator.pyc.rpc.core.generator;

import java.util.concurrent.atomic.AtomicLong;

public final class CorrelationIdGenerator {

    private static final int BLOCK_SIZE = 1024;

    private final AtomicLong nextBlockBase = new AtomicLong(1L);
    private final ThreadLocal<Range> localRange = ThreadLocal.withInitial(Range::new);

    public long nextId() {
        final Range range = this.localRange.get();
        if (range.next >= range.limit) {
            final long base = this.nextBlockBase.getAndAdd(BLOCK_SIZE);
            range.next = base;
            range.limit = base + BLOCK_SIZE;
        }
        final long id = range.next++;
        if (id == 0L) {
            return this.nextId();
        }
        return id;
    }

    private static final class Range {
        private long next;
        private long limit;
    }
}