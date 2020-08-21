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
package org.vermeir.poker.handranking.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.vermeir.poker.handranking.Card;
import org.vermeir.poker.handranking.Hand;
import org.vermeir.poker.handranking.RankableHand;
import org.vermeir.poker.handranking.StartingHand;
import org.vermeir.poker.util.Combinations;

/**
 * Flyweight Hand factory. This class avoids instantiation overhead for Hands by
 * mapping input card combinations to already existing Hand instances (if
 * applicable).
 *
 * @author Jellen Vermeir
 */
public class HandFactory {

    private static final HashMap<Long, Hand> EXISTING_HANDS = new HashMap<>();
    public static int startingHandCounter = 0; // just for demo..
    public static int rankableHandCounter = 0; // Just for demo..

    /**
     * This function calculates a unique key for an input List of cards and
     * returns the corresponding Hand instance for this particular Card
     * combination.
     *
     * The key itself is based on the cardvalues of a sorted version of the
     * Cards that are present inside the list. The function checks if a Hand
     * instance was already created for this particular key-value. If the key
     * was not found, the hand must be created and saved in the buffer before it
     * can be returned. Otherwise, it is fetched and returned from the Hand
     * instance buffer.
     *
     * @param cards The combination of cards.
     * @return A Rankable Hand instance that corresponds to the Card
     * combination.
     * @throws HandRankingException
     */
    public static Hand getHand(List<Card> cards) throws HandRankingException {

        // Sort the cards by value (different ordering with same cards == same hand)
        Collections.sort(cards);

        // create unique hash for this sorted Card combination
        long hash = 5;
        for (Card c : cards) {
            hash = 67 * hash + HandRanker.getCardValue(c);
        }

        // Fetch hand from buffer, if present
        Hand hand = EXISTING_HANDS.get(hash);

        if (hand == null) { // Hand not found, create it and put in buffer
            if (cards.size() > 2) {
                hand = new RankableHand(new HashSet(cards));
                rankableHandCounter++;
            } else {
                hand = new StartingHand(cards.get(0), cards.get(1));
                startingHandCounter++;
            }
            EXISTING_HANDS.put(hash, hand);
        }
        return (hand);
    }

    /**
     * Clear the buffer
     */
    public static void clearMapping() {
        EXISTING_HANDS.clear();
    }

    /**
     * Map all possible handCombinations
     */
    public static void mapAllHandValues() {
        List<Card> allCards = new ArrayList<>();
        for (int i = 0; i < Card.ALL_SUITS.length; i++) {
            for (int j = 0; j < Card.ALL_RANKS.length; j++) {
                allCards.add(new Card(Card.ALL_SUITS[i], Card.ALL_RANKS[j]));
            }
        }

        try {
            // Map all starting hands
            mapHands(allCards, 2);
            // Map all rankable hands
            mapHands(allCards, 5);
            mapHands(allCards, 6);
            // mapHands(allCards, 7);
        } catch (HandRankingException ex) {
            System.out.println(ex);
        }
    }

    /**
     * Create Hand instances for al k-Card combinations in the input list.
     *
     * @param allCards The cards
     * @param k The number of cards in the Card combinations.
     * @throws HandRankingException
     */
    private static void mapHands(List<Card> allCards, int k) throws HandRankingException {
        List<List<Integer>> combinations = Combinations.combine(allCards.size(), k);
        List<Card> cardCombination = new ArrayList<>(k);
        for (List<Integer> combi : combinations) {
            cardCombination.clear();
            for (Integer cardIndex : combi) {
                cardCombination.add(allCards.get(cardIndex - 1));
            }
            HandFactory.getHand(cardCombination);
        }
    }
}
