package ru.pathcreator.pyc.rpc.server;

import ru.pathcreator.pyc.rpc.core.RpcChannel;
import ru.pathcreator.pyc.rpc.server.error.RpcServerExceptionMapper;
import ru.pathcreator.pyc.rpc.server.listener.RpcServerListener;
import ru.pathcreator.pyc.rpc.server.pipeline.RpcServerInterceptor;
import ru.pathcreator.pyc.rpc.server.pipeline.RpcServerRequestValidator;

import java.util.ArrayList;
import java.util.List;

public final class RpcServerBuilder {

    private final RpcChannel channel;
    private final List<RpcServerInterceptor> interceptors = new ArrayList<>();
    private RpcServerExceptionMapper exceptionMapper = RpcServerExceptionMapper.DEFAULT;
    private RpcServerRequestValidator requestValidator = RpcServerRequestValidator.NOOP;
    private RpcServerListener listener = RpcServerListener.NOOP;

    RpcServerBuilder(final RpcChannel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("channel must not be null");
        }
        this.channel = channel;
    }

    public RpcServerBuilder exceptionMapper(
            final RpcServerExceptionMapper exceptionMapper
    ) {
        if (exceptionMapper == null) {
            throw new IllegalArgumentException("exceptionMapper must not be null");
        }
        this.exceptionMapper = exceptionMapper;
        return this;
    }

    public RpcServerBuilder requestValidator(
            final RpcServerRequestValidator requestValidator
    ) {
        if (requestValidator == null) {
            throw new IllegalArgumentException("requestValidator must not be null");
        }
        this.requestValidator = requestValidator;
        return this;
    }

    public RpcServerBuilder interceptor(
            final RpcServerInterceptor interceptor
    ) {
        if (interceptor == null) {
            throw new IllegalArgumentException("interceptor must not be null");
        }
        this.interceptors.add(interceptor);
        return this;
    }

    public RpcServerBuilder listener(
            final RpcServerListener listener
    ) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        this.listener = listener;
        return this;
    }

    public RpcServer build() {
        return new RpcServer(
                this.channel,
                List.copyOf(this.interceptors),
                this.exceptionMapper,
                this.requestValidator,
                this.listener
        );
    }
}