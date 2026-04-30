package ru.pathcreator.pyc.rpc.bootstrap.model;

import ru.pathcreator.pyc.rpc.contract.RpcServiceContract;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RpcAnnotatedService<T> {

    private final Class<T> serviceType;
    private final String channelName;
    private final RpcServiceContract contract;
    private final List<RpcAnnotatedMethod> methods;
    private final Map<java.lang.reflect.Method, RpcAnnotatedMethod> methodsByJavaMethod;

    public RpcAnnotatedService(
            final Class<T> serviceType,
            final String channelName,
            final RpcServiceContract contract,
            final List<RpcAnnotatedMethod> methods
    ) {
        if (serviceType == null) {
            throw new IllegalArgumentException("serviceType must not be null");
        }
        if (channelName == null || channelName.isBlank()) {
            throw new IllegalArgumentException("channelName must not be blank");
        }
        if (contract == null) {
            throw new IllegalArgumentException("contract must not be null");
        }
        if (methods == null || methods.isEmpty()) {
            throw new IllegalArgumentException("methods must not be empty");
        }
        this.serviceType = serviceType;
        this.channelName = channelName;
        this.contract = contract;
        this.methods = List.copyOf(methods);
        this.methodsByJavaMethod = new LinkedHashMap<>(methods.size());
        for (final RpcAnnotatedMethod method : this.methods) {
            this.methodsByJavaMethod.put(method.javaMethod(), method);
        }
    }

    public Class<T> serviceType() {
        return this.serviceType;
    }

    public RpcServiceContract contract() {
        return this.contract;
    }

    public String channelName() {
        return this.channelName;
    }

    public List<RpcAnnotatedMethod> methods() {
        return this.methods;
    }

    public RpcAnnotatedMethod requireMethod(
            final java.lang.reflect.Method javaMethod
    ) {
        final RpcAnnotatedMethod method = this.methodsByJavaMethod.get(javaMethod);
        if (method == null) {
            throw new IllegalArgumentException(
                    "method '" + javaMethod.getName() + "' is not registered in service '" + this.contract.name() + "'"
            );
        }
        return method;
    }
}