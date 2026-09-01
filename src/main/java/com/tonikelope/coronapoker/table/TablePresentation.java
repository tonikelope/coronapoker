/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.table;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;

/** Assigns one monotonic visual sequence to every renderer event. */
public final class TablePresentation implements AutoCloseable {

    private final TableRenderer renderer;
    private final AtomicLong sequence = new AtomicLong();

    public TablePresentation(TableRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public CompletionStage<Void> open(TableSnapshot initialState) {
        return renderer.open(Objects.requireNonNull(initialState, "initialState"));
    }

    public synchronized CompletionStage<Void> publish(
            LongFunction<? extends TableVisualEvent> eventFactory) {
        Objects.requireNonNull(eventFactory, "eventFactory");
        long next = sequence.incrementAndGet();
        TableVisualEvent event = Objects.requireNonNull(eventFactory.apply(next), "event");
        if (event.sequence() != next) {
            throw new IllegalArgumentException("Visual event sequence was not assigned by TablePresentation");
        }
        return Objects.requireNonNull(renderer.render(event), "renderer barrier");
    }

    public long lastSequence() {
        return sequence.get();
    }

    @Override
    public void close() {
        renderer.close();
    }
}
