package ru.pathcreator.pyc.rpc.core.serialization;

@FunctionalInterface
public interface RpcDtoHandler<Q, R> {

    R handle(Q request);
}