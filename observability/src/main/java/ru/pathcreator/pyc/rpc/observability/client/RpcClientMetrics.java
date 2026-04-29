package ru.pathcreator.pyc.rpc.observability.client;

import ru.pathcreator.pyc.rpc.client.RpcClientBuilder;
import ru.pathcreator.pyc.rpc.client.listener.RpcClientListener;
import ru.pathcreator.pyc.rpc.contract.RpcMethodContract;
import ru.pathcreator.pyc.rpc.core.exception.RpcCallTimeoutException;
import ru.pathcreator.pyc.rpc.core.exception.RpcPublishTimeoutException;
import ru.pathcreator.pyc.rpc.observability.InternalMetricsSupport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class RpcClientMetrics {

    private final ConcurrentHashMap<RpcMethodContract<?, ?>, MethodMetrics> methods = new ConcurrentHashMap<>();
    private final RpcClientListener listener = new RpcClientListener() {
        @Override
        public void onStart(
                final RpcMethodContract<?, ?> method,
                final long timeoutNs
        ) {
            metricsFor(method).recordStart();
        }

        @Override
        public void onSuccess(
                final RpcMethodContract<?, ?> method,
                final long timeoutNs,
                final long latencyNs,
                final int responsePayloadLength,
                final int statusCode
        ) {
            metricsFor(method).recordSuccess(latencyNs, responsePayloadLength);
        }

        @Override
        public void onRemoteError(
                final RpcMethodContract<?, ?> method,
                final long timeoutNs,
                final long latencyNs,
                final int responsePayloadLength,
                final int statusCode
        ) {
            metricsFor(method).recordRemoteError(latencyNs, responsePayloadLength, statusCode);
        }

        @Override
        public void onFailure(
                final RpcMethodContract<?, ?> method,
                final long timeoutNs,
                final long latencyNs,
                final Throwable error
        ) {
            metricsFor(method).recordFailure(latencyNs, error);
        }
    };

    public RpcClientBuilder attach(
            final RpcClientBuilder builder
    ) {
        if (builder == null) {
            throw new IllegalArgumentException("builder must not be null");
        }
        return builder.listener(this.listener);
    }

    public RpcClientListener listener() {
        return this.listener;
    }

    public RpcClientMetricsSnapshot snapshot() {
        final List<RpcClientMethodMetricsSnapshot> snapshots = new ArrayList<>(this.methods.size());
        for (final MethodMetrics metrics : this.methods.values()) {
            snapshots.add(metrics.snapshot());
        }
        snapshots.sort(Comparator.comparing(RpcClientMethodMetricsSnapshot::methodName));
        return new RpcClientMetricsSnapshot(List.copyOf(snapshots));
    }

    private MethodMetrics metricsFor(
            final RpcMethodContract<?, ?> method
    ) {
        return this.methods.computeIfAbsent(method, MethodMetrics::new);
    }

    private static final class MethodMetrics {

        private final RpcMethodContract<?, ?> method;
        private final LongAdder calls = new LongAdder();
        private final LongAdder successes = new LongAdder();
        private final LongAdder remoteErrors = new LongAdder();
        private final LongAdder timeouts = new LongAdder();
        private final LongAdder localFailures = new LongAdder();
        private final LongAdder inFlight = new LongAdder();
        private final AtomicLong maxInFlight = new AtomicLong();
        private final LongAdder totalLatencyNs = new LongAdder();
        private final AtomicLong maxLatencyNs = new AtomicLong();
        private final LongAdder totalResponsePayloadBytes = new LongAdder();
        private final LongAdder remoteClientErrors = new LongAdder();
        private final LongAdder remoteServerErrors = new LongAdder();
        private final LongAdder remoteOtherErrors = new LongAdder();

        private MethodMetrics(
                final RpcMethodContract<?, ?> method
        ) {
            this.method = method;
        }

        private void recordSuccess(
                final long latencyNs,
                final int responsePayloadLength
        ) {
            this.successes.increment();
            this.totalResponsePayloadBytes.add(responsePayloadLength);
            this.onFinish(latencyNs);
        }

        private void recordRemoteError(
                final long latencyNs,
                final int responsePayloadLength,
                final int statusCode
        ) {
            this.remoteErrors.increment();
            this.totalResponsePayloadBytes.add(responsePayloadLength);
            if (statusCode >= 400 && statusCode < 500) {
                this.remoteClientErrors.increment();
            } else if (statusCode >= 500 && statusCode < 600) {
                this.remoteServerErrors.increment();
            } else {
                this.remoteOtherErrors.increment();
            }
            this.onFinish(latencyNs);
        }

        private void recordFailure(
                final long latencyNs,
                final Throwable error
        ) {
            if (error instanceof RpcCallTimeoutException || error instanceof RpcPublishTimeoutException) {
                this.timeouts.increment();
            } else {
                this.localFailures.increment();
            }
            this.onFinish(latencyNs);
        }

        private void recordStart() {
            this.calls.increment();
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

        private RpcClientMethodMetricsSnapshot snapshot() {
            return new RpcClientMethodMetricsSnapshot(
                    this.method.name(),
                    this.method.requestMessageTypeId(),
                    this.method.responseMessageTypeId(),
                    this.calls.sum(),
                    this.successes.sum(),
                    this.remoteErrors.sum(),
                    this.timeouts.sum(),
                    this.localFailures.sum(),
                    this.inFlight.sum(),
                    this.maxInFlight.get(),
                    this.totalLatencyNs.sum(),
                    this.maxLatencyNs.get(),
                    this.totalResponsePayloadBytes.sum(),
                    this.remoteClientErrors.sum(),
                    this.remoteServerErrors.sum(),
                    this.remoteOtherErrors.sum()
            );
        }
    }
}