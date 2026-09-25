/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.table;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;

/** Assigns one monotonic visual sequence to every renderer event. */
public final class TablePresentation implements AutoCloseable {

    private final TableRenderer renderer;
    private final AtomicLong sequence = new AtomicLong();
    private final ArrayDeque<PendingEvent> openingEvents = new ArrayDeque<>();
    private CompletableFuture<Void> openingBarrier;
    private boolean openingStarted;
    private boolean opened;
    private boolean closed;

    public TablePresentation(TableRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public CompletionStage<Void> open(TableSnapshot initialState) {
        Objects.requireNonNull(initialState, "initialState");
        CompletableFuture<Void> ready = new CompletableFuture<>();
        CompletionStage<Void> rendererOpening;
        synchronized (this) {
            if (closed) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Table presentation is closed"));
            }
            if (openingStarted) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Table presentation is already opening"));
            }
            openingStarted = true;
            openingBarrier = ready;
            try {
                rendererOpening = Objects.requireNonNull(
                        renderer.open(initialState), "renderer opening barrier");
            } catch (Throwable failure) {
                failOpening(failure, ready);
                return ready;
            }
        }
        rendererOpening.whenComplete((ignored, failure) -> {
            synchronized (TablePresentation.this) {
                if (failure != null) {
                    failOpening(failure, ready);
                    return;
                }
                if (closed) {
                    failOpening(new IllegalStateException(
                            "Table presentation closed while opening"), ready);
                    return;
                }
                opened = true;
                while (!openingEvents.isEmpty()) {
                    PendingEvent pending = openingEvents.removeFirst();
                    dispatch(pending.event(), pending.barrier());
                }
                // The public opening barrier is completed only after every
                // event that raced native scene creation has been dispatched
                // in its original sequence. The dealer may start afterwards.
                ready.complete(null);
                openingBarrier = null;
            }
        });
        return ready;
    }

    public synchronized CompletionStage<Void> publish(
            LongFunction<? extends TableVisualEvent> eventFactory) {
        Objects.requireNonNull(eventFactory, "eventFactory");
        long next = sequence.incrementAndGet();
        TableVisualEvent event = Objects.requireNonNull(eventFactory.apply(next), "event");
        if (event.sequence() != next) {
            throw new IllegalArgumentException("Visual event sequence was not assigned by TablePresentation");
        }
        if (closed) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Table presentation is closed"));
        }
        if (!opened) {
            CompletableFuture<Void> barrier = new CompletableFuture<>();
            openingEvents.addLast(new PendingEvent(event, barrier));
            return barrier;
        }
        return Objects.requireNonNull(renderer.render(event), "renderer barrier");
    }

    public long lastSequence() {
        return sequence.get();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        IllegalStateException failure = new IllegalStateException(
                "Table presentation closed before pending events were rendered");
        while (!openingEvents.isEmpty()) {
            openingEvents.removeFirst().barrier().completeExceptionally(failure);
        }
        if (openingBarrier != null) {
            openingBarrier.completeExceptionally(failure);
            openingBarrier = null;
        }
        renderer.close();
    }

    private void dispatch(TableVisualEvent event,
            CompletableFuture<Void> barrier) {
        try {
            Objects.requireNonNull(renderer.render(event), "renderer barrier")
                    .whenComplete((ignored, failure) -> {
                        if (failure == null) barrier.complete(null);
                        else barrier.completeExceptionally(failure);
                    });
        } catch (Throwable failure) {
            barrier.completeExceptionally(failure);
        }
    }

    private void failOpening(Throwable failure, CompletableFuture<Void> ready) {
        while (!openingEvents.isEmpty()) {
            openingEvents.removeFirst().barrier().completeExceptionally(failure);
        }
        ready.completeExceptionally(failure);
        if (openingBarrier == ready) openingBarrier = null;
    }

    private record PendingEvent(TableVisualEvent event,
            CompletableFuture<Void> barrier) { }
}
