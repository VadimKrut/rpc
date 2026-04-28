package ru.pathcreator.pyc.rpc.server.handler;

import ru.pathcreator.pyc.rpc.server.context.RpcServerContext;

@FunctionalInterface
public interface RpcServerContextHandler<Q, R> {

    R handle(RpcServerContext context, Q request) throws Exception;
}
