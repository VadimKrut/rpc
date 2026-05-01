package ru.pathcreator.pyc.rpc.core;

import io.aeron.Aeron;
import io.aeron.ConcurrentPublication;
import io.aeron.FragmentAssembler;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import ru.pathcreator.pyc.rpc.core.codex.RpcEnvelope;
import ru.pathcreator.pyc.rpc.core.codex.RpcRequestHandler;
import ru.pathcreator.pyc.rpc.core.codex.RpcResponseFrame;
import ru.pathcreator.pyc.rpc.core.codex.RpcStatusCodes;
import ru.pathcreator.pyc.rpc.core.collection.WaitersTable;
import ru.pathcreator.pyc.rpc.core.config.RpcChannelConfig;
import ru.pathcreator.pyc.rpc.core.exception.RpcCallTimeoutException;
import ru.pathcreator.pyc.rpc.core.exception.RpcPublishTimeoutException;
import ru.pathcreator.pyc.rpc.core.exception.RpcRemoteException;
import ru.pathcreator.pyc.rpc.core.generator.CorrelationIdGenerator;
import ru.pathcreator.pyc.rpc.core.generator.YieldThenParkIdleStrategy;
import ru.pathcreator.pyc.rpc.core.listener.RpcChannelListener;
import ru.pathcreator.pyc.rpc.core.serialization.RpcCodecSupport;
import ru.pathcreator.pyc.rpc.core.serialization.RpcDtoHandler;
import ru.pathcreator.pyc.rpc.core.wrapper.WrapperThread;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public final class RpcChannel implements AutoCloseable {

    private static final AtomicLong IDS = new AtomicLong();

    private final long channelId;
    private final WaitersTable waiters;
    private final Thread listenerThread;
    private final RpcChannelConfig config;
    private final Subscription subscription;
    private final FragmentHandler fragmentHandler;
    private final ConcurrentPublication publication;
    private final YieldThenParkIdleStrategy listenerIdleStrategy;
    private final ThreadLocal<YieldThenParkIdleStrategy> publisherIdleStrategies;
    private final CorrelationIdGenerator correlationIds = new CorrelationIdGenerator();
    private final Int2ObjectHashMap<RpcRequestHandler> requestHandlers = new Int2ObjectHashMap<>();
    private final Object listenersLock = new Object();
    private final List<RpcChannelListener> listeners = new ArrayList<>();

    private volatile RpcChannelListener listener = RpcChannelListener.NOOP;
    private volatile boolean running;
    private volatile boolean paused;

    RpcChannel(
            final Aeron aeron,
            final RpcChannelConfig config
    ) {
        this.channelId = IDS.incrementAndGet();
        this.config = config;
        this.publication = aeron.addPublication(config.publicationChannel(), config.streamId());
        this.subscription = aeron.addSubscription(config.subscriptionChannel(), config.streamId());
        this.waiters = new WaitersTable(
                config.waitersInitialCapacity(),
                config.waitersLoadFactor()
        );
        this.listenerIdleStrategy = new YieldThenParkIdleStrategy(config.listenerMaxYields());
        this.publisherIdleStrategies = ThreadLocal.withInitial(
                () -> new YieldThenParkIdleStrategy(config.publisherMaxYields())
        );
        this.running = true;
        this.fragmentHandler = new FragmentAssembler(
                (buffer, offset, length, header) -> this.onFragment(offset, length, header, buffer)
        );
        this.listenerThread = new Thread(this::listenerLoop, "rpc-channel-listener-" + config.streamId());
        this.listenerThread.start();
    }

    public long channelId() {
        return this.channelId;
    }

    public RpcChannelConfig config() {
        return this.config;
    }

    public int currentWaiters() {
        return this.waiters.size();
    }

    public int waitersCapacity() {
        return this.waiters.capacity();
    }

    public boolean isPaused() {
        return this.paused;
    }

    public boolean isClosed() {
        return !this.running;
    }

    public long estimatedOwnedMemoryBytes() {
        return (long) this.waiters.capacity() * (Long.BYTES + 16L);
    }

    public void pause() {
        if (!this.running || this.paused) {
            return;
        }
        this.paused = true;
        this.listener.onPaused();
        this.listenerThread.interrupt();
    }

    public void resume() {
        if (!this.running || !this.paused) {
            return;
        }
        this.paused = false;
        this.listener.onResumed();
        LockSupport.unpark(this.listenerThread);
    }

    public void addListener(
            final RpcChannelListener listener
    ) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        synchronized (this.listenersLock) {
            this.listeners.add(listener);
            this.listener = compose(this.listeners);
        }
    }

    public void removeListener(
            final RpcChannelListener listener
    ) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        synchronized (this.listenersLock) {
            this.listeners.remove(listener);
            this.listener = compose(this.listeners);
        }
    }

    public void registerRequestHandler(
            final int requestMessageTypeId,
            final RpcRequestHandler handler
    ) {
        this.requestHandlers.put(requestMessageTypeId, handler);
    }

    /**
     * Низкоуровневый типизированный обработчик. Аллоцирует свежий direct {@code ByteBuffer}
     * на каждый ответ, что нежелательно на горячем пути. Используйте
     * {@code RpcServer.register(...)} — он переиспользует thread-local буфер ответа,
     * либо {@link #registerRequestHandler(int, RpcRequestHandler)} как сырое API.
     */
    @Deprecated
    public <Q, R> void registerHandler(
            final Class<Q> requestType,
            final Class<R> responseType,
            final int requestMessageTypeId,
            final int responseMessageTypeId,
            final RpcDtoHandler<Q, R> handler
    ) {
        final ru.pathcreator.pyc.rpc.codec.SerializationCodec<Q> requestCodec = RpcCodecSupport.codecFor(requestType);
        final ru.pathcreator.pyc.rpc.codec.SerializationCodec<R> responseCodec = RpcCodecSupport.codecFor(responseType);
        this.registerRequestHandler(requestMessageTypeId, (offset, length, correlationId, buffer) -> {
            final Q request = requestCodec.decode(buffer, offset, length);
            final R response = handler.handle(request);
            if (response == null) {
                throw new IllegalStateException("RPC handler returned null for " + responseType.getName());
            }
            final int payloadLength = responseCodec.measure(response);
            final UnsafeBuffer responseBuffer = new UnsafeBuffer(java.nio.ByteBuffer.allocateDirect(RpcEnvelope.HEADER_LENGTH + payloadLength));
            responseCodec.encode(response, responseBuffer, RpcEnvelope.HEADER_LENGTH);
            this.reply(payloadLength, responseMessageTypeId, correlationId, responseBuffer);
        });
    }

    /**
     * Высокоуровневый типизированный {@code send}. Аллоцирует свежий direct
     * {@code ByteBuffer} на каждый запрос (через {@code RpcCodecSupport.encode}),
     * что нежелательно на горячем пути. Используйте {@code RpcClient} — у него
     * thread-local буфер запроса и кэшированные binding'и, либо
     * {@link #sendFrame(long, int, int, MutableDirectBuffer)} как сырое API.
     */
    @Deprecated
    public <Q, R> R send(
            final Q request,
            final long timeoutNs,
            final Class<R> responseType,
            final int requestMessageTypeId,
            final int responseMessageTypeId
    ) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        @SuppressWarnings("unchecked") final Class<Q> requestType = (Class<Q>) request.getClass();
        final UnsafeBuffer requestBuffer = RpcCodecSupport.encode(request, RpcEnvelope.HEADER_LENGTH, requestType);
        final UnsafeBuffer response = this.sendRaw(
                timeoutNs,
                requestBuffer.capacity() - RpcEnvelope.HEADER_LENGTH,
                requestMessageTypeId,
                requestBuffer
        );
        return this.decodeResponse(responseType, this.toResponseFrame(response), responseMessageTypeId);
    }

    public RpcResponseFrame sendFrame(
            final long timeoutNs,
            final int payloadLength,
            final int requestMessageTypeId,
            final MutableDirectBuffer requestBuffer
    ) {
        return this.toResponseFrame(
                this.sendRaw(timeoutNs, payloadLength, requestMessageTypeId, requestBuffer)
        );
    }

    public UnsafeBuffer sendRaw(
            final long timeoutNs,
            final int payloadLength,
            final int requestMessageTypeId,
            final MutableDirectBuffer requestBuffer
    ) {
        final WrapperThread wrapper = new WrapperThread(Thread.currentThread());
        final long correlationId = this.correlationIds.nextId();
        final long deadline = System.nanoTime() + timeoutNs;
        this.waiters.put(correlationId, wrapper);
        try {
            this.publish(0, RpcStatusCodes.OK, payloadLength, requestMessageTypeId, correlationId, deadline, requestBuffer);
        } catch (final RuntimeException problem) {
            this.waiters.remove(correlationId);
            throw problem;
        }
        return this.await(deadline, correlationId, wrapper);
    }

    public void reply(
            final int payloadLength,
            final int responseMessageTypeId,
            final long correlationId,
            final MutableDirectBuffer responseBuffer
    ) {
        this.reply(payloadLength, responseMessageTypeId, RpcStatusCodes.OK, correlationId, responseBuffer);
    }

    public void reply(
            final int payloadLength,
            final int responseMessageTypeId,
            final int statusCode,
            final long correlationId,
            final MutableDirectBuffer responseBuffer
    ) {
        this.publish(
                RpcEnvelope.FLAG_RESPONSE,
                statusCode,
                payloadLength,
                responseMessageTypeId,
                correlationId,
                Long.MAX_VALUE,
                responseBuffer
        );
    }

    @Override
    public void close() {
        if (!this.running) {
            return;
        }
        this.running = false;
        this.waiters.clearAndForEach(wrapper -> LockSupport.unpark(wrapper.thread()));
        this.listenerThread.interrupt();
        try {
            this.listenerThread.join();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.publication.close();
        this.subscription.close();
        this.listener.onClosed();
    }

    private void publish(
            final int flags,
            final int statusCode,
            final int payloadLength,
            final int requestMessageTypeId,
            final long correlationId,
            final long deadline,
            final MutableDirectBuffer requestBuffer
    ) {
        final int totalLength = RpcEnvelope.HEADER_LENGTH + payloadLength;
        final YieldThenParkIdleStrategy idleStrategy = this.publisherIdleStrategies.get();
        idleStrategy.reset();
        RpcEnvelope.encode(flags, statusCode, payloadLength, requestMessageTypeId, correlationId, requestBuffer);
        while (this.running) {
            if (this.paused) {
                throw new IllegalStateException("channel paused");
            }
            final long result = this.publication.offer(requestBuffer, 0, totalLength);
            if (result > 0) {
                if ((flags & RpcEnvelope.FLAG_RESPONSE) == 0) {
                    this.listener.onRequestSent(requestMessageTypeId, payloadLength, correlationId);
                } else {
                    this.listener.onResponseSent(requestMessageTypeId, payloadLength, statusCode, correlationId);
                }
                return;
            }
            if (deadline != Long.MAX_VALUE && System.nanoTime() >= deadline) {
                this.listener.onPublishTimeout(requestMessageTypeId, payloadLength, correlationId);
                throw new RpcPublishTimeoutException(correlationId);
            }
            idleStrategy.idle();
        }
        throw new IllegalStateException("channel closed");
    }

    private void listenerLoop() {
        final YieldThenParkIdleStrategy idleStrategy = this.listenerIdleStrategy;
        idleStrategy.reset();
        while (this.running) {
            if (this.paused) {
                LockSupport.parkNanos(1_000_000L);
                idleStrategy.reset();
                continue;
            }
            final int fragments = this.subscription.poll(this.fragmentHandler, this.config.fragmentLimit());
            if (fragments == 0) {
                idleStrategy.idle();
            } else {
                idleStrategy.reset();
            }
        }
    }

    private void onFragment(
            final int offset,
            final int length,
            final Header header,
            final DirectBuffer buffer
    ) {
        if (length < RpcEnvelope.HEADER_LENGTH) {
            return;
        }
        final int flags = RpcEnvelope.flags(offset, buffer);
        final long correlationId = RpcEnvelope.correlationId(offset, buffer);
        if ((flags & RpcEnvelope.FLAG_RESPONSE) == 0) {
            final int payloadLength = RpcEnvelope.payloadLength(offset, buffer);
            if (payloadLength < 0 || payloadLength > length - RpcEnvelope.HEADER_LENGTH) {
                return;
            }
            this.listener.onRequestReceived(RpcEnvelope.messageTypeId(offset, buffer), payloadLength, correlationId);
            final RpcRequestHandler handler = this.requestHandlers.get(RpcEnvelope.messageTypeId(offset, buffer));
            if (handler != null) {
                handler.handle(offset + RpcEnvelope.HEADER_LENGTH, payloadLength, correlationId, buffer);
            }
            return;
        }
        final WrapperThread wrapper = this.waiters.remove(correlationId);
        if (wrapper == null) {
            return;
        }
        this.listener.onResponseReceived(
                RpcEnvelope.messageTypeId(offset, buffer),
                RpcEnvelope.payloadLength(offset, buffer),
                RpcEnvelope.statusCode(offset, buffer),
                correlationId
        );
        wrapper.wrap(offset, length, buffer);
        LockSupport.unpark(wrapper.thread());
    }

    private UnsafeBuffer await(
            final long deadline,
            final long correlationId,
            final WrapperThread wrapper
    ) {
        while (wrapper.bytes() == null) {
            final long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                if (this.waiters.remove(correlationId) == wrapper) {
                    this.listener.onCallTimeout(correlationId);
                    throw new RpcCallTimeoutException(correlationId);
                }
                continue;
            }
            LockSupport.parkNanos(remaining);
            if (Thread.interrupted()) {
                this.waiters.remove(correlationId);
                Thread.currentThread().interrupt();
                throw new IllegalStateException("waiting thread interrupted");
            }
        }
        return wrapper.bytes();
    }

    private <T> T decodeResponse(
            final Class<T> responseType,
            final RpcResponseFrame response,
            final int expectedResponseMessageTypeId
    ) {
        if (response.messageTypeId() != expectedResponseMessageTypeId) {
            throw new IllegalStateException("unexpected responseMessageTypeId=" + response.messageTypeId() + ", expected=" + expectedResponseMessageTypeId);
        }
        if (!response.isSuccess()) {
            throw this.remoteException(response);
        }
        return RpcCodecSupport.decode(
                response.payloadOffset(),
                response.payloadLength(),
                responseType,
                response.buffer()
        );
    }

    private RpcResponseFrame toResponseFrame(
            final UnsafeBuffer response
    ) {
        final int flags = RpcEnvelope.flags(0, response);
        final int statusCode = RpcEnvelope.statusCode(0, response);
        final int payloadLength = RpcEnvelope.payloadLength(0, response);
        if ((flags & RpcEnvelope.FLAG_RESPONSE) == 0) {
            throw new IllegalStateException("response flag is not set");
        }
        if (payloadLength < 0 || payloadLength > response.capacity() - RpcEnvelope.HEADER_LENGTH) {
            throw new IllegalStateException("invalid payload length");
        }
        return new RpcResponseFrame(
                RpcEnvelope.messageTypeId(0, response),
                RpcEnvelope.correlationId(0, response),
                statusCode,
                payloadLength,
                response
        );
    }

    private RpcRemoteException remoteException(
            final RpcResponseFrame response
    ) {
        return new RpcRemoteException(
                response.statusCode(),
                response.messageTypeId(),
                response.correlationId(),
                this.payloadText(response.payloadLength(), response.buffer())
        );
    }

    private String payloadText(
            final int length,
            final DirectBuffer buffer
    ) {
        final byte[] text = new byte[length];
        buffer.getBytes(RpcEnvelope.HEADER_LENGTH, text);
        return new String(text, StandardCharsets.UTF_8);
    }

    private static RpcChannelListener compose(
            final List<RpcChannelListener> listeners
    ) {
        if (listeners.isEmpty()) {
            return RpcChannelListener.NOOP;
        }
        if (listeners.size() == 1) {
            return listeners.getFirst();
        }
        final List<RpcChannelListener> snapshot = List.copyOf(listeners);
        return new RpcChannelListener() {
            @Override
            public void onRequestSent(final int messageTypeId, final int payloadLength, final long correlationId) {
                for (final RpcChannelListener listener : snapshot) {
                    listener.onRequestSent(messageTypeId, payloadLength, correlationId);
                }
            }

            @Override
            public void onRequestReceived(final int messageTypeId, final int payloadLength, final long correlationId) {
                for (final RpcChannelListener listener : snapshot) {
                    listener.onRequestReceived(messageTypeId, payloadLength, correlationId);
                }
            }

            @Override
            public void onResponseSent(final int messageTypeId, final int payloadLength, final int statusCode, final long correlationId) {
                for (final RpcChannelListener listener : snapshot) {
                    listener.onResponseSent(messageTypeId, payloadLength, statusCode, correlationId);
                }
            }

            @Override
            public void onResponseReceived(final int messageTypeId, final int payloadLength, final int statusCode, final long correlationId) {
                for (final RpcChannelListener listener : snapshot) {
                    listener.onResponseReceived(messageTypeId, payloadLength, statusCode, correlationId);
                }
            }

            @Override
            public void onPublishTimeout(final int messageTypeId, final int payloadLength, final long correlationId) {
                for (final RpcChannelListener listener : snapshot) {
                    listener.onPublishTimeout(messageTypeId, payloadLength, correlationId);
                }
            }

            @Override
            public void onCallTimeout(final long correlationId) {
                for (final RpcChannelListener listener : snapshot) {
                    listener.onCallTimeout(correlationId);
                }
            }

            @Override
            public void onPaused() {
                for (final RpcChannelListener listener : snapshot) {
                    listener.onPaused();
                }
            }

            @Override
            public void onResumed() {
                for (final RpcChannelListener listener : snapshot) {
                    listener.onResumed();
                }
            }

            @Override
            public void onClosed() {
                for (final RpcChannelListener listener : snapshot) {
                    listener.onClosed();
                }
            }
        };
    }
}