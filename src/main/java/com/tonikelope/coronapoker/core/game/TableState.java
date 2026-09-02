/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Shared table aggregate. It stores state only and contains no poker rules. */
public final class TableState {
    public record Snapshot(long revision, String localNickname,
            List<PlayerState.Snapshot> players, HandState.Snapshot hand,
            boolean paused, boolean finished) { }
    private final String localNickname;
    private final Map<String, PlayerState> players = new LinkedHashMap<>();
    private final HandState hand = new HandState();
    private final AtomicLong revision = new AtomicLong();
    private volatile boolean paused;
    private volatile boolean finished;
    public TableState(String localNickname) {
        this.localNickname = required(localNickname);
    }
    public String localNickname() { return localNickname; }
    public HandState hand() { return hand; }
    public synchronized void putPlayer(PlayerState player) {
        PlayerState checked = Objects.requireNonNull(player, "player");
        players.put(checked.nickname(), checked);
        revision.incrementAndGet();
    }
    public synchronized PlayerState player(String nickname) { return players.get(nickname); }
    public synchronized PlayerState removePlayer(String nickname) {
        PlayerState removed = players.remove(nickname);
        if (removed != null) revision.incrementAndGet();
        return removed;
    }
    public synchronized List<PlayerState> players() { return List.copyOf(players.values()); }
    public long touch() { return revision.incrementAndGet(); }
    public boolean paused() { return paused; }
    public boolean finished() { return finished; }
    public void setPaused(boolean value) { paused = value; touch(); }
    public void setFinished(boolean value) { finished = value; touch(); }
    public synchronized Snapshot snapshot() {
        return new Snapshot(revision.get(), localNickname,
                players.values().stream().map(PlayerState::snapshot).toList(),
                hand.snapshot(), paused, finished);
    }
    private static String required(String value) {
        String normalized = Objects.requireNonNull(value, "localNickname").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("localNickname is required");
        return normalized;
    }
}
