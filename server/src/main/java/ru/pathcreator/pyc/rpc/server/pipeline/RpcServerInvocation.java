package ru.pathcreator.pyc.rpc.server.pipeline;

import ru.pathcreator.pyc.rpc.server.context.RpcServerContext;

@FunctionalInterface
public interface RpcServerInvocation {

    Object proceed(
            RpcServerContext context,
            Object request
    ) throws Exception;
}
