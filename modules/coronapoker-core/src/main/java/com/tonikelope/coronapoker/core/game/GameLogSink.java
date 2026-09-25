/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.List;
import java.util.Objects;

/** Renderer-neutral destination for the canonical in-game log. */
@FunctionalInterface
public interface GameLogSink {
    void print(String message);

    default void updateShowdownCards(List<ShowdownEntry> entries) {
    }

    record ShowdownEntry(String nickname, boolean revealed, String holeCards, String hand) {

        public ShowdownEntry {
            Objects.requireNonNull(nickname, "nickname");
            holeCards = holeCards == null ? "" : holeCards;
            hand = hand == null ? "" : hand;
            if (revealed && (holeCards.isBlank() || hand.isBlank())) {
                throw new IllegalArgumentException("Revealed showdown entries need cards and hand");
            }
        }
    }

    static GameLogSink noop() {
        return ignored -> { };
    }
}
