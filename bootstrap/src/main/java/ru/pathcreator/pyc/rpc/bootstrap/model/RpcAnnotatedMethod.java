package ru.pathcreator.pyc.rpc.bootstrap.model;

import ru.pathcreator.pyc.rpc.contract.RpcMethodContract;

import java.lang.reflect.Method;

public record RpcAnnotatedMethod(
        Method javaMethod,
        RpcMethodContract<?, ?> contract,
        long timeoutNs
) {
}