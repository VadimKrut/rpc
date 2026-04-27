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
import ru.pathcreator.pyc.rpc.core.codex.RpcMethodRegistry;
import ru.pathcreator.pyc.rpc.core.codex.RpcRequestHandler;
import ru.pathcreator.pyc.rpc.core.codex.RpcResponseDecoder;
import ru.pathcreator.pyc.rpc.core.collection.WaitersTable;
import ru.pathcreator.pyc.rpc.core.config.RpcChannelConfig;
import ru.pathcreator.pyc.rpc.core.exception.RpcCallTimeoutException;
import ru.pathcreator.pyc.rpc.core.generator.CorrelationIdGenerator;
import ru.pathcreator.pyc.rpc.core.generator.YieldThenParkIdleStrategy;
import ru.pathcreator.pyc.rpc.core.wrapper.WrapperThread;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.LockSupport;

public final class RpcChannel implements AutoCloseable {

    private final WaitersTable waiters;
    private final Thread listenerThread;
    private final RpcChannelConfig config;
    private final Subscription subscription;
    private final FragmentHandler fragmentHandler;
    private final ConcurrentPublication publication;
    private final YieldThenParkIdleStrategy listenerIdleStrategy;
    private final RpcMethodRegistry methodRegistry = new RpcMethodRegistry();
    private final ThreadLocal<YieldThenParkIdleStrategy> publisherIdleStrategies;
    private final CorrelationIdGenerator correlationIds = new CorrelationIdGenerator();
    private final Int2ObjectHashMap<RpcRequestHandler> requestHandlers = new Int2ObjectHashMap<>();

    private volatile boolean running;

    RpcChannel(
            final Aeron aeron,
            final RpcChannelConfig config
    ) {
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
        this.fragmentHandler = new FragmentAssembler(this::onFragment);
        this.listenerThread = new Thread(this::listenerLoop, "rpc-channel-listener-" + config.streamId());
        this.listenerThread.start();
    }

    public <T> void registerResponseDecoder(
            final int responseMessageTypeId,
            final RpcResponseDecoder<T> decoder
    ) {
        methodRegistry.registerResponseDecoder(responseMessageTypeId, decoder);
    }

    public void registerRequestHandler(
            final int requestMessageTypeId,
            final RpcRequestHandler handler
    ) {
        this.requestHandlers.put(requestMessageTypeId, handler);
    }

    @SuppressWarnings("unchecked")
    public <T> T send(
            final long timeoutNs,
            final int payloadLength,
            final int requestMessageTypeId,
            final MutableDirectBuffer requestBuffer
    ) {
        return (T) this.decodeResponse(
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
        this.waiters.put(correlationId, wrapper);
        try {
            this.publish(0, payloadLength, correlationId, requestMessageTypeId, requestBuffer);
        } catch (final RuntimeException problem) {
            this.waiters.remove(correlationId);
            throw problem;
        }
        return this.await(timeoutNs, correlationId, wrapper);
    }

    public void reply(
            final int payloadLength,
            final long correlationId,
            final int responseMessageTypeId,
            final MutableDirectBuffer responseBuffer
    ) {
        this.publish(RpcEnvelope.FLAG_RESPONSE, payloadLength, correlationId, responseMessageTypeId, responseBuffer);
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
    }

    private void publish(
            final int flags,
            final int payloadLength,
            final long correlationId,
            final int requestMessageTypeId,
            final MutableDirectBuffer requestBuffer
    ) {
        final int totalLength = RpcEnvelope.HEADER_LENGTH + payloadLength;
        final YieldThenParkIdleStrategy idleStrategy = this.publisherIdleStrategies.get();
        idleStrategy.reset();
        RpcEnvelope.encode(flags, payloadLength, requestMessageTypeId, correlationId, requestBuffer);
        while (this.running) {
            final long result = this.publication.offer(requestBuffer, 0, totalLength);
            if (result > 0) {
                return;
            }
            idleStrategy.idle();
        }
        throw new IllegalStateException("channel closed");
    }

    private void listenerLoop() {
        final YieldThenParkIdleStrategy idleStrategy = this.listenerIdleStrategy;
        idleStrategy.reset();
        while (this.running) {
            final int fragments = this.subscription.poll(this.fragmentHandler, this.config.fragmentLimit());
            if (fragments == 0) {
                idleStrategy.idle();
            } else {
                idleStrategy.reset();
            }
        }
    }

    private void onFragment(
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final Header header
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
        wrapper.wrap(offset, length, buffer);
        LockSupport.unpark(wrapper.thread());
    }

    private UnsafeBuffer await(
            final long timeoutNs,
            final long correlationId,
            final WrapperThread wrapper
    ) {
        final long deadline = System.nanoTime() + timeoutNs;
        while (wrapper.bytes() == null) {
            final long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                if (this.waiters.remove(correlationId) == wrapper) {
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

    private Object decodeResponse(
            final UnsafeBuffer response
    ) {
        final int flags = RpcEnvelope.flags(0, response);
        final int payloadLength = RpcEnvelope.payloadLength(0, response);
        if ((flags & RpcEnvelope.FLAG_RESPONSE) == 0) {
            throw new IllegalStateException("response flag is not set");
        }
        if (payloadLength < 0 || payloadLength > response.capacity() - RpcEnvelope.HEADER_LENGTH) {
            throw new IllegalStateException("invalid payload length");
        }
        if ((flags & RpcEnvelope.FLAG_ERROR) != 0) {
            throw new IllegalStateException(
                    this.errorText(payloadLength, response)
            );
        }
        return this.methodRegistry.decode(
                payloadLength,
                response,
                RpcEnvelope.messageTypeId(0, response)
        );
    }

    private String errorText(
            final int length,
            final DirectBuffer buffer
    ) {
        final byte[] text = new byte[length];
        buffer.getBytes(RpcEnvelope.HEADER_LENGTH, text);
        return new String(text, StandardCharsets.UTF_8);
    }
}