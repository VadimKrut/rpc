package ru.pathcreator.pyc.rpc.client;

import org.agrona.ExpandableArrayBuffer;
import ru.pathcreator.pyc.rpc.client.call.RpcClientCall;
import ru.pathcreator.pyc.rpc.client.context.RpcClientContext;
import ru.pathcreator.pyc.rpc.client.method.RpcClientMethod;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientInterceptor;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientInvocation;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientRequestValidator;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientResponseValidator;
import ru.pathcreator.pyc.rpc.client.response.RpcClientResult;
import ru.pathcreator.pyc.rpc.codec.SerializationCodec;
import ru.pathcreator.pyc.rpc.core.RpcChannel;
import ru.pathcreator.pyc.rpc.core.codex.RpcResponseFrame;
import ru.pathcreator.pyc.rpc.core.serialization.RpcCodecSupport;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class RpcClient {

    private final RpcChannel channel;
    private final long defaultTimeoutNs;
    private final RpcClientRequestValidator requestValidator;
    private final RpcClientResponseValidator responseValidator;
    private final List<RpcClientInterceptor> interceptors;
    private final ConcurrentHashMap<RpcClientMethod<?, ?>, ClientMethodBinding<?, ?>> bindings = new ConcurrentHashMap<>();
    private final ThreadLocal<ExpandableArrayBuffer> requestBuffers =
            ThreadLocal.withInitial(() -> new ExpandableArrayBuffer(512));

    RpcClient(
            final RpcChannel channel,
            final long defaultTimeoutNs,
            final RpcClientRequestValidator requestValidator,
            final RpcClientResponseValidator responseValidator,
            final List<RpcClientInterceptor> interceptors
    ) {
        this.channel = channel;
        this.defaultTimeoutNs = defaultTimeoutNs;
        this.requestValidator = requestValidator;
        this.responseValidator = responseValidator;
        this.interceptors = interceptors;
    }

    public static RpcClientBuilder builder(
            final RpcChannel channel
    ) {
        return new RpcClientBuilder(channel);
    }

    public <Q, R> RpcClientCall<Q, R> bind(
            final RpcClientMethod<Q, R> method
    ) {
        if (method == null) {
            throw new IllegalArgumentException("method must not be null");
        }
        return new RpcClientCall<>(
                method,
                this.defaultTimeoutNs,
                (context, request) -> this.bindingFor(method).exchange(context, request)
        );
    }

    public <Q, R> R send(
            final RpcClientMethod<Q, R> method,
            final Q request
    ) {
        return this.bind(method).send(request);
    }

    public <Q, R> R send(
            final RpcClientMethod<Q, R> method,
            final Q request,
            final long timeoutNs
    ) {
        return this.bind(method).send(request, timeoutNs);
    }

    public <Q, R> RpcClientResult<R> exchange(
            final RpcClientMethod<Q, R> method,
            final Q request
    ) {
        return this.bind(method).exchange(request);
    }

    public <Q, R> RpcClientResult<R> exchange(
            final RpcClientMethod<Q, R> method,
            final Q request,
            final long timeoutNs
    ) {
        return this.bind(method).exchange(request, timeoutNs);
    }

    @SuppressWarnings("unchecked")
    private <Q, R> ClientMethodBinding<Q, R> bindingFor(
            final RpcClientMethod<Q, R> method
    ) {
        return (ClientMethodBinding<Q, R>) this.bindings.computeIfAbsent(method, this::createBinding);
    }

    private <Q, R> ClientMethodBinding<Q, R> createBinding(
            final RpcClientMethod<Q, R> method
    ) {
        return new ClientMethodBinding<>(
                method,
                RpcCodecSupport.codecFor(method.requestType()),
                RpcCodecSupport.codecFor(method.responseType())
        );
    }

    private final class ClientMethodBinding<Q, R> {

        private final RpcClientMethod<Q, R> method;
        private final SerializationCodec<Q> requestCodec;
        private final SerializationCodec<R> responseCodec;

        private ClientMethodBinding(
                final RpcClientMethod<Q, R> method,
                final SerializationCodec<Q> requestCodec,
                final SerializationCodec<R> responseCodec
        ) {
            this.method = method;
            this.requestCodec = requestCodec;
            this.responseCodec = responseCodec;
        }

        private RpcClientResult<R> exchange(
                final RpcClientContext context,
                final Q request
        ) {
            if (request == null) {
                throw new IllegalArgumentException("request must not be null");
            }
            if (context.timeoutNs() <= 0L) {
                throw new IllegalArgumentException("timeoutNs must be > 0");
            }
            requestValidator.validate(context, request);
            final RpcClientInvocation invocation = this.buildInvocation();
            @SuppressWarnings("unchecked") final RpcClientResult<R> result =
                    (RpcClientResult<R>) invocation.proceed(context, request);
            responseValidator.validate(context, result);
            return result;
        }

        private RpcClientInvocation buildInvocation() {
            RpcClientInvocation invocation = (context, request) -> this.decodeFrame(
                    this.send(this.method.requestType().cast(request), context.timeoutNs())
            );
            for (int index = interceptors.size() - 1; index >= 0; index--) {
                final RpcClientInterceptor interceptor = interceptors.get(index);
                final RpcClientInvocation next = invocation;
                invocation = (context, request) -> interceptor.intercept(context, request, next);
            }
            return invocation;
        }

        private RpcResponseFrame send(
                final Q request,
                final long timeoutNs
        ) {
            final ExpandableArrayBuffer requestBuffer = requestBuffers.get();
            final int payloadLength = this.requestCodec.encode(
                    request,
                    requestBuffer,
                    ru.pathcreator.pyc.rpc.core.codex.RpcEnvelope.HEADER_LENGTH
            );
            return channel.sendFrame(
                    timeoutNs,
                    payloadLength,
                    this.method.requestMessageTypeId(),
                    requestBuffer
            );
        }

        private RpcClientResult<R> decodeFrame(
                final RpcResponseFrame frame
        ) {
            this.ensureExpectedResponseMessageTypeId(frame);
            if (!frame.isSuccess()) {
                return new RpcClientResult<>(
                        frame.statusCode(),
                        frame.messageTypeId(),
                        frame.correlationId(),
                        frame.payloadLength(),
                        null,
                        this.payloadText(frame)
                );
            }
            return new RpcClientResult<>(
                    frame.statusCode(),
                    frame.messageTypeId(),
                    frame.correlationId(),
                    frame.payloadLength(),
                    this.responseCodec.decode(frame.buffer(), frame.payloadOffset(), frame.payloadLength()),
                    null
            );
        }

        private void ensureExpectedResponseMessageTypeId(
                final RpcResponseFrame frame
        ) {
            if (frame.messageTypeId() != this.method.responseMessageTypeId()) {
                throw new IllegalStateException(
                        "unexpected responseMessageTypeId=" + frame.messageTypeId()
                        + ", expected=" + this.method.responseMessageTypeId()
                );
            }
        }

        private String payloadText(
                final RpcResponseFrame frame
        ) {
            final byte[] text = new byte[frame.payloadLength()];
            frame.buffer().getBytes(frame.payloadOffset(), text);
            return new String(text, StandardCharsets.UTF_8);
        }
    }
}