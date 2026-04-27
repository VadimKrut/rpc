package ru.pathcreator.pyc.rpc.core.benchmark;

import com.sun.management.OperatingSystemMXBean;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import ru.pathcreator.pyc.rpc.core.RpcChannel;
import ru.pathcreator.pyc.rpc.core.RpcRuntime;
import ru.pathcreator.pyc.rpc.core.codex.RpcEnvelope;
import ru.pathcreator.pyc.rpc.core.config.RpcChannelConfig;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.LongAdder;

public final class RpcBenchmarkMain {

    private static final int REQUEST_MESSAGE_TYPE_ID = 1;
    private static final int RESPONSE_MESSAGE_TYPE_ID = 2;
    private static final int REQUEST_PAYLOAD_LENGTH = Long.BYTES * 2;
    private static final int RESPONSE_PAYLOAD_LENGTH = Long.BYTES;
    private static final long TIMEOUT_NS = 5_000_000_000L;

    private RpcBenchmarkMain() {
    }

    public static void main(final String[] args) throws Exception {
        final int channels = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        final int warmupSeconds = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        final int measureSeconds = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        final int fragmentLimit = args.length > 3 ? Integer.parseInt(args[3]) : 32;
        final int listenerMaxYields = args.length > 4 ? Integer.parseInt(args[4]) : 512;
        final int publisherMaxYields = args.length > 5 ? Integer.parseInt(args[5]) : 20_000;
        final int waitersInitialCapacity = args.length > 6 ? Integer.parseInt(args[6]) : 1_048_576;
        final float waitersLoadFactor = args.length > 7 ? Float.parseFloat(args[7]) : 0.75f;

        final Snapshot before = capture();
        final long cpuStartedAt = processCpuTimeNs();
        final Result result = runBenchmark(
                channels,
                warmupSeconds,
                measureSeconds,
                fragmentLimit,
                listenerMaxYields,
                publisherMaxYields,
                waitersInitialCapacity,
                waitersLoadFactor
        );
        final long cpuSpentNs = processCpuTimeNs() - cpuStartedAt;
        forceGc();
        final Snapshot after = capture();
        printResult(
                channels,
                warmupSeconds,
                measureSeconds,
                fragmentLimit,
                listenerMaxYields,
                publisherMaxYields,
                waitersInitialCapacity,
                waitersLoadFactor,
                result,
                cpuSpentNs,
                before,
                after
        );
    }

    private static Result runBenchmark(
            final int channels,
            final int warmupSeconds,
            final int measureSeconds,
            final int fragmentLimit,
            final int listenerMaxYields,
            final int publisherMaxYields,
            final int waitersInitialCapacity,
            final float waitersLoadFactor
    ) throws Exception {
        final int expectedOps = Math.max(1, channels * measureSeconds * 50_000);
        final long[] latenciesNs = new long[expectedOps];
        final IndexRecorder recorder = new IndexRecorder(latenciesNs);
        final LongAdder completed = new LongAdder();
        final LongAdder errors = new LongAdder();
        final long warmupNs = warmupSeconds * 1_000_000_000L;
        final long measureNs = measureSeconds * 1_000_000_000L;
        final long totalNs = warmupNs + measureNs;

        try (RpcRuntime runtime = RpcRuntime.launchEmbedded()) {
            final RpcChannel[] clients = new RpcChannel[channels];
            final RpcChannel[] servers = new RpcChannel[channels];

            for (int index = 0; index < channels; index++) {
                final int basePort = 20_121 + index * 2;
                final int streamId = 1_001 + index;
                final RpcChannelConfig clientConfig = new RpcChannelConfig(
                        "aeron:udp?endpoint=localhost:" + basePort,
                        "aeron:udp?endpoint=localhost:" + (basePort + 1),
                        streamId,
                        fragmentLimit,
                        listenerMaxYields,
                        publisherMaxYields,
                        waitersInitialCapacity,
                        waitersLoadFactor
                );
                final RpcChannelConfig serverConfig = new RpcChannelConfig(
                        "aeron:udp?endpoint=localhost:" + (basePort + 1),
                        "aeron:udp?endpoint=localhost:" + basePort,
                        streamId,
                        fragmentLimit,
                        listenerMaxYields,
                        publisherMaxYields,
                        waitersInitialCapacity,
                        waitersLoadFactor
                );

                final RpcChannel client = runtime.createChannel(clientConfig);
                final RpcChannel server = runtime.createChannel(serverConfig);
                client.registerResponseDecoder(
                        RESPONSE_MESSAGE_TYPE_ID,
                        (offset, length, buffer) -> decodeResponse(buffer, offset, length)
                );
                final UnsafeBuffer responseBuffer = new UnsafeBuffer(
                        ByteBuffer.allocateDirect(RpcEnvelope.HEADER_LENGTH + RESPONSE_PAYLOAD_LENGTH)
                );
                server.registerRequestHandler(REQUEST_MESSAGE_TYPE_ID, (offset, length, correlationId, buffer) -> {
                    final long a = buffer.getLong(offset, ByteOrder.LITTLE_ENDIAN);
                    final long b = buffer.getLong(offset + Long.BYTES, ByteOrder.LITTLE_ENDIAN);
                    responseBuffer.putLong(RpcEnvelope.HEADER_LENGTH, a + b, ByteOrder.LITTLE_ENDIAN);
                    server.reply(RESPONSE_PAYLOAD_LENGTH, correlationId, RESPONSE_MESSAGE_TYPE_ID, responseBuffer);
                });
                clients[index] = client;
                servers[index] = server;
            }

            final CountDownLatch ready = new CountDownLatch(channels);
            final CountDownLatch start = new CountDownLatch(1);
            final CountDownLatch done = new CountDownLatch(channels);

            for (int channelIndex = 0; channelIndex < channels; channelIndex++) {
                final RpcChannel client = clients[channelIndex];
                final int workerIndex = channelIndex;
                final Thread worker = new Thread(() -> {
                    final UnsafeBuffer requestBuffer = new UnsafeBuffer(
                            ByteBuffer.allocateDirect(RpcEnvelope.HEADER_LENGTH + REQUEST_PAYLOAD_LENGTH)
                    );
                    try {
                        ready.countDown();
                        start.await();
                        final long startedAt = System.nanoTime();
                        final long warmupDeadline = startedAt + warmupNs;
                        final long endAt = startedAt + totalNs;
                        int sequence = 0;
                        while (System.nanoTime() < endAt) {
                            final long a = (((long) workerIndex) << 32) | (sequence & 0xffff_ffffL);
                            final long b = 42L;
                            encodeRequest(requestBuffer, a, b);
                            final long opStartedAt = System.nanoTime();
                            final long response = client.send(
                                    TIMEOUT_NS,
                                    REQUEST_PAYLOAD_LENGTH,
                                    REQUEST_MESSAGE_TYPE_ID,
                                    requestBuffer
                            );
                            final long spent = System.nanoTime() - opStartedAt;
                            if (response != a + b) {
                                errors.increment();
                            }
                            if (opStartedAt + spent >= warmupDeadline) {
                                completed.increment();
                                recorder.record(spent);
                            }
                            sequence += 1;
                        }
                    } catch (final Exception e) {
                        errors.increment();
                    } finally {
                        done.countDown();
                    }
                }, "rpc-benchmark-worker-" + channelIndex);
                worker.start();
            }

            ready.await();
            final long stageStartedAt = System.nanoTime();
            start.countDown();
            done.await();
            final long stageNs = System.nanoTime() - stageStartedAt;
            return new Result(stageNs, completed.sum(), errors.sum(), recorder.snapshot());
        }
    }

    private static void encodeRequest(final UnsafeBuffer buffer, final long a, final long b) {
        buffer.putLong(RpcEnvelope.HEADER_LENGTH, a, ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(RpcEnvelope.HEADER_LENGTH + Long.BYTES, b, ByteOrder.LITTLE_ENDIAN);
    }

    private static Long decodeResponse(final DirectBuffer buffer, final int offset, final int length) {
        if (length != RESPONSE_PAYLOAD_LENGTH) {
            throw new IllegalStateException("invalid response payload length: " + length);
        }
        return buffer.getLong(offset, ByteOrder.LITTLE_ENDIAN);
    }

    private static void printResult(
            final int channels,
            final int warmupSeconds,
            final int measureSeconds,
            final int fragmentLimit,
            final int listenerMaxYields,
            final int publisherMaxYields,
            final int waitersInitialCapacity,
            final float waitersLoadFactor,
            final Result result,
            final long cpuSpentNs,
            final Snapshot before,
            final Snapshot after
    ) {
        final long[] sorted = result.latenciesNs.clone();
        Arrays.sort(sorted);
        final double achieved = result.completed / (double) measureSeconds;
        final double perChannel = achieved / channels;
        final int processors = Runtime.getRuntime().availableProcessors();
        final double cpuCores = cpuSpentNs / (double) result.stageNs;
        final double cpuPercentOfMachine = processors == 0 ? 0.0D : cpuCores / processors * 100.0D;

        System.out.printf(Locale.US,
                "channels=%d warmup=%ds measure=%ds fragmentLimit=%d listenerMaxYields=%d publisherMaxYields=%d waitersInitialCapacity=%d waitersLoadFactor=%.2f%n",
                channels,
                warmupSeconds,
                measureSeconds,
                fragmentLimit,
                listenerMaxYields,
                publisherMaxYields,
                waitersInitialCapacity,
                waitersLoadFactor);
        System.out.printf(Locale.US,
                "achieved=%.2f ops/s perChannel=%.2f errors=%d totalMs=%.3f cpuCores=%.3f machine=%.1f%%%n",
                achieved,
                perChannel,
                result.errors,
                result.stageNs / 1_000_000.0D,
                cpuCores,
                cpuPercentOfMachine);
        System.out.printf(Locale.US,
                "latency p50=%.3f us p90=%.3f us p99=%.3f us max=%.3f us avg=%.3f us%n",
                toMicros(percentile(sorted, 0.50D)),
                toMicros(percentile(sorted, 0.90D)),
                toMicros(percentile(sorted, 0.99D)),
                sorted.length == 0 ? 0.0D : toMicros(sorted[sorted.length - 1]),
                toMicros(average(result.latenciesNs)));
        System.out.printf(Locale.US,
                "memory before heapUsed=%d heapCommitted=%d directUsed=%d directCount=%d mappedUsed=%d mappedCount=%d%n",
                before.heapUsed,
                before.heapCommitted,
                before.directUsed,
                before.directCount,
                before.mappedUsed,
                before.mappedCount);
        System.out.printf(Locale.US,
                "memory after heapUsed=%d heapCommitted=%d directUsed=%d directCount=%d mappedUsed=%d mappedCount=%d%n",
                after.heapUsed,
                after.heapCommitted,
                after.directUsed,
                after.directCount,
                after.mappedUsed,
                after.mappedCount);
        System.out.printf(Locale.US,
                "memory delta heapUsed=%d heapCommitted=%d directUsed=%d directCount=%d mappedUsed=%d mappedCount=%d%n",
                after.heapUsed - before.heapUsed,
                after.heapCommitted - before.heapCommitted,
                after.directUsed - before.directUsed,
                after.directCount - before.directCount,
                after.mappedUsed - before.mappedUsed,
                after.mappedCount - before.mappedCount);
    }

    private static long percentile(final long[] sorted, final double p) {
        if (sorted.length == 0) {
            return 0L;
        }
        final int index = Math.min(sorted.length - 1, Math.max(0, (int) Math.ceil(p * sorted.length) - 1));
        return sorted[index];
    }

    private static double average(final long[] values) {
        if (values.length == 0) {
            return 0.0D;
        }
        double total = 0.0D;
        for (final long value : values) {
            total += value;
        }
        return total / values.length;
    }

    private static double toMicros(final double nanos) {
        return nanos / 1_000.0D;
    }

    private static long processCpuTimeNs() {
        final java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof OperatingSystemMXBean osBean) {
            return osBean.getProcessCpuTime();
        }
        return 0L;
    }

    private static Snapshot capture() {
        final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        final MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        final List<BufferPoolMXBean> pools = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
        long directUsed = 0L;
        long directCount = 0L;
        long mappedUsed = 0L;
        long mappedCount = 0L;
        for (final BufferPoolMXBean pool : pools) {
            if ("direct".equals(pool.getName())) {
                directUsed = pool.getMemoryUsed();
                directCount = pool.getCount();
            } else if ("mapped".equals(pool.getName())) {
                mappedUsed = pool.getMemoryUsed();
                mappedCount = pool.getCount();
            }
        }
        return new Snapshot(heap.getUsed(), heap.getCommitted(), directUsed, directCount, mappedUsed, mappedCount);
    }

    private static void forceGc() throws InterruptedException {
        System.gc();
        Thread.sleep(500L);
        System.gc();
        Thread.sleep(500L);
    }

    private static final class IndexRecorder {
        private final long[] values;
        private int index;

        private IndexRecorder(final long[] values) {
            this.values = values;
        }

        private synchronized void record(final long value) {
            if (this.index < this.values.length) {
                this.values[this.index] = value;
                this.index += 1;
            }
        }

        private synchronized long[] snapshot() {
            return Arrays.copyOf(this.values, this.index);
        }
    }

    private record Result(long stageNs, long completed, long errors, long[] latenciesNs) {
    }

    private record Snapshot(
            long heapUsed,
            long heapCommitted,
            long directUsed,
            long directCount,
            long mappedUsed,
            long mappedCount
    ) {
    }
}