package ru.pathcreator.pyc.rpc.admin.fixture;

import java.util.UUID;

public record AdminEchoRequest(
        UUID requestId,
        String message,
        int amount
) {
}