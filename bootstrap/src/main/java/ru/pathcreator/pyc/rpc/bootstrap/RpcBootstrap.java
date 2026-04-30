package ru.pathcreator.pyc.rpc.bootstrap;

import ru.pathcreator.pyc.rpc.bootstrap.introspection.RpcBootstrapIntrospector;
import ru.pathcreator.pyc.rpc.bootstrap.model.RpcAnnotatedService;
import ru.pathcreator.pyc.rpc.core.RpcChannel;

public final class RpcBootstrap {

    private RpcBootstrap() {
    }

    public static RpcBootstrapClientBuilder client(
            final RpcChannel channel
    ) {
        return new RpcBootstrapClientBuilder(channel);
    }

    public static RpcBootstrapServerBuilder server(
            final RpcChannel channel
    ) {
        return new RpcBootstrapServerBuilder(channel);
    }

    public static RpcBootstrapEnvironmentBuilder environment() {
        return new RpcBootstrapEnvironmentBuilder();
    }

    public static <T> RpcAnnotatedService<T> introspect(
            final Class<T> serviceType
    ) {
        return RpcBootstrapIntrospector.introspect(serviceType);
    }
}