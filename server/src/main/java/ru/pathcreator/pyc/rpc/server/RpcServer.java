package ru.pathcreator.pyc.rpc.server;

import org.agrona.ExpandableArrayBuffer;
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
import ru.pathcreator.pyc.rpc.server.handler.RpcServerContextHandler;
import ru.pathcreator.pyc.rpc.server.handler.RpcServerHandler;
import ru.pathcreator.pyc.rpc.server.listener.RpcServerListener;
import ru.pathcreator.pyc.rpc.server.pipeline.RpcServerInterceptor;
import ru.pathcreator.pyc.rpc.server.pipeline.RpcServerInvocation;
import ru.pathcreator.pyc.rpc.server.pipeline.RpcServerRequestValidator;

import java.util.List;

public final class RpcServer {

    private final RpcChannel channel;
    private final List<RpcServerInterceptor> interceptors;
    private final RpcServerExceptionMapper exceptionMapper;
    private final RpcServerRequestValidator requestValidator;
    private final RpcServerListener listener;
    private final boolean listenerEnabled;
    private final boolean payloadEncryptionEnabled;
    private final RpcPayloadEncryption payloadEncryption;
    private final IntHashSet registeredRequestMessageTypeIds = new IntHashSet();
    private final ThreadLocal<ExpandableArrayBuffer> responseBuffers = ThreadLocal.withInitial(() -> new ExpandableArrayBuffer(512));
    private final ThreadLocal<ExpandableArrayBuffer> plainResponseBuffers = ThreadLocal.withInitial(() -> new ExpandableArrayBuffer(512));
    private final ThreadLocal<ExpandableArrayBuffer> decryptedRequestBuffers = ThreadLocal.withInitial(() -> new ExpandableArrayBuffer(512));

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
        this.listener = listener;
        this.payloadEncryption = payloadEncryption;
        this.listenerEnabled = listener != RpcServerListener.NOOP;
        this.payloadEncryptionEnabled = payloadEncryption != RpcPayloadEncryption.NOOP;
    }

    public static RpcServerBuilder builder(
            final RpcChannel channel
    ) {
        return new RpcServerBuilder(channel);
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
            this.channel.registerRequestHandler(requestMessageTypeId, (offset, length, correlationId, buffer) -> {
                final long startNs = this.listenerEnabled ? System.nanoTime() : 0L;
                if (this.listenerEnabled) {
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
        if (!this.listenerEnabled) {
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
        if (!this.listenerEnabled) {
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
}