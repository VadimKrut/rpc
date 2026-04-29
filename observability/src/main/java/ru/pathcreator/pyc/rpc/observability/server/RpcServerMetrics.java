package ru.pathcreator.pyc.rpc.observability.server;

import ru.pathcreator.pyc.rpc.contract.RpcMethodContract;
import ru.pathcreator.pyc.rpc.observability.InternalMetricsSupport;
import ru.pathcreator.pyc.rpc.server.RpcServerBuilder;
import ru.pathcreator.pyc.rpc.server.listener.RpcServerListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class RpcServerMetrics {

    private final ConcurrentHashMap<RpcMethodContract<?, ?>, MethodMetrics> methods = new ConcurrentHashMap<>();
    private final RpcServerListener listener = new RpcServerListener() {
        @Override
        public void onStart(
                final RpcMethodContract<?, ?> method,
                final long correlationId,
                final int requestPayloadLength
        ) {
            metricsFor(method).recordStart();
        }

        @Override
        public void onSuccess(
                final RpcMethodContract<?, ?> method,
                final long correlationId,
                final long latencyNs,
                final int requestPayloadLength,
                final int responsePayloadLength,
                final int statusCode
        ) {
            metricsFor(method).recordSuccess(latencyNs, requestPayloadLength, responsePayloadLength);
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
            metricsFor(method).recordFailure(latencyNs, requestPayloadLength, responsePayloadLength, statusCode);
        }
    };

    public RpcServerBuilder attach(
            final RpcServerBuilder builder
    ) {
        if (builder == null) {
            throw new IllegalArgumentException("builder must not be null");
        }
        return builder.listener(this.listener);
    }

    public RpcServerListener listener() {
        return this.listener;
    }

    public RpcServerMetricsSnapshot snapshot() {
        final List<RpcServerMethodMetricsSnapshot> snapshots = new ArrayList<>(this.methods.size());
        for (final MethodMetrics metrics : this.methods.values()) {
            snapshots.add(metrics.snapshot());
        }
        snapshots.sort(Comparator.comparing(RpcServerMethodMetricsSnapshot::methodName));
        return new RpcServerMetricsSnapshot(List.copyOf(snapshots));
    }

    private MethodMetrics metricsFor(
            final RpcMethodContract<?, ?> method
    ) {
        return this.methods.computeIfAbsent(method, MethodMetrics::new);
    }

    private static final class MethodMetrics {

        private final RpcMethodContract<?, ?> method;
        private final LongAdder requests = new LongAdder();
        private final LongAdder successes = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder clientErrors = new LongAdder();
        private final LongAdder serverErrors = new LongAdder();
        private final LongAdder otherErrors = new LongAdder();
        private final LongAdder inFlight = new LongAdder();
        private final AtomicLong maxInFlight = new AtomicLong();
        private final LongAdder totalLatencyNs = new LongAdder();
        private final AtomicLong maxLatencyNs = new AtomicLong();
        private final LongAdder totalRequestPayloadBytes = new LongAdder();
        private final LongAdder totalResponsePayloadBytes = new LongAdder();

        private MethodMetrics(
                final RpcMethodContract<?, ?> method
        ) {
            this.method = method;
        }

        private void recordSuccess(
                final long latencyNs,
                final int requestPayloadLength,
                final int responsePayloadLength
        ) {
            this.successes.increment();
            this.totalRequestPayloadBytes.add(requestPayloadLength);
            this.totalResponsePayloadBytes.add(responsePayloadLength);
            this.onFinish(latencyNs);
        }

        private void recordFailure(
                final long latencyNs,
                final int requestPayloadLength,
                final int responsePayloadLength,
                final int statusCode
        ) {
            this.failures.increment();
            this.totalRequestPayloadBytes.add(requestPayloadLength);
            this.totalResponsePayloadBytes.add(responsePayloadLength);
            if (statusCode >= 400 && statusCode < 500) {
                this.clientErrors.increment();
            } else if (statusCode >= 500 && statusCode < 600) {
                this.serverErrors.increment();
            } else {
                this.otherErrors.increment();
            }
            this.onFinish(latencyNs);
        }

        private void recordStart() {
            this.requests.increment();
            this.inFlight.increment();
            InternalMetricsSupport.updateMax(this.maxInFlight, this.inFlight.sum());
        }

        private void onFinish(
                final long latencyNs
        ) {
            this.totalLatencyNs.add(latencyNs);
            InternalMetricsSupport.updateMax(this.maxLatencyNs, latencyNs);
            this.inFlight.decrement();
        }

        private RpcServerMethodMetricsSnapshot snapshot() {
            return new RpcServerMethodMetricsSnapshot(
                    this.method.name(),
                    this.method.requestMessageTypeId(),
                    this.method.responseMessageTypeId(),
                    this.requests.sum(),
                    this.successes.sum(),
                    this.failures.sum(),
                    this.clientErrors.sum(),
                    this.serverErrors.sum(),
                    this.otherErrors.sum(),
                    this.inFlight.sum(),
                    this.maxInFlight.get(),
                    this.totalLatencyNs.sum(),
                    this.maxLatencyNs.get(),
                    this.totalRequestPayloadBytes.sum(),
                    this.totalResponsePayloadBytes.sum()
            );
        }
    }
}