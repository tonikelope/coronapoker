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
import static java.util.Arrays.asList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.vermeir.poker.handranking.Card;

/**
 * Handranking utiliy tools. Includes preflop / postflop hand evaluation
 * functions plus probability weight mapping.
 *
 * @author Jellen Vermeir
 */
public class HandRanker {

    // These variables are used for postflop hand ranking
    private static final int STRAIGHT_FLUSH = 8000000; // base straight flush value
    private static final int QUADS = 7000000; // base quad value
    private static final int FULL_HOUSE = 6000000; // base full house value
    private static final int FLUSH = 5000000; // base flush value
    private static final int STRAIGHT = 4000000; // base straight value
    private static final int SET = 3000000; // base set value   
    private static final int TWO_PAIRS = 2000000; // base two pair value    
    private static final int ONE_PAIR = 1000000; // base one pair ranking    
    public static final int WORST_RANKING = 0;       // base value

    // Used for preflop card rank lookup into probability array (mapping of unique 169 hands)
    private static final Map<Integer, Integer> cardRankToStartIndex = new HashMap<Integer, Integer>() {
        {
            put(Card.RANK_2, 0);
            put(Card.RANK_3, 1);
            put(Card.RANK_4, 2);
            put(Card.RANK_5, 3);
            put(Card.RANK_6, 4);
            put(Card.RANK_7, 5);
            put(Card.RANK_8, 6);
            put(Card.RANK_9, 7);
            put(Card.RANK_10, 8);
            put(Card.RANK_JACK, 9);
            put(Card.RANK_QUEEN, 10);
            put(Card.RANK_KING, 11);
            put(Card.RANK_ACE, 12);
        }
    };

    // Used for preflop card suit lookup into probability array (mapping of unique 169 hands)
    private static final Map<Integer, Integer> cardSuitToStartIndex = new HashMap<Integer, Integer>() {
        {
            put(Card.SUIT_CLUBS, 0);
            put(Card.SUIT_DIAMONDS, 1);
            put(Card.SUIT_HEARTS, 2);
            put(Card.SUIT_SPADES, 3);
        }
    };

    // Preflop income rates for 4 or more active opponents. Obtained via simulation (view Loki paper for info)
    private static final List<Integer> IR7_2 = asList(-6, -462, -422, -397, -459, -495, -469, -433, -383, -336, -274, -188, -39);
    private static final List<Integer> IR7_3 = asList(-180, 21, -347, -304, -365, -418, -447, -414, -356, -308, -248, -163, -1);
    private static final List<Integer> IR7_4 = asList(-148, -69, 67, -227, -273, -323, -362, -391, -334, -287, -223, -133, 32);
    private static final List<Integer> IR7_5 = asList(-121, -38, 31, 122, -198, -230, -270, -303, -309, -259, -200, -103, 64);
    private static final List<Integer> IR7_6 = asList(-174, -95, -10, 64, 206, -151, -175, -204, -217, -235, -164, -72, 23);
    private static final List<Integer> IR7_7 = asList(-208, -135, -47, 35, 108, 298, -87, -106, -112, -128, -124, -26, 72);
    private static final List<Integer> IR7_8 = asList(-184, -164, -83, 2, 93, 168, 420, -5, 6, -10, -10, 22, 126);
    private static final List<Integer> IR7_9 = asList(-146, -128, -111, -26, 64, 153, 245, 565, 134, 118, 118, 151, 189);
    private static final List<Integer> IR7_10 = asList(-88, -68, -46, -29, 59, 155, 268, 383, 765, 299, 305, 336, 373);
    private static final List<Integer> IR7_J = asList(-38, -15, 1, 30, 51, 147, 256, 377, 536, 996, 380, 420, 462);
    private static final List<Integer> IR7_Q = asList(35, 49, 72, 99, 127, 162, 268, 384, 553, 628, 1279, 529, 574);
    private static final List<Integer> IR7_K = asList(117, 141, 167, 190, 223, 261, 304, 423, 591, 669, 764, 1621, 712);
    private static final List<Integer> IR7_A = asList(269, 304, 333, 363, 313, 365, 416, 475, 644, 720, 815, 934, 2043);
    private static final List<List<Integer>> IR7 = asList(IR7_2, IR7_3, IR7_4, IR7_5, IR7_6,
            IR7_7, IR7_8, IR7_9, IR7_10, IR7_J,
            IR7_Q, IR7_K, IR7_A);

    // Preflop income rates for 2 or 3 active opponent. Obtained via simulation (view Loki paper for info)
    private static final List<Integer> IR4_2 = asList(-121, -440, -409, -382, -411, -432, -394, -357, -301, -259, -194, -116, 16);
    private static final List<Integer> IR4_3 = asList(-271, -42, -345, -312, -340, -358, -371, -328, -277, -231, -165, -87, 54);
    private static final List<Integer> IR4_4 = asList(-245, -183, 52, -246, -269, -287, -300, -308, -252, -204, -135, -55, 84);
    private static final List<Integer> IR4_5 = asList(-219, -151, -91, 152, -200, -211, -227, -236, -227, -169, -104, -24, 118);
    private static final List<Integer> IR4_6 = asList(-247, -177, -113, -52, 256, -145, -152, -158, -152, -145, -74, 9, 99);
    private static final List<Integer> IR4_7 = asList(-261, -201, -129, -65, 3, 376, -76, -79, -68, -66, -44, 48, 148);
    private static final List<Integer> IR4_8 = asList(-226, -204, -140, -73, -2, 66, 503, 0, 15, 24, 45, 84, 194);
    private static final List<Integer> IR4_9 = asList(-191, -166, -147, -79, -5, 68, 138, 647, 104, 113, 136, 177, 241);
    private static final List<Integer> IR4_10 = asList(-141, -116, -91, -69, -4, 75, 150, 235, 806, 226, 255, 295, 354);
    private static final List<Integer> IR4_J = asList(-89, -67, -41, -12, 7, 82, 163, 248, 349, 965, 301, 348, 410);
    private static final List<Integer> IR4_Q = asList(-29, -3, 22, 51, 80, 108, 185, 274, 379, 423, 1141, 403, 473);
    private static final List<Integer> IR4_K = asList(47, 76, 101, 128, 161, 199, 230, 318, 425, 473, 529, 1325, 541);
    private static final List<Integer> IR4_A = asList(175, 211, 237, 266, 249, 295, 338, 381, 491, 539, 594, 655, 1554);
    private static final List<List<Integer>> IR4 = asList(IR4_2, IR4_3, IR4_4, IR4_5, IR4_6,
            IR4_7, IR4_8, IR4_9, IR4_10, IR4_J,
            IR4_Q, IR4_K, IR4_A);

    // Preflop income rates for 1 active opponent. Obtained via simulation (view Loki paper for info)
    private static final List<Integer> IR2_2 = asList(7, -351, -334, -314, -318, -308, -264, -217, -166, -113, -53, 10, 98);
    private static final List<Integer> IR2_3 = asList(-279, 74, -296, -274, -277, -267, -251, -201, -148, -93, -35, 27, 116);
    private static final List<Integer> IR2_4 = asList(-263, -225, 142, -236, -240, -231, -209, -185, -130, -75, -17, 46, 134);
    private static final List<Integer> IR2_5 = asList(-244, -206, -169, 207, -201, -189, -169, -148, -114, -55, 2, 68, 153);
    private static final List<Integer> IR2_6 = asList(-247, -208, -171, -138, 264, -153, -134, -108, -78, -43, 19, 85, 154);
    private static final List<Integer> IR2_7 = asList(-236, -200, -162, -125, -91, 324, -99, -72, -43, -6, 37, 104, 176);
    private static final List<Integer> IR2_8 = asList(-192, -182, -143, -108, -75, -43, 384, -39, -4, 29, 72, 120, 197);
    private static final List<Integer> IR2_9 = asList(-152, -134, -122, -84, -50, -17, 16, 440, 28, 65, 106, 155, 215);
    private static final List<Integer> IR2_10 = asList(-104, -86, -69, -56, -19, 12, 47, 81, 499, 102, 146, 195, 254);
    private static final List<Integer> IR2_J = asList(-52, -35, -19, 0, 11, 46, 79, 113, 149, 549, 161, 212, 271);
    private static final List<Integer> IR2_Q = asList(2, 21, 34, 55, 72, 86, 121, 153, 188, 204, 598, 228, 289);
    private static final List<Integer> IR2_K = asList(63, 79, 98, 116, 132, 151, 168, 200, 235, 249, 268, 647, 305);
    private static final List<Integer> IR2_A = asList(146, 164, 180, 198, 198, 220, 240, 257, 291, 305, 323, 339, 704);
    private static final List<List<Integer>> IR2 = asList(IR2_2, IR2_3, IR2_4, IR2_5, IR2_6,
            IR2_7, IR2_8, IR2_9, IR2_10, IR2_J,
            IR2_Q, IR2_K, IR2_A);

    /**
     * This function maps a two card combination to an index inside a 13x13
     * input List. The two dimensional input List should represent a mapping for
     * 169 unique starting hands (for example, starting hand probabilties /
     * field array).
     *
     * @param <T> The type of entries in the map
     * @param c1 The first holecard
     * @param c2 The second holecard
     * @param toMap 13x13 input List for mapping the two card combination.
     * @return
     */
    public static <T> T Map_169(Card c1, Card c2, List<List<T>> toMap) {

        int rank1 = c1.getRank();
        int rank2 = c2.getRank();
        int idx1 = cardRankToStartIndex.get(rank1);
        int idx2 = cardRankToStartIndex.get(rank2);

        boolean r1Greatest = rank1 > rank2;
        if (c1.getSuit() == c2.getSuit()) {
            return r1Greatest ? toMap.get(idx1).get(idx2) : toMap.get(idx2).get(idx1);
        } else {
            return r1Greatest ? toMap.get(idx2).get(idx1) : toMap.get(idx1).get(idx2);
        }
    }

    /**
     * This function defines a ranking for indiviudal cards. The resulting value
     * is used to define a natural ordering for individual cards. For suits, the
     * ordering is defined as follows: clubs < diamonds < hearts < spades @
     *
     *
     * param c The card @return The card value
     */
    public static int getCardValue(Card c) {
        return cardSuitToStartIndex.get(c.getSuit()) * 14 + cardRankToStartIndex.get(c.getRank());
    }

    /**
     * Create a field array that corresponds to uniform probabilities, for the
     * 169 unique holecard combinations. All values are set to 1.
     *
     * @return The uniform weight array
     */
    public static List<List<Double>> getUniformWeightArray() {
        List<List<Double>> weightArray = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            List<Double> arr = new ArrayList<>();
            for (int j = 0; j < 13; j++) {
                arr.add(new Double(1));
            }
            weightArray.add(arr);
        }
        return weightArray;
    }

    /**
     * This function perform the hand ranking for preflop hands, given the
     * holecards and the number of active opponents as input. The returned value
     * corresponds to the "income rate" of the particular starting hand (View
     * Loki paper for more info).
     *
     * @param c1 The first holecard
     * @param c2 The second holecard
     * @param nrOpponents The number of active opponents
     * @return The preflop handrank (income rate)
     * @throws HandRankingException
     */
    public static int startingHandRank(Card c1, Card c2, int nrOpponents) throws HandRankingException {
        if (nrOpponents >= 4) // 5 or more players
        {
            return HandRanker.Map_169(c1, c2, IR7);
        } else if (nrOpponents >= 2) // 3-4 players
        {
            return HandRanker.Map_169(c1, c2, IR4);
        } else {
            return HandRanker.Map_169(c1, c2, IR2); // 2 players
        }
    }

    /**
     * Postflop Handvalue calculation for a 5-card combination. All possible
     * subcombinations (kickers etc..) are taken into account during the
     * mapping. Hence, every unique 5 card combination will be mapped to a
     * different value: The stronger the combination the higher the returned
     * value will be.
     *
     * @param cards The 5-card combination to be evaluated.
     * @return The handvalue for the 5-card input combination.
     * @throws HandRankingException
     */
    public static int getHandRanking(Card[] cards) throws HandRankingException {
        if (cards.length != 5) {
            String ex = "Exactly 5 cards required for handRank evaluation. "
                    + "Detected " + cards.length + " cards";
            throw new HandRankingException(ex);
        }

        sortBySuit(cards);
        if (isFlush(cards)) {
            sortByRank(cards);

            if (isStraight(cards)) {
                return (valueStraightFlush(cards));
            } else // can not be quads or full house
            {
                return (valueFlush(cards));
            }
        }

        sortByRank(cards);
        if (isQuads(cards)) {
            return valueQuads(cards);
        }
        if (isFullHouse(cards)) {
            return valueFullHouse(cards);
        }
        if (isStraight(cards)) {
            return valueStraight(cards);
        }
        if (isSet(cards)) {
            return valueSet(cards);
        }
        if (isTwoPair(cards)) {
            return valueTwoPair(cards);
        }
        if (isPair(cards)) {
            return valuePair(cards);
        }

        return valueCards(cards);
    }

    /**
     * Check for quads. Note: Assumes that cards are presorted by rank.
     *
     * @param cards The 5-card combination to evaluate.
     * @return True if quads, false otherwise
     */
    private static boolean isQuads(Card[] cards) {
        boolean p1, p2;

        // xxxx y
        p1 = cards[0].getRank() == cards[1].getRank()
                && cards[1].getRank() == cards[2].getRank()
                && cards[2].getRank() == cards[3].getRank();

        // y xxxx
        p2 = cards[1].getRank() == cards[2].getRank()
                && cards[2].getRank() == cards[3].getRank()
                && cards[3].getRank() == cards[4].getRank();

        return (p1 || p2);
    }

    /**
     * Check for full house. Note: Assumes that cards are presorted by rank.
     *
     * @param cards The 5-card combination to evaluate.
     * @return true if full house, false otherwise
     */
    private static boolean isFullHouse(Card[] cards) {
        sortByRank(cards);

        boolean p1, p2;

        // xxx yy
        p1 = cards[0].getRank() == cards[1].getRank()
                && cards[1].getRank() == cards[2].getRank()
                && cards[3].getRank() == cards[4].getRank();

        // xx yyy
        p2 = cards[0].getRank() == cards[1].getRank()
                && cards[2].getRank() == cards[3].getRank()
                && cards[3].getRank() == cards[4].getRank();

        return (p1 || p2);
    }

    /**
     * Check for flush (can also be straight flush) Note: Assumes that cards are
     * presorted by suit
     *
     * @param cards The 5-card combination to evaluate.
     * @return true if flush, false otherwise
     */
    private static boolean isFlush(Card[] cards) {
        sortBySuit(cards);

        return (cards[0].getSuit() == cards[4].getSuit());
    }

    /**
     * Check for straight (can also be straight flush) Note: Assumes that cards
     * are presorted by rank.
     *
     * @param cards The 5-card combination to evaluate.
     * @return true if straight, false otherwise
     */
    private static boolean isStraight(Card[] cards) {
        sortByRank(cards);

        // check for wheel
        if (cards[4].getRank() == Card.RANK_ACE
                && cards[0].getRank() == Card.RANK_2
                && cards[1].getRank() == Card.RANK_3
                && cards[2].getRank() == Card.RANK_4
                && cards[3].getRank() == Card.RANK_5) {
            return true;
        }

        int nextRank = cards[0].getRank();
        for (int i = 1; i < cards.length; i++) {
            nextRank++;
            if (cards[i].getRank() != nextRank) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check for set (can also be full house or four of a kind) Note: Assumes
     * that cards are presorted by rank.
     *
     * @param cards The 5-card combination to evaluate.
     * @return true if set, false otherwise
     */
    private static boolean isSet(Card[] cards) {
        sortByRank(cards);

        boolean p1, p2, p3;

        // xxx yz
        p1 = cards[0].getRank() == cards[1].getRank()
                && cards[1].getRank() == cards[2].getRank();

        // y xxx z
        p2 = cards[1].getRank() == cards[2].getRank()
                && cards[2].getRank() == cards[3].getRank();

        // yz xxx
        p3 = cards[2].getRank() == cards[3].getRank()
                && cards[3].getRank() == cards[4].getRank();

        return (p1 || p2 || p3);
    }

    /**
     * Check for two pair (can also be full house) Note: Assumes that cards are
     * sorted by rank.
     *
     * @param cards The 5-card combination to evaluate.
     * @return true if two pair, false otherwise
     */
    private static boolean isTwoPair(Card[] cards) {
        boolean p1, p2, p3;

        // xx yy z
        p1 = cards[0].getRank() == cards[1].getRank()
                && cards[2].getRank() == cards[3].getRank();

        // xx z yy
        p2 = cards[0].getRank() == cards[1].getRank()
                && cards[3].getRank() == cards[4].getRank();

        // z xx yy
        p3 = cards[1].getRank() == cards[2].getRank()
                && cards[3].getRank() == cards[4].getRank();

        return (p1 || p2 || p3);
    }

    /**
     * Check for pair (can also be two pair, set, full house or quads) Note:
     * Assumes that cards are sorted by rank.
     *
     * @param cards The 5-card combination to evaluate.
     * @return true if pair, false otherwise
     */
    private static boolean isPair(Card[] cards) {
        boolean p1, p2, p3, p4;
        p1 = cards[0].getRank() == cards[1].getRank();
        p2 = cards[1].getRank() == cards[2].getRank();
        p3 = cards[2].getRank() == cards[3].getRank();
        p4 = cards[3].getRank() == cards[4].getRank();

        return (p1 || p2 || p3 || p4);
    }

    /**
     * Return straight flush hand value. Assume sorted by rank. STRAIGHT_FLUSH +
     * kicker
     *
     * @param cards the card combination under consideration
     * @return hand value
     */
    private static int valueStraightFlush(Card[] cards) {
        return STRAIGHT_FLUSH + cards[4].getRank();
    }

    /**
     * Return full house hand value. Assume sorted by rank. FULL_HOUSE +
     * SETCard*10000 + PAIRCard
     *
     * @param cards The 5-card combination to evaluate.
     * @return hand value
     */
    private static int valueFullHouse(Card[] cards) {
        int setValue = cards[2].getRank() * 10000;

        int firstRank = cards[0].getRank();
        if (firstRank != cards[2].getRank()) {
            return FULL_HOUSE + setValue + firstRank;
        } else {
            return FULL_HOUSE + setValue + cards[4].getRank();
        }
    }

    /**
     * Return flush hand value. Assume sorted by rank. FLUSH + valueCards
     *
     * @param cards The 5-card combination to evaluate.
     * @return hand value
     */
    private static int valueFlush(Card[] cards) {
        return FLUSH + valueCards(cards);
    }

    /**
     * Return straight hand value. Assume sorted by rank. STRAIGHT + high card
     *
     * @param cards The 5-card combination to evaluate.
     * @return hand value
     */
    private static int valueStraight(Card[] cards) {
        return STRAIGHT + cards[4].getRank();
    }

    /**
     * Return quads hand value. Assume sorted by rank. STRAIGHT + QUADCard*10000
     * + kicker
     *
     * @param cards The 5-card combination to evaluate.
     * @return hand value
     */
    private static int valueQuads(Card[] cards) {
        int quadRank = cards[2].getRank();
        int quadValue = quadRank * 10000;

        int firstRank = cards[0].getRank();
        if (firstRank != quadRank) {
            return QUADS + quadValue + firstRank;
        } else {
            return QUADS + quadValue + cards[4].getRank();
        }
    }

    /**
     * Return set hand value. Assume sorted by rank. SET + SetCard * 10000 +
     * kicker1*100 + kicker2
     *
     * @param cards The 5-card combination to evaluate.
     * @return hand value
     */
    private static int valueSet(Card[] cards) {

        int setRank = cards[2].getRank();
        int setValue = setRank * 10000;

        int firstRank = cards[0].getRank();
        if (firstRank == setRank) {
            return SET + setValue + cards[4].getRank() * 100 + cards[3].getRank();
        }

        int secondRank = cards[1].getRank();
        if (secondRank == setRank) {
            return SET + setValue + cards[4].getRank() * 100 + firstRank;
        }

        return SET + setValue + secondRank * 100 + firstRank;
    }

    /**
     * Return two pair hand value. Assume sorted by rank. TWO_PAIRS + HighPair *
     * 10000 + LowPair*100 + kicker
     *
     * @param cards The 5-card combination to evaluate.
     * @return hand value
     */
    private static int valueTwoPair(Card[] cards) {
        int highPairRank = cards[3].getRank(); // always highest pair rank
        int highPairValue = highPairRank * 10000;

        int firstRank = cards[0].getRank();
        int secondRank = cards[1].getRank();
        if (firstRank != secondRank) {
            return TWO_PAIRS + highPairValue + secondRank * 100 + firstRank;
        }

        int lowPairValue = firstRank * 100;
        int thirdRank = cards[2].getRank();
        if (thirdRank == highPairRank) {
            return TWO_PAIRS + highPairValue + lowPairValue + cards[4].getRank();
        }

        return (TWO_PAIRS + highPairValue + lowPairValue + thirdRank);
    }

    /**
     * Return one pair hand value. Assume sorted by rank. ONE_PAIR + Pair*10000
     * + kicker1*500 + kicker2*15 + kicker3
     *
     * @param cards The 5-card combination to evaluate.
     * @return hand value
     */
    private static int valuePair(Card[] cards) {
        int firstRank = cards[0].getRank();
        int secondRank = cards[1].getRank();
        int thirdRank = cards[2].getRank();
        int fourthRank = cards[3].getRank();
        int fifthRank = cards[4].getRank();

        if (fourthRank == fifthRank) {
            return ONE_PAIR + fourthRank * 10000 + thirdRank * 500 + secondRank * 15 + firstRank;
        }
        if (thirdRank == fourthRank) {
            return ONE_PAIR + thirdRank * 10000 + fifthRank * 500 + secondRank * 15 + firstRank;
        }
        if (secondRank == thirdRank) {
            return ONE_PAIR + secondRank * 10000 + fifthRank * 500 + fourthRank * 15 + firstRank;
        }

        return ONE_PAIR + firstRank * 10000 + fifthRank * 500 + fourthRank * 15 + thirdRank;
    }

    /**
     * Return basic card value. Assume sorted by rank. 15^4 highcard1 + 15^3
     * highcard2 + ... + lowCard
     *
     * @param cards The 5-card combination to evaluate.
     * @return hand value
     */
    private static int valueCards(Card[] cards) {
        return cards[4].getRank() * 50625 + cards[3].getRank() * 3375
                + cards[2].getRank() * 225 + cards[1].getRank() * 15 + cards[0].getRank();
    }

    /**
     * Sort cards by rank
     *
     * @param cards The 5-card combination to evaluate.
     */
    private static void sortByRank(Card[] cards) {
        int i, j, newMin;
        for (i = 0; i < cards.length; i++) {
            newMin = i;   // Assume elem i (h[i]) is the minimum
            for (j = i + 1; j < cards.length; j++) {
                if (cards[j].getRank() < cards[newMin].getRank()) {
                    newMin = j;
                }
            }
            swap(cards, i, newMin);
        }
    }

    /**
     * Sort cards by suit
     *
     * @param cards The 5-card combination to evaluate.
     */
    private static void sortBySuit(Card[] cards) {
        int i, j, newMin;
        for (i = 0; i < cards.length; i++) {
            newMin = i;   // Assume elem i (h[i]) is the minimum
            for (j = i + 1; j < cards.length; j++) {
                if (cards[j].getSuit() < cards[newMin].getSuit()) {
                    newMin = j;
                }
            }
            swap(cards, i, newMin);
        }
    }

    /**
     * Swap two elements in an array.
     *
     * @param arr The array in which elements must be swapped.
     * @param pos1 The index of the first element.
     * @param pos2 The index of the second element.
     */
    private static void swap(final Card[] arr, final int pos1, final int pos2) {
        Card temp = arr[pos1];
        arr[pos1] = arr[pos2];
        arr[pos2] = temp;
    }
}
