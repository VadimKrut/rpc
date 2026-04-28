package ru.pathcreator.pyc.rpc.client.pipeline;

import ru.pathcreator.pyc.rpc.client.context.RpcClientContext;

@FunctionalInterface
public interface RpcClientRequestValidator {

    RpcClientRequestValidator NOOP = (context, request) -> {
    };

    void validate(
            RpcClientContext context,
            Object request
    );
}