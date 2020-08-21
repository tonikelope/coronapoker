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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.vermeir.poker.handranking.util.HandRanker;
import org.vermeir.poker.handranking.util.HandRankingException;
import org.vermeir.poker.util.Combinations;

/**
 * This class represents a rankable (postflop) hand.
 *
 * @author Jellen Vermeir
 */
public class RankableHand extends Hand {

    /**
     * Construct a hand from a set of cards
     *
     * @param c A set of cards
     * @throws poker.handranking.util.HandRankingException
     */
    public RankableHand(Set<Card> c) throws HandRankingException {

        if (c.size() < 5 | c.size() > 7) {
            for (Card carta : c) {
                System.out.println(carta);
            }
            String ex = "Between 5 and 7 unique cards required to construct instance. " + "Detected " + c.size() + " cards";
            throw new HandRankingException(ex);

        }

        this.cards = c;
        calculateHandValue();
    }

    /**
     * Calculate the overal ranking of this particular hand If the hand contains
     * more than 5 cards then the ranking for the strongest combination of 5
     * cards is obtained.
     */
    public final void calculateHandValue() {
        // Convert our set of cards in an arraylist
        List<Card> crds = new ArrayList<>(cards);
        // Enumerate all 5 card combinations
        List<List<Integer>> combinations
                = Combinations.combine(crds.size(), 5);

        int maxRank = HandRanker.WORST_RANKING;
        Card[] toRank = new Card[5];
        for (List<Integer> comb : combinations) {
            toRank[0] = crds.get(comb.get(0) - 1);
            toRank[1] = crds.get(comb.get(1) - 1);
            toRank[2] = crds.get(comb.get(2) - 1);
            toRank[3] = crds.get(comb.get(3) - 1);
            toRank[4] = crds.get(comb.get(4) - 1);

            try {
                int newRank = HandRanker.getHandRanking(toRank);
                if (newRank > maxRank) {
                    maxRank = newRank;
                }
            } catch (HandRankingException ex) {
                System.out.println(ex);
                return;
            }
        }
        this.handValue = maxRank;
    }
}
