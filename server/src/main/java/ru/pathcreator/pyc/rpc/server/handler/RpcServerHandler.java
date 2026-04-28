package ru.pathcreator.pyc.rpc.server.handler;

@FunctionalInterface
public interface RpcServerHandler<Q, R> {

    R handle(Q request) throws Exception;
}
