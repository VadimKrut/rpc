package ru.pathcreator.pyc.rpc.serialization.support.io;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import java.nio.ByteOrder;

public final class BinaryIo {

    private BinaryIo() {
    }

    public static void putByte(final MutableDirectBuffer buffer, final int offset, final byte value) {
        buffer.putByte(offset, value);
    }

    public static byte getByte(final DirectBuffer buffer, final int offset) {
        return buffer.getByte(offset);
    }

    public static void putShortLE(final MutableDirectBuffer buffer, final int offset, final short value) {
        buffer.putShort(offset, value, ByteOrder.LITTLE_ENDIAN);
    }

    public static short getShortLE(final DirectBuffer buffer, final int offset) {
        return buffer.getShort(offset, ByteOrder.LITTLE_ENDIAN);
    }

    public static void putIntLE(final MutableDirectBuffer buffer, final int offset, final int value) {
        buffer.putInt(offset, value, ByteOrder.LITTLE_ENDIAN);
    }

    public static int getIntLE(final DirectBuffer buffer, final int offset) {
        return buffer.getInt(offset, ByteOrder.LITTLE_ENDIAN);
    }

    public static void putLongLE(final MutableDirectBuffer buffer, final int offset, final long value) {
        buffer.putLong(offset, value, ByteOrder.LITTLE_ENDIAN);
    }

    public static long getLongLE(final DirectBuffer buffer, final int offset) {
        return buffer.getLong(offset, ByteOrder.LITTLE_ENDIAN);
    }

    public static void putFloatLE(final MutableDirectBuffer buffer, final int offset, final float value) {
        putIntLE(buffer, offset, Float.floatToRawIntBits(value));
    }

    public static float getFloatLE(final DirectBuffer buffer, final int offset) {
        return Float.intBitsToFloat(getIntLE(buffer, offset));
    }

    public static void putDoubleLE(final MutableDirectBuffer buffer, final int offset, final double value) {
        putLongLE(buffer, offset, Double.doubleToRawLongBits(value));
    }

    public static double getDoubleLE(final DirectBuffer buffer, final int offset) {
        return Double.longBitsToDouble(getLongLE(buffer, offset));
    }

    public static void putBytes(final MutableDirectBuffer destination, final int offset, final byte[] source) {
        destination.putBytes(offset, source);
    }

    public static byte[] getBytes(final DirectBuffer source, final int offset, final int length) {
        final byte[] copy = new byte[length];
        source.getBytes(offset, copy);
        return copy;
    }
}