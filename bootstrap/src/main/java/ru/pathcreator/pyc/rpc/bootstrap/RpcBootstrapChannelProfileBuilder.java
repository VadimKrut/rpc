package ru.pathcreator.pyc.rpc.bootstrap;

import ru.pathcreator.pyc.rpc.client.listener.RpcClientListener;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientInterceptor;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientRequestValidator;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientResponseValidator;
import ru.pathcreator.pyc.rpc.core.RpcChannel;
import ru.pathcreator.pyc.rpc.encryption.RpcPayloadEncryption;
import ru.pathcreator.pyc.rpc.observability.client.RpcClientMetrics;
import ru.pathcreator.pyc.rpc.observability.server.RpcServerMetrics;
import ru.pathcreator.pyc.rpc.server.error.RpcServerExceptionMapper;
import ru.pathcreator.pyc.rpc.server.listener.RpcServerListener;
import ru.pathcreator.pyc.rpc.server.pipeline.RpcServerInterceptor;
import ru.pathcreator.pyc.rpc.server.pipeline.RpcServerRequestValidator;

import java.util.ArrayList;
import java.util.List;

public final class RpcBootstrapChannelProfileBuilder {

    private final RpcBootstrapEnvironmentBuilder parent;
    private final String name;
    private final RpcChannel channel;
    private long clientDefaultTimeoutNs = 5_000_000_000L;
    private RpcClientRequestValidator clientRequestValidator = RpcClientRequestValidator.NOOP;
    private RpcClientResponseValidator clientResponseValidator = RpcClientResponseValidator.NOOP;
    private final List<RpcClientInterceptor> clientInterceptors = new ArrayList<>();
    private RpcClientListener clientListener = RpcClientListener.NOOP;
    private RpcClientMetrics clientMetrics;
    private RpcPayloadEncryption clientPayloadEncryption = RpcPayloadEncryption.NOOP;
    private RpcServerExceptionMapper serverExceptionMapper = RpcServerExceptionMapper.DEFAULT;
    private RpcServerRequestValidator serverRequestValidator = RpcServerRequestValidator.NOOP;
    private final List<RpcServerInterceptor> serverInterceptors = new ArrayList<>();
    private RpcServerListener serverListener = RpcServerListener.NOOP;
    private RpcServerMetrics serverMetrics;
    private RpcPayloadEncryption serverPayloadEncryption = RpcPayloadEncryption.NOOP;

    RpcBootstrapChannelProfileBuilder(
            final RpcBootstrapEnvironmentBuilder parent,
            final String name,
            final RpcChannel channel
    ) {
        this.parent = parent;
        this.name = name;
        this.channel = channel;
    }

    public String name() {
        return this.name;
    }

    public RpcBootstrapEnvironmentBuilder done() {
        return this.parent;
    }

    public RpcBootstrapChannelProfileBuilder clientDefaultTimeoutNs(
            final long clientDefaultTimeoutNs
    ) {
        if (clientDefaultTimeoutNs <= 0L) {
            throw new IllegalArgumentException("clientDefaultTimeoutNs must be > 0");
        }
        this.clientDefaultTimeoutNs = clientDefaultTimeoutNs;
        return this;
    }

    public RpcBootstrapChannelProfileBuilder clientRequestValidator(
            final RpcClientRequestValidator clientRequestValidator
    ) {
        if (clientRequestValidator == null) {
            throw new IllegalArgumentException("clientRequestValidator must not be null");
        }
        this.clientRequestValidator = clientRequestValidator;
        return this;
    }

    public RpcBootstrapChannelProfileBuilder clientResponseValidator(
            final RpcClientResponseValidator clientResponseValidator
    ) {
        if (clientResponseValidator == null) {
            throw new IllegalArgumentException("clientResponseValidator must not be null");
        }
        this.clientResponseValidator = clientResponseValidator;
        return this;
    }

    public RpcBootstrapChannelProfileBuilder clientInterceptor(
            final RpcClientInterceptor clientInterceptor
    ) {
        if (clientInterceptor == null) {
            throw new IllegalArgumentException("clientInterceptor must not be null");
        }
        this.clientInterceptors.add(clientInterceptor);
        return this;
    }

    public RpcBootstrapChannelProfileBuilder clientListener(
            final RpcClientListener clientListener
    ) {
        if (clientListener == null) {
            throw new IllegalArgumentException("clientListener must not be null");
        }
        this.clientListener = clientListener;
        return this;
    }

    public RpcBootstrapChannelProfileBuilder clientMetrics(
            final RpcClientMetrics clientMetrics
    ) {
        if (clientMetrics == null) {
            throw new IllegalArgumentException("clientMetrics must not be null");
        }
        this.clientMetrics = clientMetrics;
        return this;
    }

    public RpcBootstrapChannelProfileBuilder clientPayloadEncryption(
            final RpcPayloadEncryption clientPayloadEncryption
    ) {
        if (clientPayloadEncryption == null) {
            throw new IllegalArgumentException("clientPayloadEncryption must not be null");
        }
        this.clientPayloadEncryption = clientPayloadEncryption;
        return this;
    }

    public RpcBootstrapChannelProfileBuilder serverExceptionMapper(
            final RpcServerExceptionMapper serverExceptionMapper
    ) {
        if (serverExceptionMapper == null) {
            throw new IllegalArgumentException("serverExceptionMapper must not be null");
        }
        this.serverExceptionMapper = serverExceptionMapper;
        return this;
    }

    public RpcBootstrapChannelProfileBuilder serverRequestValidator(
            final RpcServerRequestValidator serverRequestValidator
    ) {
        if (serverRequestValidator == null) {
            throw new IllegalArgumentException("serverRequestValidator must not be null");
        }
        this.serverRequestValidator = serverRequestValidator;
        return this;
    }

    public RpcBootstrapChannelProfileBuilder serverInterceptor(
            final RpcServerInterceptor serverInterceptor
    ) {
        if (serverInterceptor == null) {
            throw new IllegalArgumentException("serverInterceptor must not be null");
        }
        this.serverInterceptors.add(serverInterceptor);
        return this;
    }

    public RpcBootstrapChannelProfileBuilder serverListener(
            final RpcServerListener serverListener
    ) {
        if (serverListener == null) {
            throw new IllegalArgumentException("serverListener must not be null");
        }
        this.serverListener = serverListener;
        return this;
    }

    public RpcBootstrapChannelProfileBuilder serverMetrics(
            final RpcServerMetrics serverMetrics
    ) {
        if (serverMetrics == null) {
            throw new IllegalArgumentException("serverMetrics must not be null");
        }
        this.serverMetrics = serverMetrics;
        return this;
    }

    public RpcBootstrapChannelProfileBuilder serverPayloadEncryption(
            final RpcPayloadEncryption serverPayloadEncryption
    ) {
        if (serverPayloadEncryption == null) {
            throw new IllegalArgumentException("serverPayloadEncryption must not be null");
        }
        this.serverPayloadEncryption = serverPayloadEncryption;
        return this;
    }

    public RpcBootstrapChannelProfileBuilder payloadEncryption(
            final RpcPayloadEncryption payloadEncryption
    ) {
        return this.clientPayloadEncryption(payloadEncryption).serverPayloadEncryption(payloadEncryption);
    }

    RpcBootstrapEnvironment.ProfileConfig buildConfig() {
        return new RpcBootstrapEnvironment.ProfileConfig(
                this.name,
                this.channel,
                this.clientDefaultTimeoutNs,
                this.clientRequestValidator,
                this.clientResponseValidator,
                List.copyOf(this.clientInterceptors),
                this.clientListener,
                this.clientMetrics,
                this.clientPayloadEncryption,
                this.serverExceptionMapper,
                this.serverRequestValidator,
                List.copyOf(this.serverInterceptors),
                this.serverListener,
                this.serverMetrics,
                this.serverPayloadEncryption
        );
    }
}