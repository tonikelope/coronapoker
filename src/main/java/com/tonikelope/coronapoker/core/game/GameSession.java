/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Renderer-neutral ownership of one running poker game. */
public final class GameSession implements AutoCloseable {

    public enum Phase { CREATED, RUNNING, FINISHED, CLOSED }

    private final String localNickname;
    private final boolean host;
    private final TableState table;
    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.CREATED);

    public GameSession(String localNickname, boolean host) {
        String normalized = Objects.requireNonNull(localNickname, "localNickname").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("localNickname is required");
        }
        this.localNickname = normalized;
        this.host = host;
        this.table = new TableState(normalized);
    }

    public String localNickname() { return localNickname; }
    public boolean isHost() { return host; }
    public TableState table() { return table; }
    public Phase phase() { return phase.get(); }

    public void start() {
        if (!phase.compareAndSet(Phase.CREATED, Phase.RUNNING)) {
            throw new IllegalStateException("Game session cannot start from " + phase.get());
        }
    }

    public void finish() {
        Phase current;
        do {
            current = phase.get();
            if (current == Phase.FINISHED || current == Phase.CLOSED) return;
        } while (!phase.compareAndSet(current, Phase.FINISHED));
        table.setFinished(true);
    }

    public void setPaused(boolean paused) {
        if (phase.get() == Phase.CLOSED) {
            throw new IllegalStateException("Game session is closed");
        }
        table.setPaused(paused);
    }

    @Override
    public void close() {
        phase.set(Phase.CLOSED);
        table.setFinished(true);
    }
}
