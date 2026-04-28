package ru.pathcreator.pyc.rpc.core.fixture;

import java.util.UUID;

public record CoreEchoRequest(
        UUID requestId,
        String message,
        int amount
) {
}
