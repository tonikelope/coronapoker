/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/**
 * Blocking game-thread boundary used while a table is paused.
 * Implementations must never block the frontend render thread.
 */
@FunctionalInterface
public interface PauseGate {
    /** Waits according to the active session policy and reports whether it waited. */
    boolean await();

    static PauseGate open() {
        return () -> false;
    }
}
