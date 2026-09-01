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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongFunction;

/**
 * The single optional presentation outlet owned by a live table.
 *
 * Swing tables leave it detached, making every publication an immediate no-op.
 * A GDX table attaches exactly one presentation before the hand starts. This
 * keeps renderer checks out of the dealer and provides the same completion
 * stage at the few points where the classic flow already waits for visuals.
 */
public final class TableEventBridge implements AutoCloseable {

    private static final CompletionStage<Void> NO_RENDERER =
            CompletableFuture.completedFuture(null);

    private final AtomicReference<TablePresentation> presentation = new AtomicReference<>();

    public CompletionStage<Void> attach(TableRenderer renderer, TableSnapshot initialState) {
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(initialState, "initialState");
        TablePresentation candidate = new TablePresentation(renderer);
        if (!presentation.compareAndSet(null, candidate)) {
            candidate.close();
            throw new IllegalStateException("A table renderer is already attached");
        }
        CompletionStage<Void> opening;
        try {
            opening = candidate.open(initialState);
        } catch (RuntimeException error) {
            presentation.compareAndSet(candidate, null);
            candidate.close();
            throw error;
        }
        return Objects.requireNonNull(opening, "renderer opening barrier")
                .whenComplete((ignored, error) -> {
                    if (error != null && presentation.compareAndSet(candidate, null)) {
                        candidate.close();
                    }
                });
    }

    public boolean isAttached() {
        return presentation.get() != null;
    }

    public CompletionStage<Void> publish(
            LongFunction<? extends TableVisualEvent> eventFactory) {
        Objects.requireNonNull(eventFactory, "eventFactory");
        TablePresentation current = presentation.get();
        return current == null ? NO_RENDERER : current.publish(eventFactory);
    }

    @Override
    public void close() {
        TablePresentation current = presentation.getAndSet(null);
        if (current != null) {
            current.close();
        }
    }
}
