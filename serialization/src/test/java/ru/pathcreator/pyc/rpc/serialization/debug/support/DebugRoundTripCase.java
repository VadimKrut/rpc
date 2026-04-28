package ru.pathcreator.pyc.rpc.serialization.debug.support;

public record DebugRoundTripCase<T>(
        Class<T> type,
        T sample
) {
}