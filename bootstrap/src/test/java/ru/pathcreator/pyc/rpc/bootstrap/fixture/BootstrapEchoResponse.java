package ru.pathcreator.pyc.rpc.bootstrap.fixture;

import java.util.UUID;

public record BootstrapEchoResponse(UUID requestId, String message, int amount) {
}
