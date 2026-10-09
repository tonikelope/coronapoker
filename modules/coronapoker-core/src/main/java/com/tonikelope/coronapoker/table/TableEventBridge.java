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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongFunction;

/**
 * The single optional presentation outlet owned by a live table.
 *
 * Headless tables leave it detached, making every publication an immediate
 * no-op. A GDX table attaches exactly one presentation before the hand starts.
 * This keeps renderer checks out of the dealer and provides a completion stage
 * at the points where game flow waits for presentation barriers.
 */
public final class TableEventBridge implements AutoCloseable {

    private static final CompletionStage<Void> NO_RENDERER =
            CompletableFuture.completedFuture(null);

    private final AtomicReference<TablePresentation> presentation = new AtomicReference<>();
    private final AtomicReference<Consumer<TableVisualEvent>> observer
            = new AtomicReference<>(ignored -> { });
    private final AtomicBoolean localPublicationsSuppressed
            = new AtomicBoolean();

    /**
     * Installs the table-scoped observer used by protocol projections such as
     * live hot join. The observer sees the exact sequenced event before it is
     * handed to the renderer; it must never perform blocking presentation work.
     */
    public void observe(Consumer<TableVisualEvent> eventObserver) {
        observer.set(Objects.requireNonNull(eventObserver, "eventObserver"));
    }

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

    /**
     * Returns the last sequence already assigned by the attached presentation.
     * Protocol projections use this as a snapshot cut-over frontier: events at
     * or below it are already represented by a state snapshot taken
     * immediately afterwards and must not be replayed on top of that snapshot.
     */
    public long lastSequence() {
        TablePresentation current = presentation.get();
        return current == null ? 0L : current.lastSequence();
    }

    /**
     * Prevents a passive recovery model from presenting its provisional local
     * replay over an authoritative public stream. Network-authoritative events
     * still enter through {@link #publishAuthoritative(LongFunction)}.
     */
    public void suppressLocalPublications(boolean suppressed) {
        localPublicationsSuppressed.set(suppressed);
    }

    public CompletionStage<Void> publish(
            LongFunction<? extends TableVisualEvent> eventFactory) {
        return publishIfAttached(eventFactory).orElse(NO_RENDERER);
    }

    /** Publishes a server-authoritative event even during passive recovery. */
    public CompletionStage<Void> publishAuthoritative(
            LongFunction<? extends TableVisualEvent> eventFactory) {
        return publishAttached(eventFactory).orElse(NO_RENDERER);
    }

    /**
     * Publishes the local lifecycle terminator even when provisional recovery
     * presentation is suppressed.  Exiting a passive hot-join table must
     * still close its renderer; this does not reopen the suppressed replay.
     */
    public CompletionStage<Void> publishTerminal(
            LongFunction<? extends TableVisualEvent.CloseTable> eventFactory) {
        return publishAttached(eventFactory).orElse(NO_RENDERER);
    }

    /** Publishes atomically with the attachment lookup. */
    public Optional<CompletionStage<Void>> publishIfAttached(
            LongFunction<? extends TableVisualEvent> eventFactory) {
        if (localPublicationsSuppressed.get()) {
            return Optional.empty();
        }
        return publishAttached(eventFactory);
    }

    private Optional<CompletionStage<Void>> publishAttached(
            LongFunction<? extends TableVisualEvent> eventFactory) {
        Objects.requireNonNull(eventFactory, "eventFactory");
        TablePresentation current = presentation.get();
        return current == null
                ? Optional.empty()
                : Optional.of(current.publish(sequence -> {
                    TableVisualEvent event = Objects.requireNonNull(
                            eventFactory.apply(sequence), "event");
                    observer.get().accept(event);
                    return event;
                }));
    }

    @Override
    public void close() {
        TablePresentation current = presentation.getAndSet(null);
        if (current != null) {
            current.close();
        }
    }
}
