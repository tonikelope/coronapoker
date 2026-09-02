/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.Locale;
import java.util.Objects;

/** Canonical CoronaPoker card identity in the protocol's 0..51 order. */
public record CardCode(Rank rank, Suit suit) {

    public enum Rank {
        ACE("A", 1), TWO("2", 2), THREE("3", 3), FOUR("4", 4),
        FIVE("5", 5), SIX("6", 6), SEVEN("7", 7), EIGHT("8", 8),
        NINE("9", 9), TEN("10", 10), JACK("J", 11), QUEEN("Q", 12),
        KING("K", 13);

        private final String wire;
        private final int aceLowValue;

        Rank(String wire, int aceLowValue) {
            this.wire = wire;
            this.aceLowValue = aceLowValue;
        }

        public String wire() { return wire; }
        public int aceLowValue() { return aceLowValue; }
        public int aceHighValue() { return this == ACE ? 14 : aceLowValue; }

        static Rank parse(String value) {
            String normalized = Objects.requireNonNull(value, "rank")
                    .trim().toUpperCase(Locale.ROOT);
            for (Rank candidate : values()) {
                if (candidate.wire.equals(normalized)) return candidate;
            }
            throw new IllegalArgumentException("Unknown card rank: " + value);
        }
    }

    public enum Suit {
        SPADES("P"), HEARTS("C"), CLUBS("T"), DIAMONDS("D");

        private final String wire;

        Suit(String wire) { this.wire = wire; }
        public String wire() { return wire; }

        static Suit parse(String value) {
            String normalized = Objects.requireNonNull(value, "suit")
                    .trim().toUpperCase(Locale.ROOT);
            for (Suit candidate : values()) {
                if (candidate.wire.equals(normalized)) return candidate;
            }
            throw new IllegalArgumentException("Unknown card suit: " + value);
        }
    }

    public CardCode {
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(suit, "suit");
    }

    public static CardCode of(String rank, String suit) {
        return new CardCode(Rank.parse(rank), Suit.parse(suit));
    }

    public static CardCode fromIndex(int index) {
        if (index < 0 || index >= 52) {
            throw new IllegalArgumentException("Card index must be between 0 and 51");
        }
        return new CardCode(Rank.values()[index % 13], Suit.values()[index / 13]);
    }

    public static CardCode fromOneBased(int value) {
        if (value < 1 || value > 52) {
            throw new IllegalArgumentException("Card value must be between 1 and 52");
        }
        return fromIndex(value - 1);
    }

    public static CardCode parseShortCode(String shortCode) {
        String normalized = Objects.requireNonNull(shortCode, "shortCode").trim();
        int separator = normalized.lastIndexOf('_');
        if (separator <= 0 || separator == normalized.length() - 1) {
            throw new IllegalArgumentException("Card code must use VALUE_SUIT form");
        }
        return of(normalized.substring(0, separator), normalized.substring(separator + 1));
    }

    public int index() { return suit.ordinal() * 13 + rank.ordinal(); }
    public int oneBased() { return index() + 1; }
    public String shortCode() { return rank.wire() + "_" + suit.wire(); }
}
