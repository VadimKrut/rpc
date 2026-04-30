package ru.pathcreator.pyc.rpc.bootstrap.introspection;

import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcMethod;
import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcService;
import ru.pathcreator.pyc.rpc.bootstrap.model.RpcAnnotatedMethod;
import ru.pathcreator.pyc.rpc.bootstrap.model.RpcAnnotatedService;
import ru.pathcreator.pyc.rpc.contract.RpcMethodContract;
import ru.pathcreator.pyc.rpc.contract.RpcServiceContract;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RpcBootstrapIntrospector {

    private RpcBootstrapIntrospector() {
    }

    public static <T> RpcAnnotatedService<T> introspect(
            final Class<T> serviceType
    ) {
        if (serviceType == null) {
            throw new IllegalArgumentException("serviceType must not be null");
        }
        if (!serviceType.isInterface()) {
            throw new IllegalArgumentException("RPC bootstrap service must be an interface: " + serviceType.getName());
        }
        final RpcService serviceAnnotation = serviceType.getAnnotation(RpcService.class);
        if (serviceAnnotation == null) {
            throw new IllegalArgumentException("Missing @RpcService on " + serviceType.getName());
        }
        final String serviceName = serviceAnnotation.value().isBlank()
                ? serviceType.getName()
                : serviceAnnotation.value();
        final String channelName = serviceAnnotation.channel().isBlank()
                ? "default"
                : serviceAnnotation.channel();
        final List<Method> candidates = new ArrayList<>();
        for (final Method method : serviceType.getMethods()) {
            if (method.getDeclaringClass() == Object.class || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.isDefault()) {
                throw new IllegalArgumentException("Default methods are not supported in RPC bootstrap service: " + method);
            }
            candidates.add(method);
        }
        candidates.sort(Comparator.comparing(Method::toGenericString));
        final List<RpcAnnotatedMethod> methods = new ArrayList<>(candidates.size());
        final RpcServiceContract.Builder contract = RpcServiceContract.builder(serviceName);
        for (final Method javaMethod : candidates) {
            final RpcMethod methodAnnotation = javaMethod.getAnnotation(RpcMethod.class);
            if (methodAnnotation == null) {
                throw new IllegalArgumentException("Missing @RpcMethod on " + javaMethod);
            }
            if (javaMethod.getParameterCount() != 1) {
                throw new IllegalArgumentException("RPC bootstrap method must have exactly one parameter: " + javaMethod);
            }
            if (javaMethod.getReturnType() == Void.TYPE) {
                throw new IllegalArgumentException("RPC bootstrap method must not return void: " + javaMethod);
            }
            final String methodName = methodAnnotation.name().isBlank()
                    ? serviceName + "." + javaMethod.getName()
                    : methodAnnotation.name();
            @SuppressWarnings("unchecked") final Class<Object> requestType = (Class<Object>) javaMethod.getParameterTypes()[0];
            @SuppressWarnings("unchecked") final Class<Object> responseType = (Class<Object>) javaMethod.getReturnType();
            final RpcMethodContract<Object, Object> methodContract = RpcMethodContract.of(
                    methodName,
                    requestType,
                    responseType,
                    methodAnnotation.requestMessageTypeId(),
                    methodAnnotation.responseMessageTypeId()
            );
            contract.method(methodContract);
            methods.add(new RpcAnnotatedMethod(javaMethod, methodContract, methodAnnotation.timeoutNs()));
        }
        return new RpcAnnotatedService<>(
                serviceType,
                channelName,
                contract.build(),
                methods
        );
    }
}