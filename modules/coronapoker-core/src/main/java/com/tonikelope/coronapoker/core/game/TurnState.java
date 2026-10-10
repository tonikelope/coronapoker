/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.Objects;

/** Current turn and pause timing without UI clocks or widgets. */
public final class TurnState {
    public record Snapshot(String nickname, long startedAtMillis, int durationSeconds,
            boolean paused, int pausedSeconds) { }
    private String nickname = "";
    private long startedAtMillis;
    private int durationSeconds;
    private boolean paused;
    private long pausedAtMillis;
    private long accumulatedPausedMillis;

    public synchronized String nickname() { return nickname; }
    public synchronized long startedAtMillis() { return startedAtMillis; }
    public synchronized int durationSeconds() { return durationSeconds; }
    public synchronized boolean paused() { return paused; }
    public synchronized int pausedSeconds() {
        return Math.toIntExact(Math.min(Integer.MAX_VALUE,
                currentPausedMillis(System.currentTimeMillis()) / 1_000L));
    }

    public synchronized void begin(String nextNickname, long start, int duration) {
        nickname = Objects.requireNonNull(nextNickname, "nickname");
        startedAtMillis = start;
        durationSeconds = Math.max(0, duration);
        paused = false;
        pausedAtMillis = 0L;
        accumulatedPausedMillis = 0L;
    }

    public synchronized void clear() {
        nickname = "";
        startedAtMillis = 0L;
        durationSeconds = 0;
        paused = false;
        pausedAtMillis = 0L;
        accumulatedPausedMillis = 0L;
    }

    public void setPaused(boolean value) {
        setPaused(value, System.currentTimeMillis());
    }

    synchronized void setPaused(boolean value, long nowMillis) {
        if (paused == value) return;
        if (value) {
            pausedAtMillis = Math.max(startedAtMillis, nowMillis);
        } else if (pausedAtMillis > 0L) {
            accumulatedPausedMillis += Math.max(0L, nowMillis - pausedAtMillis);
            pausedAtMillis = 0L;
        }
        paused = value;
    }

    public synchronized void setPausedSeconds(int value) {
        accumulatedPausedMillis = Math.max(0, value) * 1_000L;
        if (paused) pausedAtMillis = System.currentTimeMillis();
    }

    public synchronized long remainingMillis(long nowMillis) {
        if (nickname.isBlank() || durationSeconds <= 0) return 0L;
        long totalMillis = durationSeconds * 1_000L;
        long effectiveNow = paused && pausedAtMillis > 0L
                ? pausedAtMillis : nowMillis;
        long activeElapsed = Math.max(0L, effectiveNow - startedAtMillis
                - accumulatedPausedMillis);
        return Math.max(0L, totalMillis - activeElapsed);
    }

    public synchronized Snapshot snapshot() {
        long pausedMillis = currentPausedMillis(System.currentTimeMillis());
        int seconds = Math.toIntExact(Math.min(Integer.MAX_VALUE,
                pausedMillis / 1_000L));
        return new Snapshot(nickname, startedAtMillis, durationSeconds, paused,
                seconds);
    }

    private long currentPausedMillis(long nowMillis) {
        return accumulatedPausedMillis + (paused && pausedAtMillis > 0L
                ? Math.max(0L, nowMillis - pausedAtMillis) : 0L);
    }
}
