package ru.pathcreator.pyc.rpc.admin.fixture;

import java.util.UUID;

public record AdminEchoResponse(
        UUID requestId,
        String message,
        int amount
) {
}