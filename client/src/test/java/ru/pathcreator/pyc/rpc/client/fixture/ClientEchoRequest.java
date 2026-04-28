package ru.pathcreator.pyc.rpc.client.fixture;

import java.util.UUID;

public record ClientEchoRequest(
        UUID requestId,
        String message,
        int amount
) {
}