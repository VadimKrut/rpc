package ru.pathcreator.pyc.rpc.server.fixture;

import java.util.UUID;

public record ServerEchoRequest(
        UUID requestId,
        String message,
        int amount
) {
}