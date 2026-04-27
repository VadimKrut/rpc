package ru.pathcreator.pyc.rpc.core.codex;

import org.agrona.DirectBuffer;

@FunctionalInterface
public interface RpcResponseDecoder<T> {

    T decode(
            int offset,
            int length,
            DirectBuffer buffer
    );
}