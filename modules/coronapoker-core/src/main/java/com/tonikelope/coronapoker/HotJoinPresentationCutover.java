/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-peer hand-off from a complete hot-join snapshot to the ordinary visual
 * event stream.
 *
 * <p>Events assigned before the snapshot frontier are already represented by
 * that snapshot. Replaying them afterwards can rewind visual state (for
 * example, a stale {@code DealCommunityCard} can cover an already revealed
 * flop). Events newer than the frontier remain the normal animation stream.</p>
 */
final class HotJoinPresentationCutover<T> {

    private final ArrayList<Entry<T>> pending = new ArrayList<>();
    private long snapshotFrontier = -1L;
    private boolean live;

    void beginSnapshot(long frontier) {
        if (frontier < 0L) {
            throw new IllegalArgumentException(
                    "snapshot frontier cannot be negative");
        }
        if (snapshotFrontier >= 0L || live) {
            throw new IllegalStateException(
                    "hot-join snapshot cut-over already started");
        }
        snapshotFrontier = frontier;
    }

    /**
     * Buffers while the bootstrap is in flight, then returns only live events
     * that belong after the snapshot.
     */
    Optional<T> accept(long sequence, T payload) {
        requireSequence(sequence);
        T checked = Objects.requireNonNull(payload, "payload");
        if (!live) {
            pending.add(new Entry<>(sequence, checked));
            return Optional.empty();
        }
        return sequence > snapshotFrontier
                ? Optional.of(checked) : Optional.empty();
    }

    /** Completes the bootstrap and returns the ordered post-snapshot tail. */
    List<T> completeSnapshot() {
        if (snapshotFrontier < 0L) {
            throw new IllegalStateException(
                    "hot-join snapshot cut-over has not started");
        }
        if (live) {
            throw new IllegalStateException(
                    "hot-join snapshot cut-over already completed");
        }
        live = true;
        List<T> tail = pending.stream()
                .filter(entry -> entry.sequence() > snapshotFrontier)
                .map(Entry::payload)
                .toList();
        pending.clear();
        return tail;
    }

    private static void requireSequence(long sequence) {
        if (sequence < 1L) {
            throw new IllegalArgumentException(
                    "presentation sequence must be positive");
        }
    }

    private record Entry<T>(long sequence, T payload) { }
}
