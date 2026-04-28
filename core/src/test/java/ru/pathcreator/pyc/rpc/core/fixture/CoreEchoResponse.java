package ru.pathcreator.pyc.rpc.core.fixture;

import java.util.UUID;

public record CoreEchoResponse(
        UUID requestId,
        String message,
        int amount
) {
}
