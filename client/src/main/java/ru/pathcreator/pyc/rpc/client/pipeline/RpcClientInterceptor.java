package ru.pathcreator.pyc.rpc.client.pipeline;

import ru.pathcreator.pyc.rpc.client.context.RpcClientContext;
import ru.pathcreator.pyc.rpc.client.response.RpcClientResult;

@FunctionalInterface
public interface RpcClientInterceptor {

    RpcClientResult<?> intercept(
            RpcClientContext context,
            Object request,
            RpcClientInvocation invocation
    );
}