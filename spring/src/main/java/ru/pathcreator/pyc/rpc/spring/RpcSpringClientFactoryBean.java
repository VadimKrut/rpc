package ru.pathcreator.pyc.rpc.spring;

import org.springframework.beans.factory.FactoryBean;

public final class RpcSpringClientFactoryBean<T> implements FactoryBean<T> {

    private final RpcSpringClientFactory clientFactory;
    private final Class<T> serviceType;

    public RpcSpringClientFactoryBean(
            final RpcSpringClientFactory clientFactory,
            final Class<T> serviceType
    ) {
        if (clientFactory == null) {
            throw new IllegalArgumentException("clientFactory must not be null");
        }
        if (serviceType == null) {
            throw new IllegalArgumentException("serviceType must not be null");
        }
        this.clientFactory = clientFactory;
        this.serviceType = serviceType;
    }

    @Override
    public T getObject() {
        return this.clientFactory.get(this.serviceType);
    }

    @Override
    public Class<?> getObjectType() {
        return this.serviceType;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}