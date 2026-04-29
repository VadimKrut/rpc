package ru.pathcreator.pyc.rpc.observability.fixture;

import java.util.UUID;

public record MetricsEchoRequest(
        UUID requestId,
        String message,
        int amount
) {
}
