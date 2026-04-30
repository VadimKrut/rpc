package ru.pathcreator.pyc.rpc.spring;

import ru.pathcreator.pyc.rpc.bootstrap.RpcBootstrapEnvironment;

public final class RpcSpringClientFactory {

    private final RpcBootstrapEnvironment environment;

    public RpcSpringClientFactory(
            final RpcBootstrapEnvironment environment
    ) {
        if (environment == null) {
            throw new IllegalArgumentException("environment must not be null");
        }
        this.environment = environment;
    }

    public <T> T get(
            final Class<T> serviceType
    ) {
        return this.environment.client(serviceType);
    }
}