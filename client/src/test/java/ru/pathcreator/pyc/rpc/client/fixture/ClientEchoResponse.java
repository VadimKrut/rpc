package ru.pathcreator.pyc.rpc.client.fixture;

import java.util.UUID;

public record ClientEchoResponse(
        UUID requestId,
        String message,
        int amount
) {
}