package ru.pathcreator.pyc.rpc.server.fixture;

import java.util.UUID;

public record ServerEchoResponse(
        UUID requestId,
        String message,
        int amount
) {
}