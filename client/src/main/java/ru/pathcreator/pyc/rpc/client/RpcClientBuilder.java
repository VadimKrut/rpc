package ru.pathcreator.pyc.rpc.client;

import ru.pathcreator.pyc.rpc.client.listener.RpcClientListener;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientInterceptor;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientRequestValidator;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientResponseValidator;
import ru.pathcreator.pyc.rpc.core.RpcChannel;

import java.util.ArrayList;
import java.util.List;

public final class RpcClientBuilder {

    private final RpcChannel channel;
    private long defaultTimeoutNs = 5_000_000_000L;
    private RpcClientListener listener = RpcClientListener.NOOP;
    private final List<RpcClientInterceptor> interceptors = new ArrayList<>();
    private RpcClientRequestValidator requestValidator = RpcClientRequestValidator.NOOP;
    private RpcClientResponseValidator responseValidator = RpcClientResponseValidator.NOOP;

    RpcClientBuilder(final RpcChannel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("channel must not be null");
        }
        this.channel = channel;
    }

    public RpcClientBuilder defaultTimeoutNs(
            final long defaultTimeoutNs
    ) {
        if (defaultTimeoutNs <= 0L) {
            throw new IllegalArgumentException("defaultTimeoutNs must be > 0");
        }
        this.defaultTimeoutNs = defaultTimeoutNs;
        return this;
    }

    public RpcClientBuilder requestValidator(
            final RpcClientRequestValidator requestValidator
    ) {
        if (requestValidator == null) {
            throw new IllegalArgumentException("requestValidator must not be null");
        }
        this.requestValidator = requestValidator;
        return this;
    }

    public RpcClientBuilder responseValidator(
            final RpcClientResponseValidator responseValidator
    ) {
        if (responseValidator == null) {
            throw new IllegalArgumentException("responseValidator must not be null");
        }
        this.responseValidator = responseValidator;
        return this;
    }

    public RpcClientBuilder interceptor(
            final RpcClientInterceptor interceptor
    ) {
        if (interceptor == null) {
            throw new IllegalArgumentException("interceptor must not be null");
        }
        this.interceptors.add(interceptor);
        return this;
    }

    public RpcClientBuilder listener(
            final RpcClientListener listener
    ) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        this.listener = listener;
        return this;
    }

    public RpcClient build() {
        return new RpcClient(
                this.channel,
                this.defaultTimeoutNs,
                this.requestValidator,
                this.responseValidator,
                List.copyOf(this.interceptors),
                this.listener
        );
    }
}