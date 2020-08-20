/*
 * Copyright (C) 2016 Jellen Vermeir
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.vermeir.poker.handranking;

import org.vermeir.poker.handranking.util.HandRanker;

/**
 * This class represents a Card.
 *
 * @author Jellen Vermeir
 */
public class Card implements Comparable<Card> {

    public static final int SUIT_CLUBS = 0;
    public static final int SUIT_DIAMONDS = 1;
    public static final int SUIT_HEARTS = 2;
    public static final int SUIT_SPADES = 3;

    public static final int[] ALL_SUITS = {SUIT_CLUBS,
        SUIT_DIAMONDS,
        SUIT_HEARTS,
        SUIT_SPADES};

    public static final int RANK_2 = 2;
    public static final int RANK_3 = 3;
    public static final int RANK_4 = 4;
    public static final int RANK_5 = 5;
    public static final int RANK_6 = 6;
    public static final int RANK_7 = 7;
    public static final int RANK_8 = 8;
    public static final int RANK_9 = 9;
    public static final int RANK_10 = 10;
    public static final int RANK_JACK = 11;
    public static final int RANK_QUEEN = 12;
    public static final int RANK_KING = 13;
    public static final int RANK_ACE = 14;

    public static final int[] ALL_RANKS = {
        RANK_2, RANK_3, RANK_4, RANK_5,
        RANK_6, RANK_7, RANK_8, RANK_9,
        RANK_10, RANK_JACK, RANK_QUEEN,
        RANK_KING, RANK_ACE};

    // Card suit
    private final int suit;
    // Card rank
    private final int rank;

    /**
     * Card constructur
     *
     * @param suit The suit of the card
     * @param rank The rank of the card
     */
    public Card(int suit, int rank) {
        this.suit = suit;
        this.rank = rank;
    }

    /**
     * Return the suit of the card
     *
     * @return The suit of the card
     */
    public int getSuit() {
        return this.suit;
    }

    /**
     * Return the rank of the card
     *
     * @return The rank of the card
     */
    public int getRank() {
        return this.rank;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || this.getClass() != obj.getClass()) {
            return false;
        }

        Card otherCard = (Card) obj;

        return otherCard.getSuit() == this.suit
                && otherCard.getRank() == this.rank;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 67 * hash + this.suit;
        hash = 67 * hash + this.rank;
        return hash;
    }

    /**
     * Define natural ordering for Card objects.
     *
     * @param o The object to compare the current object with.
     * @return 1 if greater, -1 if smaller, 0 if equal to the input Card object.
     */
    @Override
    public int compareTo(Card o) {
        int thisValue = HandRanker.getCardValue(this);
        int otherValue = HandRanker.getCardValue(o);

        if (thisValue > otherValue) {
            return 1;
        }
        if (thisValue < otherValue) {
            return -1;
        }

        return 0; // Same suit and rank
    }

    @Override
    public String toString() {
        return "[CARD suit " + this.suit + " Rank " + this.rank + "]";
    }
}
