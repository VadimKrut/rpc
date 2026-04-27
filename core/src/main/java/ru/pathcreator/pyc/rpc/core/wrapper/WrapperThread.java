package ru.pathcreator.pyc.rpc.core.wrapper;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

public final class WrapperThread {

    private final Thread thread;
    private volatile UnsafeBuffer bytes;

    public WrapperThread(
            final Thread thread
    ) {
        this.thread = thread;
    }

    public void wrap(
            final int offset,
            final int length,
            final DirectBuffer source
    ) {
        final UnsafeBuffer copy = new UnsafeBuffer(ByteBuffer.allocateDirect(length));
        copy.putBytes(0, source, offset, length);
        this.bytes = copy;
    }

    public Thread thread() {
        return this.thread;
    }

    public UnsafeBuffer bytes() {
        return this.bytes;
    }
}