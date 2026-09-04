/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.List;

/** Creates an evaluated poker hand without binding the dealer to a frontend. */
@FunctionalInterface
public interface GameHandFactory {

    GameHandResult evaluate(List<? extends GameCardController> cards);

    static GameHandFactory unavailable() {
        return cards -> {
            throw new IllegalStateException("No game-hand evaluator is bound");
        };
    }
}
