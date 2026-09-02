/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/** Renderer-neutral destination for the canonical in-game log. */
@FunctionalInterface
public interface GameLogSink {
    void print(String message);

    static GameLogSink noop() {
        return ignored -> { };
    }
}
