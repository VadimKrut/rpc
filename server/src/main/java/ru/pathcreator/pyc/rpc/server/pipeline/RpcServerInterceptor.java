package ru.pathcreator.pyc.rpc.server.pipeline;

import ru.pathcreator.pyc.rpc.server.context.RpcServerContext;

@FunctionalInterface
public interface RpcServerInterceptor {

    Object intercept(
            RpcServerContext context,
            Object request,
            RpcServerInvocation invocation
    ) throws Exception;
}
