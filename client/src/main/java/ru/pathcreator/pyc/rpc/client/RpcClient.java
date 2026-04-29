package ru.pathcreator.pyc.rpc.client;

import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import ru.pathcreator.pyc.rpc.client.call.RpcClientCall;
import ru.pathcreator.pyc.rpc.client.context.RpcClientContext;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientInterceptor;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientInvocation;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientRequestValidator;
import ru.pathcreator.pyc.rpc.client.pipeline.RpcClientResponseValidator;
import ru.pathcreator.pyc.rpc.client.response.RpcClientResult;
import ru.pathcreator.pyc.rpc.codec.SerializationCodec;
import ru.pathcreator.pyc.rpc.contract.RpcMethodContract;
import ru.pathcreator.pyc.rpc.core.RpcChannel;
import ru.pathcreator.pyc.rpc.core.codex.RpcEnvelope;
import ru.pathcreator.pyc.rpc.core.codex.RpcResponseFrame;
import ru.pathcreator.pyc.rpc.core.serialization.RpcCodecSupport;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class RpcClient {

    private final RpcChannel channel;
    private final long defaultTimeoutNs;
    private final boolean fastPathEligible;
    private final Object bindingsLock = new Object();
    private final List<RpcClientInterceptor> interceptors;
    private final RpcClientRequestValidator requestValidator;
    private final RpcClientResponseValidator responseValidator;
    private volatile Int2ObjectHashMap<ClientMethodBinding<?, ?>> bindings = new Int2ObjectHashMap<>();
    private final ThreadLocal<ExpandableArrayBuffer> requestBuffers = ThreadLocal.withInitial(() -> new ExpandableArrayBuffer(512));

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
        this.fastPathEligible = interceptors.isEmpty()
                                && requestValidator == RpcClientRequestValidator.NOOP
                                && responseValidator == RpcClientResponseValidator.NOOP;
    }

    public static RpcClientBuilder builder(
            final RpcChannel channel
    ) {
        return new RpcClientBuilder(channel);
    }

    public <Q, R> RpcClientCall<Q, R> bind(
            final RpcMethodContract<Q, R> method
    ) {
        if (method == null) {
            throw new IllegalArgumentException("method must not be null");
        }
        return this.bindingFor(method).call;
    }

    public <Q, R> R send(
            final RpcMethodContract<Q, R> method,
            final Q request
    ) {
        return this.bind(method).send(request);
    }

    public <Q, R> R send(
            final RpcMethodContract<Q, R> method,
            final Q request,
            final long timeoutNs
    ) {
        return this.bind(method).send(request, timeoutNs);
    }

    public <Q, R> RpcClientResult<R> exchange(
            final RpcMethodContract<Q, R> method,
            final Q request
    ) {
        return this.bind(method).exchange(request);
    }

    public <Q, R> RpcClientResult<R> exchange(
            final RpcMethodContract<Q, R> method,
            final Q request,
            final long timeoutNs
    ) {
        return this.bind(method).exchange(request, timeoutNs);
    }

    @SuppressWarnings("unchecked")
    private <Q, R> ClientMethodBinding<Q, R> bindingFor(
            final RpcMethodContract<Q, R> method
    ) {
        final ClientMethodBinding<?, ?> existing = this.bindings.get(method.requestMessageTypeId());
        if (existing != null) {
            ensureMatchingMethod(existing, method);
            return (ClientMethodBinding<Q, R>) existing;
        }
        return this.createBindingSlow(method);
    }

    @SuppressWarnings("unchecked")
    private <Q, R> ClientMethodBinding<Q, R> createBindingSlow(
            final RpcMethodContract<Q, R> method
    ) {
        synchronized (this.bindingsLock) {
            final Int2ObjectHashMap<ClientMethodBinding<?, ?>> current = this.bindings;
            final ClientMethodBinding<?, ?> existing = current.get(method.requestMessageTypeId());
            if (existing != null) {
                ensureMatchingMethod(existing, method);
                return (ClientMethodBinding<Q, R>) existing;
            }
            final ClientMethodBinding<Q, R> binding = new ClientMethodBinding<>(
                    method,
                    RpcCodecSupport.codecFor(method.requestType()),
                    RpcCodecSupport.codecFor(method.responseType())
            );
            final Int2ObjectHashMap<ClientMethodBinding<?, ?>> next = new Int2ObjectHashMap<>(current.size() + 1, 0.6f);
            next.putAll(current);
            next.put(method.requestMessageTypeId(), binding);
            this.bindings = next;
            return binding;
        }
    }

    private static void ensureMatchingMethod(
            final ClientMethodBinding<?, ?> binding,
            final RpcMethodContract<?, ?> method
    ) {
        if (!binding.method.equals(method)) {
            throw new IllegalStateException(
                    "requestMessageTypeId " + method.requestMessageTypeId()
                    + " is already bound to contract '" + binding.method.name()
                    + "', cannot reuse it for '" + method.name() + "'"
            );
        }
    }

    private final class ClientMethodBinding<Q, R> {

        private final RpcMethodContract<Q, R> method;
        private final SerializationCodec<Q> requestCodec;
        private final SerializationCodec<R> responseCodec;
        private final RpcClientInvocation cachedInvocation;
        private final RpcClientCall<Q, R> call;

        private ClientMethodBinding(
                final RpcMethodContract<Q, R> method,
                final SerializationCodec<Q> requestCodec,
                final SerializationCodec<R> responseCodec
        ) {
            this.method = method;
            this.requestCodec = requestCodec;
            this.responseCodec = responseCodec;
            this.cachedInvocation = this.buildInvocation();
            final RpcClientCall.Dispatcher<Q, R> dispatcher = RpcClient.this.fastPathEligible
                    ? this::exchangeFast
                    : this::exchangeWithPipeline;
            this.call = new RpcClientCall<>(method, RpcClient.this.defaultTimeoutNs, dispatcher);
        }

        private RpcClientResult<R> exchangeFast(
                final Q request,
                final long timeoutNs
        ) {
            if (request == null) {
                throw new IllegalArgumentException("request must not be null");
            }
            if (timeoutNs <= 0L) {
                throw new IllegalArgumentException("timeoutNs must be > 0");
            }
            return this.decodeFrame(this.send(request, timeoutNs));
        }

        private RpcClientResult<R> exchangeWithPipeline(
                final Q request,
                final long timeoutNs
        ) {
            if (request == null) {
                throw new IllegalArgumentException("request must not be null");
            }
            if (timeoutNs <= 0L) {
                throw new IllegalArgumentException("timeoutNs must be > 0");
            }
            final RpcClientContext context = new RpcClientContext(this.method, timeoutNs);
            RpcClient.this.requestValidator.validate(context, request);
            @SuppressWarnings("unchecked") final RpcClientResult<R> result =
                    (RpcClientResult<R>) this.cachedInvocation.proceed(context, request);
            RpcClient.this.responseValidator.validate(context, result);
            return result;
        }

        private RpcClientInvocation buildInvocation() {
            RpcClientInvocation invocation = (context, request) -> this.decodeFrame(
                    this.send(this.method.requestType().cast(request), context.timeoutNs())
            );
            final List<RpcClientInterceptor> chain = RpcClient.this.interceptors;
            for (int index = chain.size() - 1; index >= 0; index--) {
                final RpcClientInterceptor interceptor = chain.get(index);
                final RpcClientInvocation next = invocation;
                invocation = (context, request) -> interceptor.intercept(context, request, next);
            }
            return invocation;
        }

        private RpcResponseFrame send(
                final Q request,
                final long timeoutNs
        ) {
            final ExpandableArrayBuffer requestBuffer = RpcClient.this.requestBuffers.get();
            final int payloadLength = this.requestCodec.encode(
                    request,
                    requestBuffer,
                    RpcEnvelope.HEADER_LENGTH
            );
            return RpcClient.this.channel.sendFrame(
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