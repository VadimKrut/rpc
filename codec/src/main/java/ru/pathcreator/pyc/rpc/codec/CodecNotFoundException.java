package ru.pathcreator.pyc.rpc.codec;

public final class CodecNotFoundException extends RuntimeException {

    public CodecNotFoundException(final Class<?> type) {
        super("No generated codec registered for " + type.getName());
    }
}