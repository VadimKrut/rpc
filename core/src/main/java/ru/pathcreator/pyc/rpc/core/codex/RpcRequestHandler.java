package ru.pathcreator.pyc.rpc.core.codex;

import org.agrona.DirectBuffer;

@FunctionalInterface
public interface RpcRequestHandler {

    void handle(
            int offset,
            int length,
            long correlationId,
            DirectBuffer buffer
    );
}