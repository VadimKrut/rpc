package ru.pathcreator.pyc.rpc.bootstrap.fixture;

import java.util.UUID;

public record BootstrapEchoRequest(UUID requestId, String message, int amount) {
}
