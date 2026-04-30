package ru.pathcreator.pyc.rpc.bootstrap;

import ru.pathcreator.pyc.rpc.bootstrap.model.RpcAnnotatedMethod;
import ru.pathcreator.pyc.rpc.bootstrap.model.RpcAnnotatedService;
import ru.pathcreator.pyc.rpc.client.RpcClient;
import ru.pathcreator.pyc.rpc.client.RpcClientBuilder;
import ru.pathcreator.pyc.rpc.client.listener.RpcClientListener;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientInterceptor;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientRequestValidator;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientResponseValidator;
import ru.pathcreator.pyc.rpc.encryption.RpcPayloadEncryption;
import ru.pathcreator.pyc.rpc.observability.client.RpcClientMetrics;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class RpcBootstrapClientBuilder {

    private final RpcClientBuilder delegate;

    RpcBootstrapClientBuilder(
            final ru.pathcreator.pyc.rpc.core.RpcChannel channel
    ) {
        this.delegate = RpcClient.builder(channel);
    }

    public RpcBootstrapClientBuilder defaultTimeoutNs(
            final long defaultTimeoutNs
    ) {
        this.delegate.defaultTimeoutNs(defaultTimeoutNs);
        return this;
    }

    public RpcBootstrapClientBuilder payloadEncryption(
            final RpcPayloadEncryption payloadEncryption
    ) {
        this.delegate.payloadEncryption(payloadEncryption);
        return this;
    }

    public RpcBootstrapClientBuilder requestValidator(
            final RpcClientRequestValidator requestValidator
    ) {
        this.delegate.requestValidator(requestValidator);
        return this;
    }

    public RpcBootstrapClientBuilder responseValidator(
            final RpcClientResponseValidator responseValidator
    ) {
        this.delegate.responseValidator(responseValidator);
        return this;
    }

    public RpcBootstrapClientBuilder interceptor(
            final RpcClientInterceptor interceptor
    ) {
        this.delegate.interceptor(interceptor);
        return this;
    }

    public RpcBootstrapClientBuilder listener(
            final RpcClientListener listener
    ) {
        this.delegate.listener(listener);
        return this;
    }

    public RpcBootstrapClientBuilder metrics(
            final RpcClientMetrics metrics
    ) {
        metrics.attach(this.delegate);
        return this;
    }

    public RpcClient buildClient() {
        return this.delegate.build();
    }

    public <T> T buildService(
            final Class<T> serviceType
    ) {
        return bind(this.buildClient(), serviceType);
    }

    public static <T> T bind(
            final RpcClient client,
            final Class<T> serviceType
    ) {
        final RpcAnnotatedService<T> service = RpcBootstrap.introspect(serviceType);
        final InvocationHandler handler = new ClientInvocationHandler(client, service);
        final Object proxy = Proxy.newProxyInstance(
                serviceType.getClassLoader(),
                new Class<?>[]{serviceType},
                handler
        );
        return serviceType.cast(proxy);
    }

    private record ClientInvocationHandler(
            RpcClient client,
            RpcAnnotatedService<?> service
    ) implements InvocationHandler {

        @Override
        public Object invoke(
                final Object proxy,
                final Method method,
                final Object[] arguments
        ) {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, arguments);
            }
            final RpcAnnotatedMethod annotatedMethod = this.service.requireMethod(method);
            final Object request = arguments == null || arguments.length == 0 ? null : arguments[0];
            if (annotatedMethod.timeoutNs() > 0L) {
                return send(this.client, annotatedMethod, request, annotatedMethod.timeoutNs());
            }
            return send(this.client, annotatedMethod, request);
        }

        private static Object invokeObjectMethod(
                final Object proxy,
                final Method method,
                final Object[] arguments
        ) {
            return switch (method.getName()) {
                case "toString" -> proxy.getClass().getInterfaces()[0].getName() + "$RpcClientProxy";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw new IllegalStateException("Unsupported Object method: " + method);
            };
        }

        @SuppressWarnings("unchecked")
        private static Object send(
                final RpcClient client,
                final RpcAnnotatedMethod annotatedMethod,
                final Object request
        ) {
            return client.send((ru.pathcreator.pyc.rpc.contract.RpcMethodContract<Object, Object>) annotatedMethod.contract(), request);
        }

        @SuppressWarnings("unchecked")
        private static Object send(
                final RpcClient client,
                final RpcAnnotatedMethod annotatedMethod,
                final Object request,
                final long timeoutNs
        ) {
            return client.send(
                    (ru.pathcreator.pyc.rpc.contract.RpcMethodContract<Object, Object>) annotatedMethod.contract(),
                    request,
                    timeoutNs
            );
        }
    }
}