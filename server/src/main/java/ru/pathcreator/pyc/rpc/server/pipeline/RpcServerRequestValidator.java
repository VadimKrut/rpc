package ru.pathcreator.pyc.rpc.server.pipeline;

import ru.pathcreator.pyc.rpc.server.context.RpcServerContext;

@FunctionalInterface
public interface RpcServerRequestValidator {

    RpcServerRequestValidator NOOP = (context, request) -> {
    };

    void validate(
            RpcServerContext context,
            Object request
    ) throws Exception;
}
