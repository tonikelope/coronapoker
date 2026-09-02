/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.Objects;

/** Current turn and pause timing without UI clocks or widgets. */
public final class TurnState {
    public record Snapshot(String nickname, long startedAtMillis, int durationSeconds,
            boolean paused, int pausedSeconds) { }
    private volatile String nickname = "";
    private volatile long startedAtMillis;
    private volatile int durationSeconds;
    private volatile boolean paused;
    private volatile int pausedSeconds;
    public String nickname() { return nickname; }
    public long startedAtMillis() { return startedAtMillis; }
    public int durationSeconds() { return durationSeconds; }
    public boolean paused() { return paused; }
    public int pausedSeconds() { return pausedSeconds; }
    public void begin(String nextNickname, long start, int duration) {
        nickname = Objects.requireNonNull(nextNickname, "nickname");
        startedAtMillis = start;
        durationSeconds = duration;
    }
    public void clear() { nickname = ""; startedAtMillis = 0L; durationSeconds = 0; }
    public void setPaused(boolean value) { paused = value; }
    public void setPausedSeconds(int value) { pausedSeconds = value; }
    public Snapshot snapshot() {
        return new Snapshot(nickname, startedAtMillis, durationSeconds, paused, pausedSeconds);
    }
}
