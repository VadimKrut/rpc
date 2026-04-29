package ru.pathcreator.pyc.rpc.contract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RpcServiceContract {

    private final String name;
    private final List<RpcMethodContract<?, ?>> methods;
    private final Map<String, RpcMethodContract<?, ?>> methodsByName;
    private final Map<Integer, RpcMethodContract<?, ?>> methodsByRequestMessageTypeId;

    private RpcServiceContract(
            final String name,
            final List<RpcMethodContract<?, ?>> methods
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (methods == null || methods.isEmpty()) {
            throw new IllegalArgumentException("methods must not be empty");
        }
        this.name = name;
        this.methods = List.copyOf(methods);
        this.methodsByName = new LinkedHashMap<>(methods.size());
        this.methodsByRequestMessageTypeId = new LinkedHashMap<>(methods.size());
        final Map<Integer, RpcMethodContract<?, ?>> methodsByResponseMessageTypeId = new LinkedHashMap<>(methods.size());
        for (final RpcMethodContract<?, ?> method : this.methods) {
            if (this.methodsByName.putIfAbsent(method.name(), method) != null) {
                throw new IllegalArgumentException(
                        "duplicate method name in service '" + name + "': " + method.name()
                );
            }
            if (this.methodsByRequestMessageTypeId.putIfAbsent(method.requestMessageTypeId(), method) != null) {
                throw new IllegalArgumentException(
                        "duplicate requestMessageTypeId in service '" + name + "': " + method.requestMessageTypeId()
                );
            }
            if (methodsByResponseMessageTypeId.putIfAbsent(method.responseMessageTypeId(), method) != null) {
                throw new IllegalArgumentException(
                        "duplicate responseMessageTypeId in service '" + name + "': " + method.responseMessageTypeId()
                );
            }
        }
    }

    public static Builder builder(
            final String name
    ) {
        return new Builder(name);
    }

    public static RpcServiceContract of(
            final String name,
            final RpcMethodContract<?, ?>... methods
    ) {
        return builder(name).methods(methods).build();
    }

    public String name() {
        return this.name;
    }

    public List<RpcMethodContract<?, ?>> methods() {
        return this.methods;
    }

    public RpcMethodContract<?, ?> findMethod(
            final String methodName
    ) {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        return this.methodsByName.get(methodName);
    }

    public RpcMethodContract<?, ?> findMethod(
            final int requestMessageTypeId
    ) {
        if (requestMessageTypeId <= 0) {
            throw new IllegalArgumentException("requestMessageTypeId must be > 0");
        }
        return this.methodsByRequestMessageTypeId.get(requestMessageTypeId);
    }

    public <Q, R> RpcMethodContract<Q, R> requireMethod(
            final String methodName,
            final Class<Q> requestType,
            final Class<R> responseType
    ) {
        final RpcMethodContract<?, ?> method = this.findMethod(methodName);
        if (method == null) {
            throw new IllegalArgumentException(
                    "method '" + methodName + "' is not registered in service '" + this.name + "'"
            );
        }
        return cast(method, requestType, responseType);
    }

    public <Q, R> RpcMethodContract<Q, R> requireMethod(
            final int requestMessageTypeId,
            final Class<Q> requestType,
            final Class<R> responseType
    ) {
        final RpcMethodContract<?, ?> method = this.findMethod(requestMessageTypeId);
        if (method == null) {
            throw new IllegalArgumentException(
                    "requestMessageTypeId " + requestMessageTypeId + " is not registered in service '" + this.name + "'"
            );
        }
        return cast(method, requestType, responseType);
    }

    @SuppressWarnings("unchecked")
    private static <Q, R> RpcMethodContract<Q, R> cast(
            final RpcMethodContract<?, ?> method,
            final Class<Q> requestType,
            final Class<R> responseType
    ) {
        if (requestType == null) {
            throw new IllegalArgumentException("requestType must not be null");
        }
        if (responseType == null) {
            throw new IllegalArgumentException("responseType must not be null");
        }
        if (method.requestType() != requestType) {
            throw new IllegalArgumentException(
                    "method '" + method.name() + "' request type mismatch: expected "
                    + method.requestType().getName() + ", got " + requestType.getName()
            );
        }
        if (method.responseType() != responseType) {
            throw new IllegalArgumentException(
                    "method '" + method.name() + "' response type mismatch: expected "
                    + method.responseType().getName() + ", got " + responseType.getName()
            );
        }
        return (RpcMethodContract<Q, R>) method;
    }

    public static final class Builder {

        private final String name;
        private final List<RpcMethodContract<?, ?>> methods = new ArrayList<>();

        private Builder(
                final String name
        ) {
            this.name = name;
        }

        public Builder method(
                final RpcMethodContract<?, ?> method
        ) {
            if (method == null) {
                throw new IllegalArgumentException("method must not be null");
            }
            this.methods.add(method);
            return this;
        }

        public Builder methods(
                final RpcMethodContract<?, ?>... methods
        ) {
            if (methods == null) {
                throw new IllegalArgumentException("methods must not be null");
            }
            for (final RpcMethodContract<?, ?> method : methods) {
                this.method(method);
            }
            return this;
        }

        public <Q, R> Builder method(
                final String methodName,
                final Class<Q> requestType,
                final Class<R> responseType,
                final int requestMessageTypeId,
                final int responseMessageTypeId
        ) {
            return this.method(
                    RpcMethodContract.of(
                            methodName,
                            requestType,
                            responseType,
                            requestMessageTypeId,
                            responseMessageTypeId
                    )
            );
        }

        public RpcServiceContract build() {
            return new RpcServiceContract(this.name, this.methods);
        }
    }
}