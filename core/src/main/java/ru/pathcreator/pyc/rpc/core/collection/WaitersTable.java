package ru.pathcreator.pyc.rpc.core.collection;

import org.agrona.BitUtil;
import org.agrona.collections.Hashing;
import ru.pathcreator.pyc.rpc.core.wrapper.WrapperThread;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

public final class WaitersTable {

    private static final int MIN_CAPACITY = 8;

    private int size;
    private long[] keys;
    private final Object writes;
    private int resizeThreshold;
    private volatile int version;
    private final float loadFactor;
    private WrapperThread[] values;

    public WaitersTable(
            final int initialCapacity,
            final float loadFactor
    ) {
        if (!(loadFactor > 0.0F && loadFactor < 1.0F)) {
            throw new IllegalArgumentException("invalid load factor: " + loadFactor);
        }
        this.writes = new Object();
        this.loadFactor = loadFactor;
        final int capacity = BitUtil.findNextPositivePowerOfTwo(Math.max(MIN_CAPACITY, initialCapacity));
        this.resizeThreshold = (int) (capacity * loadFactor);
        this.keys = new long[capacity];
        this.values = new WrapperThread[capacity];
    }

    public int size() {
        return this.size;
    }

    public int capacity() {
        return this.values.length;
    }

    public WrapperThread get(
            final long key
    ) {
        while (true) {
            final int before = this.version;
            if ((before & 1) != 0) {
                Thread.onSpinWait();
                continue;
            }
            final long[] currentKeys = this.keys;
            final WrapperThread[] currentValues = this.values;
            final WrapperThread found = this.findValue(key, currentKeys, currentValues);
            if (before == this.version) {
                return found;
            }
        }
    }

    public void put(
            final long key,
            final WrapperThread value
    ) {
        Objects.requireNonNull(value, "value cannot be null");
        synchronized (this.writes) {
            this.version += 1;
            try {
                final int mask = this.values.length - 1;
                int index = Hashing.hash(key, mask);
                WrapperThread old;
                while ((old = this.values[index]) != null && key != this.keys[index]) {
                    index = (index + 1) & mask;
                }
                if (old == null) {
                    this.size += 1;
                    this.keys[index] = key;
                }
                this.values[index] = value;
                if (this.size > this.resizeThreshold) {
                    this.rehash(this.values.length << 1);
                }
            } finally {
                this.version += 1;
            }
        }
    }

    public WrapperThread remove(
            final long key
    ) {
        synchronized (this.writes) {
            this.version += 1;
            try {
                final int mask = this.values.length - 1;
                for (int index = Hashing.hash(key, mask); this.values[index] != null; index = (index + 1) & mask) {
                    if (key == this.keys[index]) {
                        final WrapperThread old = this.values[index];
                        this.values[index] = null;
                        this.size -= 1;
                        this.compactChain(index);
                        return old;
                    }
                }
                return null;
            } finally {
                this.version += 1;
            }
        }
    }

    public void clearAndForEach(
            final Consumer<WrapperThread> consumer
    ) {
        synchronized (this.writes) {
            this.version += 1;
            try {
                for (final WrapperThread value : this.values) {
                    if (value != null) {
                        consumer.accept(value);
                    }
                }
                Arrays.fill(this.values, null);
                this.size = 0;
            } finally {
                this.version += 1;
            }
        }
    }

    private WrapperThread findValue(
            final long key,
            final long[] currentKeys,
            final WrapperThread[] currentValues
    ) {
        final int mask = currentValues.length - 1;
        for (int index = Hashing.hash(key, mask); currentValues[index] != null; index = (index + 1) & mask) {
            if (key == currentKeys[index]) {
                return currentValues[index];
            }
        }
        return null;
    }

    private void rehash(
            final int newCapacity
    ) {
        if (newCapacity < 0) {
            throw new IllegalStateException("max capacity reached at size=" + this.size);
        }
        final int mask = newCapacity - 1;
        final long[] expandedKeys = new long[newCapacity];
        final WrapperThread[] expandedValues = new WrapperThread[newCapacity];
        for (int index = 0; index < this.values.length; index++) {
            final WrapperThread value = this.values[index];
            if (value != null) {
                final long key = this.keys[index];
                int target = Hashing.hash(key, mask);
                while (expandedValues[target] != null) {
                    target = (target + 1) & mask;
                }
                expandedKeys[target] = key;
                expandedValues[target] = value;
            }
        }
        this.resizeThreshold = (int) (newCapacity * this.loadFactor);
        this.keys = expandedKeys;
        this.values = expandedValues;
    }

    private void compactChain(
            int deleteIndex
    ) {
        final int mask = this.values.length - 1;
        int index = deleteIndex;
        while (true) {
            index = (index + 1) & mask;
            final WrapperThread value = this.values[index];
            if (value == null) {
                return;
            }
            final long key = this.keys[index];
            final int hash = Hashing.hash(key, mask);
            if ((index < hash && (hash <= deleteIndex || deleteIndex <= index))
                || (hash <= deleteIndex && deleteIndex <= index)) {
                this.keys[deleteIndex] = key;
                this.values[deleteIndex] = value;
                this.values[index] = null;
                deleteIndex = index;
            }
        }
    }
}