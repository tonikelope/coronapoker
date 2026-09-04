/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/** Creates a frontend-independent pot repository for one hand. */
@FunctionalInterface
public interface GamePotFactory {

    GamePot create(double difference);

    static GamePotFactory unavailable() {
        return difference -> {
            throw new IllegalStateException("No game-pot factory is bound");
        };
    }
}
