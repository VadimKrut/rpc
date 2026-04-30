package ru.pathcreator.pyc.rpc.spring;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import ru.pathcreator.pyc.rpc.bootstrap.RpcBootstrapEnvironment;
import ru.pathcreator.pyc.rpc.spring.annotation.RpcEndpoint;

import java.util.Map;

final class RpcSpringServiceExporter implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;
    private final RpcBootstrapEnvironment environment;

    RpcSpringServiceExporter(
            final ApplicationContext applicationContext,
            final RpcBootstrapEnvironment environment
    ) {
        this.applicationContext = applicationContext;
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        final Map<String, Object> endpoints = this.applicationContext.getBeansWithAnnotation(RpcEndpoint.class);
        for (final Object endpointBean : endpoints.values()) {
            this.export(endpointBean);
        }
    }

    private void export(
            final Object endpointBean
    ) {
        final Class<?> beanType = AopUtils.getTargetClass(endpointBean);
        final RpcEndpoint endpoint = beanType.getAnnotation(RpcEndpoint.class);
        if (endpoint == null || endpoint.service() == Void.class) {
            this.environment.service(endpointBean);
            return;
        }
        exportResolved(endpoint.service(), endpointBean);
    }

    @SuppressWarnings("unchecked")
    private <T> void exportResolved(
            final Class<?> serviceType,
            final Object endpointBean
    ) {
        this.environment.service((Class<T>) serviceType, (T) endpointBean);
    }
}