package ru.pathcreator.pyc.rpc.spring.fixture;

import java.util.UUID;

public record SpringEchoResponse(UUID requestId, String message) {
}
