/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.gdx;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Ordered render-thread work with a hard per-frame count and time budget.
 *
 * <p>libGDX executes every runnable already posted at the start of one frame.
 * Recovery can publish a dense replay of historical table events, so posting
 * one runnable per event lets that whole replay monopolise a frame and visibly
 * stop modal GIFs. This pump posts only one batch at a time and schedules any
 * remainder for the next frame while preserving exact FIFO order.</p>
 */
final class GdxFrameTaskPump implements AutoCloseable {

    private final ArrayDeque<Entry> pending = new ArrayDeque<>();
    private final Consumer<Runnable> frameScheduler;
    private final LongSupplier nanoTime;
    private final int maxTasksPerFrame;
    private final long frameBudgetNanos;
    private boolean scheduled;
    private boolean closed;

    GdxFrameTaskPump(Consumer<Runnable> frameScheduler,
            LongSupplier nanoTime, int maxTasksPerFrame,
            long frameBudgetNanos) {
        this.frameScheduler = Objects.requireNonNull(frameScheduler,
                "frameScheduler");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        if (maxTasksPerFrame <= 0) {
            throw new IllegalArgumentException("maxTasksPerFrame <= 0");
        }
        if (frameBudgetNanos <= 0L) {
            throw new IllegalArgumentException("frameBudgetNanos <= 0");
        }
        this.maxTasksPerFrame = maxTasksPerFrame;
        this.frameBudgetNanos = frameBudgetNanos;
    }

    void submit(Runnable task, Consumer<Throwable> failureHandler) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(failureHandler, "failureHandler");
        boolean schedule = false;
        synchronized (this) {
            if (closed) {
                failureHandler.accept(closedFailure());
                return;
            }
            pending.addLast(new Entry(task, failureHandler));
            if (!scheduled) {
                scheduled = true;
                schedule = true;
            }
        }
        if (schedule) scheduleNextFrame();
    }

    private void drainFrame() {
        long startedAt = nanoTime.getAsLong();
        int processed = 0;
        while (true) {
            Entry entry;
            synchronized (this) {
                entry = pending.pollFirst();
                if (entry == null) {
                    scheduled = false;
                    return;
                }
            }
            try {
                entry.task().run();
            } catch (Throwable failure) {
                entry.failureHandler().accept(failure);
            }
            processed++;

            boolean hasMore;
            synchronized (this) {
                hasMore = !pending.isEmpty();
                if (!hasMore) {
                    scheduled = false;
                    return;
                }
            }
            if (processed >= maxTasksPerFrame
                    || nanoTime.getAsLong() - startedAt >= frameBudgetNanos) {
                scheduleNextFrame();
                return;
            }
        }
    }

    private void scheduleNextFrame() {
        try {
            frameScheduler.accept(this::drainFrame);
        } catch (Throwable failure) {
            failPending(failure);
        }
    }

    @Override
    public void close() {
        failPending(closedFailure());
    }

    private void failPending(Throwable failure) {
        ArrayDeque<Entry> abandoned = new ArrayDeque<>();
        synchronized (this) {
            if (closed && pending.isEmpty()) return;
            closed = true;
            scheduled = false;
            abandoned.addAll(pending);
            pending.clear();
        }
        while (!abandoned.isEmpty()) {
            abandoned.removeFirst().failureHandler().accept(failure);
        }
    }

    private static IllegalStateException closedFailure() {
        return new IllegalStateException("GDX render event pump is closed");
    }

    private record Entry(Runnable task,
            Consumer<Throwable> failureHandler) { }
}
