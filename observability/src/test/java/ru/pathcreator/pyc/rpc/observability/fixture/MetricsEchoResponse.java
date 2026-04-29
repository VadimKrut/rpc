package ru.pathcreator.pyc.rpc.observability.fixture;

import java.util.UUID;

public record MetricsEchoResponse(
        UUID requestId,
        String message,
        int amount
) {
}
