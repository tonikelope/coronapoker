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

import java.util.HashSet;
import org.vermeir.poker.handranking.util.HandRanker;
import org.vermeir.poker.handranking.util.HandRankingException;

/**
 * This class represents a starting hand with 2 cards
 *
 * @author Jellen Vermeir
 */
public class StartingHand extends Hand {

    private Card c1;
    private Card c2;

    /**
     * Construct a starting hand from two hole Cards given as input.
     *
     * @param c1 The first holecard
     * @param c2 The second holecard
     */
    public StartingHand(Card c1, Card c2) {
        this.cards = new HashSet<>();
        cards.add(c1);
        cards.add(c2);

        this.c1 = c1;
        this.c2 = c2;
    }

    /**
     * Return the first hole card
     *
     * @return The first holecard
     */
    public Card getFirstCard() {
        return this.c1;
    }

    /**
     * Returns the second holecard.
     *
     * @return The second holecard.
     */
    public Card getSecondCard() {
        return this.c2;
    }

    /**
     * Construct a starting hand from the suits/ranks of the cards
     *
     * @param suit1 suit of the first card
     * @param rank1 rank of the first card
     * @param suit2 suit of the first card
     * @param rank2 rank of the first card
     */
    public StartingHand(int suit1, int rank1, int suit2, int rank2) {
        this(new Card(suit1, rank1), new Card(suit2, rank2));
    }

    /**
     * Return the starting hand value (income rate) for this particular hand,
     * given the number of active opponents in the pot.
     *
     * @param nrOpponents
     * @return The starting hand Value.
     * @throws poker.handranking.util.HandRankingException
     */
    public int updateHandValue(int nrOpponents) throws HandRankingException {
        this.handValue = HandRanker.startingHandRank(this.c1, this.c2, nrOpponents);
        return (this.handValue);
    }
}
