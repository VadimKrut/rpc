package ru.pathcreator.pyc.rpc.bootstrap;

import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcService;
import ru.pathcreator.pyc.rpc.bootstrap.model.RpcAnnotatedMethod;
import ru.pathcreator.pyc.rpc.bootstrap.model.RpcAnnotatedService;
import ru.pathcreator.pyc.rpc.encryption.RpcPayloadEncryption;
import ru.pathcreator.pyc.rpc.observability.server.RpcServerMetrics;
import ru.pathcreator.pyc.rpc.server.RpcServer;
import ru.pathcreator.pyc.rpc.server.RpcServerBuilder;
import ru.pathcreator.pyc.rpc.server.error.RpcServerExceptionMapper;
import ru.pathcreator.pyc.rpc.server.listener.RpcServerListener;
import ru.pathcreator.pyc.rpc.server.pipeline.RpcServerInterceptor;
import ru.pathcreator.pyc.rpc.server.pipeline.RpcServerRequestValidator;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public final class RpcBootstrapServerBuilder {

    private final RpcServerBuilder delegate;
    private final List<ServiceRegistration<?>> registrations = new ArrayList<>();

    RpcBootstrapServerBuilder(
            final ru.pathcreator.pyc.rpc.core.RpcChannel channel
    ) {
        this.delegate = RpcServer.builder(channel);
    }

    public RpcBootstrapServerBuilder payloadEncryption(
            final RpcPayloadEncryption payloadEncryption
    ) {
        this.delegate.payloadEncryption(payloadEncryption);
        return this;
    }

    public RpcBootstrapServerBuilder exceptionMapper(
            final RpcServerExceptionMapper exceptionMapper
    ) {
        this.delegate.exceptionMapper(exceptionMapper);
        return this;
    }

    public RpcBootstrapServerBuilder requestValidator(
            final RpcServerRequestValidator requestValidator
    ) {
        this.delegate.requestValidator(requestValidator);
        return this;
    }

    public RpcBootstrapServerBuilder interceptor(
            final RpcServerInterceptor interceptor
    ) {
        this.delegate.interceptor(interceptor);
        return this;
    }

    public RpcBootstrapServerBuilder listener(
            final RpcServerListener listener
    ) {
        this.delegate.listener(listener);
        return this;
    }

    public RpcBootstrapServerBuilder metrics(
            final RpcServerMetrics metrics
    ) {
        metrics.attach(this.delegate);
        return this;
    }

    public <T> RpcBootstrapServerBuilder service(
            final Class<T> serviceType,
            final T implementation
    ) {
        if (implementation == null) {
            throw new IllegalArgumentException("implementation must not be null");
        }
        this.registrations.add(new ServiceRegistration<>(serviceType, implementation));
        return this;
    }

    public RpcBootstrapServerBuilder service(
            final Object implementation
    ) {
        if (implementation == null) {
            throw new IllegalArgumentException("implementation must not be null");
        }
        final Class<?> serviceType = resolveAnnotatedServiceType(implementation.getClass());
        this.registrations.add(ServiceRegistration.of(serviceType, implementation));
        return this;
    }

    public RpcServer build() {
        final RpcServer server = this.delegate.build();
        for (final ServiceRegistration<?> registration : this.registrations) {
            registerResolved(server, registration.serviceType(), registration.implementation());
        }
        return server;
    }

    public static <T> void register(
            final RpcServer server,
            final Class<T> serviceType,
            final T implementation
    ) {
        final RpcAnnotatedService<T> service = RpcBootstrap.introspect(serviceType);
        for (final RpcAnnotatedMethod method : service.methods()) {
            registerMethod(server, method, implementation);
        }
    }

    public static void register(
            final RpcServer server,
            final Object implementation
    ) {
        if (implementation == null) {
            throw new IllegalArgumentException("implementation must not be null");
        }
        registerResolved(server, resolveAnnotatedServiceType(implementation.getClass()), implementation);
    }

    private static Object invoke(
            final Object implementation,
            final RpcAnnotatedMethod method,
            final Object request
    ) throws Exception {
        try {
            return method.javaMethod().invoke(implementation, request);
        } catch (final InvocationTargetException error) {
            final Throwable target = error.getTargetException();
            if (target instanceof Exception checked) {
                throw checked;
            }
            if (target instanceof Error fatal) {
                throw fatal;
            }
            throw new IllegalStateException("Unexpected invocation failure", target);
        } catch (final IllegalAccessException error) {
            throw new IllegalStateException("Failed to invoke bootstrap RPC method", error);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> resolveAnnotatedServiceType(
            final Class<?> implementationType
    ) {
        final List<Class<?>> matches = new ArrayList<>();
        collectAnnotatedInterfaces(implementationType, matches);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    "implementation does not expose an interface annotated with @RpcService: " + implementationType.getName()
            );
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                    "implementation exposes multiple @RpcService interfaces, specify the service type explicitly: "
                    + implementationType.getName()
            );
        }
        return (Class<T>) matches.getFirst();
    }

    @SuppressWarnings("unchecked")
    private static <T> void registerResolved(
            final RpcServer server,
            final Class<?> serviceType,
            final Object implementation
    ) {
        register(server, (Class<T>) serviceType, (T) implementation);
    }

    @SuppressWarnings("unchecked")
    private static <T> void registerMethod(
            final RpcServer server,
            final RpcAnnotatedMethod method,
            final T implementation
    ) {
        server.register(
                (ru.pathcreator.pyc.rpc.contract.RpcMethodContract<Object, Object>) method.contract(),
                request -> invoke(implementation, method, request)
        );
    }

    private static void collectAnnotatedInterfaces(
            final Class<?> implementationType,
            final List<Class<?>> matches
    ) {
        if (implementationType == null || implementationType == Object.class) {
            return;
        }
        for (final Class<?> candidate : implementationType.getInterfaces()) {
            if (candidate.isAnnotationPresent(RpcService.class) && !matches.contains(candidate)) {
                matches.add(candidate);
            }
            collectAnnotatedInterfaces(candidate, matches);
        }
        collectAnnotatedInterfaces(implementationType.getSuperclass(), matches);
    }

    private record ServiceRegistration<T>(
            Class<T> serviceType,
            T implementation
    ) {

        @SuppressWarnings("unchecked")
        private static ServiceRegistration<?> of(
                final Class<?> serviceType,
                final Object implementation
        ) {
            return new ServiceRegistration<>((Class<Object>) serviceType, implementation);
        }
    }
}