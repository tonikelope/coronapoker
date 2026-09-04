/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.List;

/** Card text and collection operations shared by every presentation frontend. */
public final class GameCards {

    private GameCards() {
    }

    public static String display(GameCardController card) {
        if (card == null) {
            return null;
        }
        return "[" + card.getValor() + suitSymbol(card.getPalo()) + "]";
    }

    /** Legacy-compatible display join: spaces between cards, null for no cards. */
    public static String displayCollection(
            List<? extends GameCardController> cards) {
        if (cards == null || cards.isEmpty()) {
            return null;
        }
        return cards.stream().map(GameCards::display)
                .reduce((left, right) -> left + " " + right).orElse(null);
    }

    /** Legacy-compatible wire join: hash separators, null for no cards. */
    public static String shortCollection(
            List<? extends GameCardController> cards) {
        if (cards == null || cards.isEmpty()) {
            return null;
        }
        return cards.stream().map(GameCardController::toShortString)
                .reduce((left, right) -> left + "#" + right).orElse(null);
    }

    private static String suitSymbol(String suit) {
        return switch (suit) {
            case "P" -> "\u2660";
            case "C" -> "\u2665";
            case "T" -> "\u2663";
            case "D" -> "\u2666";
            default -> "null";
        };
    }
}
