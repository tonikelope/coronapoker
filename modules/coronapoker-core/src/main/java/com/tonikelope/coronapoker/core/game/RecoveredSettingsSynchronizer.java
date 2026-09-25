/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/** Applies settings restored from a recovery fossil to the active frontend. */
@FunctionalInterface
public interface RecoveredSettingsSynchronizer {
    void apply();

    static RecoveredSettingsSynchronizer noop() {
        return () -> { };
    }
}
