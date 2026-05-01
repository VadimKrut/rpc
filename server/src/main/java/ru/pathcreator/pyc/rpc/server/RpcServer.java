package ru.pathcreator.pyc.rpc.server;

import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntHashSet;
import ru.pathcreator.pyc.rpc.codec.SerializationCodec;
import ru.pathcreator.pyc.rpc.contract.RpcMethodContract;
import ru.pathcreator.pyc.rpc.core.RpcChannel;
import ru.pathcreator.pyc.rpc.core.codex.RpcEnvelope;
import ru.pathcreator.pyc.rpc.core.codex.RpcStatusCodes;
import ru.pathcreator.pyc.rpc.core.serialization.RpcCodecSupport;
import ru.pathcreator.pyc.rpc.encryption.RpcPayloadEncryption;
import ru.pathcreator.pyc.rpc.server.context.RpcServerContext;
import ru.pathcreator.pyc.rpc.server.error.RpcServerErrorResponse;
import ru.pathcreator.pyc.rpc.server.error.RpcServerExceptionMapper;
import ru.pathcreator.pyc.rpc.server.error.RpcStatusException;
import ru.pathcreator.pyc.rpc.server.handler.RpcServerContextHandler;
import ru.pathcreator.pyc.rpc.server.handler.RpcServerHandler;
import ru.pathcreator.pyc.rpc.server.listener.RpcServerListener;
import ru.pathcreator.pyc.rpc.server.pipeline.RpcServerInterceptor;
import ru.pathcreator.pyc.rpc.server.pipeline.RpcServerInvocation;
import ru.pathcreator.pyc.rpc.server.pipeline.RpcServerRequestValidator;

import java.util.ArrayList;
import java.util.List;

public final class RpcServer {

    private final RpcChannel channel;
    private final List<RpcServerInterceptor> interceptors;
    private final RpcServerExceptionMapper exceptionMapper;
    private final RpcServerRequestValidator requestValidator;
    private final Object listenersLock = new Object();
    private final boolean payloadEncryptionEnabled;
    private final RpcPayloadEncryption payloadEncryption;
    private final List<RpcServerListener> listeners = new ArrayList<>();
    private final IntHashSet registeredRequestMessageTypeIds = new IntHashSet();
    private final Int2ObjectHashMap<RegisteredMethod> registeredMethods = new Int2ObjectHashMap<>();
    private final ThreadLocal<ExpandableArrayBuffer> responseBuffers = ThreadLocal.withInitial(() -> new ExpandableArrayBuffer(512));
    private final ThreadLocal<ExpandableArrayBuffer> plainResponseBuffers = ThreadLocal.withInitial(() -> new ExpandableArrayBuffer(512));
    private final ThreadLocal<ExpandableArrayBuffer> decryptedRequestBuffers = ThreadLocal.withInitial(() -> new ExpandableArrayBuffer(512));
    private volatile RpcServerListener listener = RpcServerListener.NOOP;
    private volatile boolean enabled = true;

    RpcServer(
            final RpcChannel channel,
            final List<RpcServerInterceptor> interceptors,
            final RpcServerExceptionMapper exceptionMapper,
            final RpcServerRequestValidator requestValidator,
            final RpcServerListener listener,
            final RpcPayloadEncryption payloadEncryption
    ) {
        this.channel = channel;
        this.exceptionMapper = exceptionMapper;
        this.requestValidator = requestValidator;
        this.interceptors = interceptors;
        this.payloadEncryption = payloadEncryption;
        this.payloadEncryptionEnabled = payloadEncryption != RpcPayloadEncryption.NOOP;
        if (listener != RpcServerListener.NOOP) {
            this.listeners.add(listener);
            this.listener = listener;
        }
    }

    public static RpcServerBuilder builder(
            final RpcChannel channel
    ) {
        return new RpcServerBuilder(channel);
    }

    public RpcChannel channel() {
        return this.channel;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public void addListener(
            final RpcServerListener listener
    ) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        synchronized (this.listenersLock) {
            this.listeners.add(listener);
            this.listener = composeListeners(this.listeners);
        }
    }

    public void removeListener(
            final RpcServerListener listener
    ) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        synchronized (this.listenersLock) {
            this.listeners.remove(listener);
            this.listener = composeListeners(this.listeners);
        }
    }

    public void enableMethod(
            final int requestMessageTypeId
    ) {
        final RegisteredMethod method = this.registeredMethods.get(requestMessageTypeId);
        if (method == null) {
            throw new IllegalArgumentException("requestMessageTypeId is not registered: " + requestMessageTypeId);
        }
        method.enabled = true;
    }

    public void disableMethod(
            final int requestMessageTypeId
    ) {
        final RegisteredMethod method = this.registeredMethods.get(requestMessageTypeId);
        if (method == null) {
            throw new IllegalArgumentException("requestMessageTypeId is not registered: " + requestMessageTypeId);
        }
        method.enabled = false;
    }

    public List<RegisteredMethodSnapshot> registeredMethods() {
        final List<RegisteredMethodSnapshot> snapshots = new ArrayList<>(this.registeredMethods.size());
        for (final RegisteredMethod method : this.registeredMethods.values()) {
            snapshots.add(new RegisteredMethodSnapshot(method.contract, method.enabled));
        }
        return List.copyOf(snapshots);
    }

    public synchronized <Q, R> RpcServer register(
            final RpcMethodContract<Q, R> method,
            final RpcServerHandler<? super Q, ? extends R> handler
    ) {
        return this.register(method, (context, request) -> handler.handle(request));
    }

    public synchronized <Q, R> RpcServer register(
            final RpcMethodContract<Q, R> method,
            final RpcServerContextHandler<? super Q, ? extends R> handler
    ) {
        if (method == null) {
            throw new IllegalArgumentException("method must not be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }
        final int requestMessageTypeId = method.requestMessageTypeId();
        if (!this.registeredRequestMessageTypeIds.add(requestMessageTypeId)) {
            throw new IllegalStateException("requestMessageTypeId already registered: " + requestMessageTypeId);
        }
        try {
            final SerializationCodec<Q> requestCodec = RpcCodecSupport.codecFor(method.requestType());
            final SerializationCodec<R> responseCodec = RpcCodecSupport.codecFor(method.responseType());
            final RpcServerInvocation invocation = this.buildInvocation(method, handler);
            final RegisteredMethod registeredMethod = new RegisteredMethod(method);
            this.registeredMethods.put(requestMessageTypeId, registeredMethod);
            this.channel.registerRequestHandler(requestMessageTypeId, (offset, length, correlationId, buffer) -> {
                final boolean listenerEnabled = this.listener != RpcServerListener.NOOP;
                final long startNs = listenerEnabled ? System.nanoTime() : 0L;
                if (listenerEnabled) {
                    this.listener.onStart(method, correlationId, length);
                }
                Q request = null;
                final RpcServerContext context = new RpcServerContext(
                        method,
                        correlationId,
                        method.requestMessageTypeId(),
                        method.responseMessageTypeId(),
                        length
                );
                try {
                    if (!this.enabled || !registeredMethod.enabled) {
                        throw new RpcStatusException(RpcStatusCodes.SERVICE_UNAVAILABLE, "service unavailable");
                    }
                    if (this.payloadEncryptionEnabled) {
                        final ExpandableArrayBuffer decryptedRequestBuffer = this.decryptedRequestBuffers.get();
                        final int decryptedLength = this.payloadEncryption.decrypt(
                                buffer,
                                offset,
                                length,
                                decryptedRequestBuffer,
                                0
                        );
                        request = requestCodec.decode(decryptedRequestBuffer, 0, decryptedLength);
                    } else {
                        request = requestCodec.decode(buffer, offset, length);
                    }
                    this.requestValidator.validate(context, request);
                    final R response = method.responseType().cast(invocation.proceed(context, request));
                    if (response == null) {
                        throw new IllegalStateException(
                                "RPC handler returned null for " + method.responseType().getName()
                        );
                    }
                    final ExpandableArrayBuffer responseBuffer = this.responseBuffers.get();
                    final int payloadLength;
                    if (this.payloadEncryptionEnabled) {
                        final ExpandableArrayBuffer plainResponseBuffer = this.plainResponseBuffers.get();
                        final int plainPayloadLength = responseCodec.encode(response, plainResponseBuffer, 0);
                        payloadLength = this.payloadEncryption.encrypt(
                                plainResponseBuffer,
                                0,
                                plainPayloadLength,
                                responseBuffer,
                                RpcEnvelope.HEADER_LENGTH
                        );
                    } else {
                        payloadLength = responseCodec.encode(response, responseBuffer, RpcEnvelope.HEADER_LENGTH);
                    }
                    this.channel.reply(
                            payloadLength,
                            method.responseMessageTypeId(),
                            correlationId,
                            responseBuffer
                    );
                    this.notifySuccess(method, correlationId, startNs, length, payloadLength);
                } catch (final Throwable error) {
                    if (isFatal(error)) {
                        throwUnchecked(error);
                    }
                    final RpcServerErrorResponse errorResponse = safeErrorResponse(request, error, method);
                    final ExpandableArrayBuffer responseBuffer = this.responseBuffers.get();
                    final int payloadLength;
                    if (this.payloadEncryptionEnabled) {
                        final ExpandableArrayBuffer plainResponseBuffer = this.plainResponseBuffers.get();
                        final int plainPayloadLength = plainResponseBuffer.putStringWithoutLengthUtf8(
                                0,
                                errorResponse.message()
                        );
                        payloadLength = this.payloadEncryption.encrypt(
                                plainResponseBuffer,
                                0,
                                plainPayloadLength,
                                responseBuffer,
                                RpcEnvelope.HEADER_LENGTH
                        );
                    } else {
                        payloadLength = responseBuffer.putStringWithoutLengthUtf8(
                                RpcEnvelope.HEADER_LENGTH,
                                errorResponse.message()
                        );
                    }
                    this.channel.reply(
                            payloadLength,
                            method.responseMessageTypeId(),
                            errorResponse.statusCode(),
                            correlationId,
                            responseBuffer
                    );
                    this.notifyFailure(
                            method,
                            correlationId,
                            startNs,
                            length,
                            payloadLength,
                            errorResponse.statusCode(),
                            error
                    );
                }
            });
            return this;
        } catch (final RuntimeException error) {
            this.registeredRequestMessageTypeIds.remove(requestMessageTypeId);
            this.registeredMethods.remove(requestMessageTypeId);
            throw error;
        }
    }

    private <Q, R> RpcServerInvocation buildInvocation(
            final RpcMethodContract<Q, R> method,
            final RpcServerContextHandler<? super Q, ? extends R> handler
    ) {
        RpcServerInvocation invocation = (context, request) -> handler.handle(
                context,
                method.requestType().cast(request)
        );
        for (int index = this.interceptors.size() - 1; index >= 0; index--) {
            final RpcServerInterceptor interceptor = this.interceptors.get(index);
            final RpcServerInvocation next = invocation;
            invocation = (context, request) -> interceptor.intercept(context, request, next);
        }
        return invocation;
    }

    private RpcServerErrorResponse safeErrorResponse(
            final Object request,
            final Throwable error,
            final RpcMethodContract<?, ?> method
    ) {
        try {
            return this.exceptionMapper.toErrorResponse(method, request, error);
        } catch (final Throwable mappingError) {
            if (isFatal(mappingError)) {
                throw mappingError;
            }
            return RpcServerExceptionMapper.DEFAULT.toErrorResponse(method, request, error);
        }
    }

    private static boolean isFatal(final Throwable error) {
        return error instanceof VirtualMachineError
               || error instanceof LinkageError;
    }

    private static void throwUnchecked(final Throwable error) {
        RpcServer.<RuntimeException>throwUnchecked0(error);
    }

    private void notifySuccess(
            final RpcMethodContract<?, ?> method,
            final long correlationId,
            final long startNs,
            final int requestPayloadLength,
            final int responsePayloadLength
    ) {
        if (this.listener == RpcServerListener.NOOP) {
            return;
        }
        this.listener.onSuccess(
                method,
                correlationId,
                System.nanoTime() - startNs,
                requestPayloadLength,
                responsePayloadLength,
                RpcStatusCodes.OK
        );
    }

    private void notifyFailure(
            final RpcMethodContract<?, ?> method,
            final long correlationId,
            final long startNs,
            final int requestPayloadLength,
            final int responsePayloadLength,
            final int statusCode,
            final Throwable error
    ) {
        if (this.listener == RpcServerListener.NOOP) {
            return;
        }
        this.listener.onFailure(
                method,
                correlationId,
                System.nanoTime() - startNs,
                requestPayloadLength,
                responsePayloadLength,
                statusCode,
                error
        );
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked0(final Throwable error) throws E {
        throw (E) error;
    }

    private static RpcServerListener composeListeners(
            final List<RpcServerListener> listeners
    ) {
        if (listeners.isEmpty()) {
            return RpcServerListener.NOOP;
        }
        if (listeners.size() == 1) {
            return listeners.getFirst();
        }
        final List<RpcServerListener> snapshot = List.copyOf(listeners);
        return new RpcServerListener() {
            @Override
            public void onSuccess(
                    final RpcMethodContract<?, ?> method,
                    final long correlationId,
                    final long latencyNs,
                    final int requestPayloadLength,
                    final int responsePayloadLength,
                    final int statusCode
            ) {
                for (final RpcServerListener listener : snapshot) {
                    listener.onSuccess(method, correlationId, latencyNs, requestPayloadLength, responsePayloadLength, statusCode);
                }
            }

            @Override
            public void onStart(
                    final RpcMethodContract<?, ?> method,
                    final long correlationId,
                    final int requestPayloadLength
            ) {
                for (final RpcServerListener listener : snapshot) {
                    listener.onStart(method, correlationId, requestPayloadLength);
                }
            }

            @Override
            public void onFailure(
                    final RpcMethodContract<?, ?> method,
                    final long correlationId,
                    final long latencyNs,
                    final int requestPayloadLength,
                    final int responsePayloadLength,
                    final int statusCode,
                    final Throwable error
            ) {
                for (final RpcServerListener listener : snapshot) {
                    listener.onFailure(method, correlationId, latencyNs, requestPayloadLength, responsePayloadLength, statusCode, error);
                }
            }
        };
    }

    private static final class RegisteredMethod {

        private final RpcMethodContract<?, ?> contract;
        private volatile boolean enabled;

        private RegisteredMethod(
                final RpcMethodContract<?, ?> contract
        ) {
            this.contract = contract;
            this.enabled = true;
        }
    }

    public record RegisteredMethodSnapshot(
            RpcMethodContract<?, ?> contract,
            boolean enabled
    ) {
    }
}