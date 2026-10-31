/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Low-overhead JVM telemetry for the optional on-screen debug overlay.
 *
 * <p>The render thread only reads a cached immutable sample. Potentially slow
 * platform telemetry calls are isolated on one low-priority daemon so a platform/JDK
 * stall can never freeze input, animation or audio.</p>
 */
final class GdxProcessPerformanceSampler {

    static final long SAMPLE_INTERVAL_NANOS = 1_000_000_000L;
    private static final long BYTES_PER_MEBIBYTE = 1024L * 1024L;
    private static final Executor SAMPLE_EXECUTOR =
            Executors.newSingleThreadExecutor(task -> {
                Thread worker = new Thread(task,
                        "coronapoker-performance-monitor");
                worker.setDaemon(true);
                worker.setPriority(Thread.MIN_PRIORITY);
                return worker;
            });

    private final Executor executor;
    private final Probe probe;
    private final AtomicBoolean samplePending = new AtomicBoolean();
    private volatile Sample sample = Sample.EMPTY;
    private volatile long nextSampleAt = Long.MIN_VALUE;

    GdxProcessPerformanceSampler() {
        this(SAMPLE_EXECUTOR, new JvmProbe(
                () -> ProcessHandle.current().info().totalCpuDuration(),
                Runtime.getRuntime()));
    }

    GdxProcessPerformanceSampler(Executor executor, Probe probe) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.probe = Objects.requireNonNull(probe, "probe");
    }

    String label(GdxGameText text, long nowNanos) {
        requestSampleIfDue(nowNanos);
        Sample current = sample;
        String cpu = current.cpuPercent() < 0 ? "--"
                : Integer.toString(current.cpuPercent());
        return text.translate("gdx.performance.overlay", cpu,
                current.heapMemoryMebibytes());
    }

    boolean samplePending() {
        return samplePending.get();
    }

    private void requestSampleIfDue(long nowNanos) {
        if (nextSampleAt != Long.MIN_VALUE && nowNanos < nextSampleAt) return;
        if (!samplePending.compareAndSet(false, true)) return;
        nextSampleAt = safeNextSampleAt(nowNanos);
        try {
            executor.execute(() -> {
                try {
                    Sample next = probe.sample();
                    if (next != null) sample = next;
                } catch (RuntimeException | LinkageError ignored) {
                    // Optional diagnostics must never destabilize the game.
                } finally {
                    samplePending.set(false);
                }
            });
        } catch (RuntimeException rejected) {
            samplePending.set(false);
        }
    }

    private static long safeNextSampleAt(long nowNanos) {
        return nowNanos > Long.MAX_VALUE - SAMPLE_INTERVAL_NANOS
                ? Long.MAX_VALUE : nowNanos + SAMPLE_INTERVAL_NANOS;
    }

    static int normalizedCpuPercent(long currentCpuNanos,
            long previousCpuNanos, long currentWallNanos,
            long previousWallNanos, int processors) {
        if (currentCpuNanos < 0L || previousCpuNanos < 0L
                || currentCpuNanos < previousCpuNanos
                || currentWallNanos <= previousWallNanos
                || processors <= 0) return -1;
        double load = (currentCpuNanos - previousCpuNanos)
                / (double) (currentWallNanos - previousWallNanos)
                / processors;
        if (!Double.isFinite(load) || load < 0d) return -1;
        return (int) Math.round(Math.min(1d, load) * 100d);
    }

    static long processCpuNanos(Supplier<Optional<Duration>> cpuDuration) {
        if (cpuDuration == null) return -1L;
        Optional<Duration> duration = cpuDuration.get();
        if (duration == null || duration.isEmpty()) return -1L;
        try {
            return duration.get().toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    static long heapMemoryBytes(Runtime runtime) {
        if (runtime == null) return 0L;
        return Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
    }

    private static long bytesToMebibytes(long bytes) {
        if (bytes <= 0L) return 0L;
        long rounding = BYTES_PER_MEBIBYTE / 2L;
        long rounded = bytes > Long.MAX_VALUE - rounding
                ? Long.MAX_VALUE : bytes + rounding;
        return Math.max(1L, rounded / BYTES_PER_MEBIBYTE);
    }

    @FunctionalInterface
    interface Probe {
        Sample sample();
    }

    record Sample(int cpuPercent, long heapMemoryMebibytes) {
        static final Sample EMPTY = new Sample(-1, 0L);
    }

    private static final class JvmProbe implements Probe {
        private final Supplier<Optional<Duration>> cpuDuration;
        private final Runtime runtime;
        private final int processors;
        private long previousCpuNanos = -1L;
        private long previousWallNanos = -1L;

        JvmProbe(Supplier<Optional<Duration>> cpuDuration, Runtime runtime) {
            this.cpuDuration = Objects.requireNonNull(cpuDuration,
                    "cpuDuration");
            this.runtime = runtime;
            processors = Math.max(1, runtime.availableProcessors());
        }

        @Override
        public Sample sample() {
            long currentCpuNanos = processCpuNanos(cpuDuration);
            long currentWallNanos = System.nanoTime();
            int cpu = normalizedCpuPercent(currentCpuNanos,
                    previousCpuNanos, currentWallNanos, previousWallNanos,
                    processors);
            previousCpuNanos = currentCpuNanos;
            previousWallNanos = currentWallNanos;
            return new Sample(cpu,
                    bytesToMebibytes(heapMemoryBytes(runtime)));
        }
    }
}
