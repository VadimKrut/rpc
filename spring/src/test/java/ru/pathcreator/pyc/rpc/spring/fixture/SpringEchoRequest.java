package ru.pathcreator.pyc.rpc.spring.fixture;

import java.util.UUID;

public record SpringEchoRequest(UUID requestId, String message) {
}
