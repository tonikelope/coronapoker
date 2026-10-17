/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Native replacement for Swing's fatal JVM-deadlock detector. */
final class JvmDeadlockWatchdog implements AutoCloseable {

    private final long intervalMillis;
    private final Supplier<String> probe;
    private final Consumer<String> handler;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean reported = new AtomicBoolean();
    private ScheduledExecutorService executor;

    JvmDeadlockWatchdog(long intervalMillis, Supplier<String> probe,
            Consumer<String> handler) {
        if (intervalMillis <= 0L) {
            throw new IllegalArgumentException("intervalMillis must be positive");
        }
        this.intervalMillis = intervalMillis;
        this.probe = Objects.requireNonNull(probe, "probe");
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    void start() {
        if (!started.compareAndSet(false, true)) return;
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "coronapoker-deadlock-watchdog");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::poll, intervalMillis,
                intervalMillis, TimeUnit.MILLISECONDS);
    }

    private void poll() {
        if (reported.get()) return;
        try {
            String report = probe.get();
            if (report != null && !report.isBlank()
                    && reported.compareAndSet(false, true)) {
                handler.accept(report);
            }
        } catch (Throwable failure) {
            // A diagnostic watchdog must never become an application failure.
            System.err.println("Deadlock watchdog probe failed: " + failure);
        }
    }

    static String detectJvmDeadlock() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long[] ids = threads.findDeadlockedThreads();
        if (ids == null || ids.length == 0) return null;
        StringBuilder report = new StringBuilder(512);
        ThreadInfo[] infos = threads.getThreadInfo(ids, true, true);
        for (ThreadInfo info : infos) {
            if (info != null) report.append(info).append(System.lineSeparator());
        }
        return report.toString();
    }

    @Override
    public void close() {
        ScheduledExecutorService running = executor;
        executor = null;
        if (running != null) running.shutdownNow();
    }
}
