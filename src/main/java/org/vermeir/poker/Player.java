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
package org.vermeir.poker;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.vermeir.poker.handranking.Card;
import org.vermeir.poker.handranking.StartingHand;
import org.vermeir.poker.handranking.util.HandFactory;
import org.vermeir.poker.handranking.util.HandRanker;
import org.vermeir.poker.handranking.util.HandRankingException;
import org.vermeir.poker.util.Combinations;

/**
 * This class represents a poker player.
 *
 * @author Jellen Vermeir
 */
public class Player {

    // The starting hand, representing the two player hole Cards.
    private final StartingHand holeCards;
    // The current gamestate.
    private final GameState gameState;
    // Unknown cards, from the players perspective
    private final Deck unknownCards;

    // This value represents the immediate handstrength
    private double handStrength;
    // This value respresents the implied odds
    private double positiveHandPotential;
    // This value represents the reverse implied odds
    private double negativeHandPotential;

    /**
     * Create a player, taking the two holecards and current gamestate as input.
     *
     * @param c1 The first holecard
     * @param c2 The second holecard
     * @param state The GameState
     * @throws HandRankingException
     */
    public Player(Card c1, Card c2, GameState state) throws HandRankingException {
        this.gameState = state;
        this.holeCards = new StartingHand(c1, c2);

        this.unknownCards = new Deck();
        this.unknownCards.removeCard(c1);
        this.unknownCards.removeCard(c2);
    }

    /**
     * Return the expected immediate strength of the Players Hand.
     *
     * @return The handStrength
     */
    public double getHandStrength() {
        return this.handStrength;
    }

    /**
     * Return the expected positive Player hand potential.
     *
     * @return the positive hand potential.
     */
    public double getPositiveHandPotential() {
        return this.positiveHandPotential;
    }

    /**
     * Return the expected negative Hand Potential.
     *
     * @return the negative hand potential.
     */
    public double getNegativeHandPotential() {
        return this.negativeHandPotential;
    }

    /**
     * This function calculates and sets the immediate expected hand strength
     * given the opponent starting hand probabilities and the number of expected
     * active oponents as inputs. The unknown cards are derived from the
     * communitycards and the Player holecards.
     *
     * @param weights the fieldArray containing the starting hand probabilities.
     * The result should be fetched throught the corresponding getters.
     * @param nrOpponents The number of opponents
     * @throws HandRankingException
     */
    public void calculateHandStrength(List<List<Double>> weights, int nrOpponents) throws HandRankingException {
        List<Card> uCards = this.unknownCards.getCards();
        calculateHandStatistics(weights, nrOpponents, false, false, uCards);
    }

    /**
     * This function calculates and sets the immediate expected hand strength
     * given the opponent starting hand probabilities, the number of active
     * oponents and a list of unknown cards available in the Deck as inputs.
     *
     * @param weights the fieldArray containing the starting hand probabilities.
     * The result should be fetched throught the corresponding getters.
     * @param nrOpponents The number of opponents
     * @param unknownCards The unknown cards
     * @throws HandRankingException
     */
    public void calculateHandStrength(List<List<Double>> weights, int nrOpponents, List<Card> unknownCards) throws HandRankingException {
        calculateHandStatistics(weights, nrOpponents, false, false, unknownCards);
    }

    /**
     * This function calculates and sets the positive and negative hand
     * potential, given the opponent starting hand probabilities and the number
     * of expected active oponents as inputs. The unknown cards are derived from
     * the communitycards and the Player holecards.
     *
     * @param weights the fieldArray containing the starting hand probabilities.
     * The result should be fetched throught the corresponding getters.
     * @param nrOpponents The number of opponents
     * @param effectiveOdds True for two step ahead analysis
     * @throws HandRankingException
     */
    public void calculateHandPotential(List<List<Double>> weights, int nrOpponents, boolean effectiveOdds) throws HandRankingException {
        List<Card> uCards = this.unknownCards.getCards();
        calculateHandStatistics(weights, nrOpponents, true, effectiveOdds, uCards);
    }

    /**
     * This function calculates and sets the positive and negative hand
     * potential, given the opponent starting hand probabilities, the number of
     * active oponents and a list of unknown cards available in the Deck as
     * inputs.
     *
     * @param weights the fieldArray containing the starting hand probabilities.
     * The result should be fetched throught the corresponding getters.
     * @param nrOpponents The number of opponents.
     * @param effectiveOdds True for two step ahead analysis.
     * @param unknownCards The unknown cards
     * @throws HandRankingException
     */
    public void calculateHandPotential(List<List<Double>> weights, int nrOpponents, boolean effectiveOdds, List<Card> unknownCards) throws HandRankingException {
        calculateHandStatistics(weights, nrOpponents, true, effectiveOdds, unknownCards);
    }

    private void calculateHandStatistics(List<List<Double>> weights, int nrOpponents, boolean calculatePotential,
            boolean effectiveOdds, List<Card> uCards) throws HandRankingException {

        final int AHEAD = 0;
        final int BEHIND = 1;
        final int TIED = 2;
        // ahead, behind, tied
        double[] outcomes = new double[]{0, 0, 0}; // for handstrength
        // Transitions from ahead/behind/tied to ahead/behind/tied
        double[][] transitionMatrix = new double[3][3]; // for potential
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                transitionMatrix[i][j] = 0;
            }
        }

        // Opponent holecard combinations
        List<List<Integer>> combinations = Combinations.combine(uCards.size(), 2);
        // BoardCard Combinations
        List<List<Integer>> boardCombinations
                = Combinations.combine(uCards.size() - 2, effectiveOdds ? 2 : 1);

        // Fetch communitycards
        Set<Card> communityCards = this.gameState.getCommunityCards();
        // Fetch player holecards
        Set<Card> hCards = this.holeCards.getCards();

        List<Card> playerCards = new ArrayList<>(communityCards);
        playerCards.addAll(hCards);
        List<Card> enemyCards = new ArrayList<>(7);

        int initialPlayerHandValue
                = HandFactory.getHand(playerCards).getHandValue();

        // for each possible set of opponent hole cards
        for (List<Integer> comb : combinations) {

            Card u1 = uCards.get(comb.get(0) - 1);
            Card u2 = uCards.get(comb.get(1) - 1);

            enemyCards.clear();
            enemyCards.addAll(communityCards);
            enemyCards.add(u1);
            enemyCards.add(u2);

            int initialEnemyHandValue
                    = HandFactory.getHand(enemyCards).getHandValue();

            int initialStatus;
            double enemyCardWeights = HandRanker.Map_169(u1, u2, weights);

            if (initialPlayerHandValue > initialEnemyHandValue) {
                initialStatus = AHEAD;
                outcomes[AHEAD] += enemyCardWeights;
            } else if (initialPlayerHandValue < initialEnemyHandValue) {
                initialStatus = BEHIND;
                outcomes[BEHIND] += enemyCardWeights;
            } else {
                initialStatus = TIED;
                outcomes[TIED] += enemyCardWeights;
            }

            if (calculatePotential) {
                List<Card> remainingCards = new ArrayList<>(uCards);
                remainingCards.remove(u1);
                remainingCards.remove(u2);

                List<Card> impliedPlayerCards = new ArrayList<>(7);
                List<Card> impliedEnemyCards = new ArrayList<>(7);

                for (List<Integer> boardCombi : boardCombinations) {
                    impliedPlayerCards.clear();
                    impliedEnemyCards.clear();
                    impliedPlayerCards.addAll(playerCards);
                    impliedEnemyCards.addAll(enemyCards);

                    Card u3 = remainingCards.get(boardCombi.get(0) - 1);
                    impliedPlayerCards.add(u3);
                    impliedEnemyCards.add(u3);
                    if (effectiveOdds) {
                        Card u4 = remainingCards.get(boardCombi.get(1) - 1);
                        impliedPlayerCards.add(u4);
                        impliedEnemyCards.add(u4);
                    }

                    int impliedPlayerHandValue
                            = HandFactory.getHand(impliedPlayerCards).getHandValue();
                    int impliedEnemyHandValue
                            = HandFactory.getHand(impliedEnemyCards).getHandValue();

                    if (impliedPlayerHandValue > impliedEnemyHandValue) {
                        transitionMatrix[initialStatus][AHEAD] += enemyCardWeights;
                    } else if (impliedPlayerHandValue < impliedEnemyHandValue) {
                        transitionMatrix[initialStatus][BEHIND] += enemyCardWeights;
                    } else {
                        transitionMatrix[initialStatus][TIED] += enemyCardWeights;
                    }
                }
            }
        }

        double hs = (outcomes[AHEAD] + outcomes[TIED] / 2) / (outcomes[AHEAD] + outcomes[BEHIND] + outcomes[TIED]);
        this.handStrength = Math.pow(hs, nrOpponents);
        if (calculatePotential) {

            // printStats(outcomes, transitionMatrix);
            double sumBehind = transitionMatrix[BEHIND][BEHIND] + transitionMatrix[BEHIND][AHEAD] + transitionMatrix[BEHIND][TIED];
            double sumAhead = transitionMatrix[AHEAD][BEHIND] + transitionMatrix[AHEAD][AHEAD] + transitionMatrix[AHEAD][TIED];
            double sumTied = transitionMatrix[TIED][BEHIND] + transitionMatrix[TIED][AHEAD] + transitionMatrix[TIED][TIED];

            this.positiveHandPotential = (transitionMatrix[BEHIND][AHEAD] + transitionMatrix[BEHIND][TIED] / 2 + transitionMatrix[TIED][AHEAD] / 2)
                    / (sumBehind + sumTied / 2);
            this.negativeHandPotential = (transitionMatrix[AHEAD][BEHIND] + transitionMatrix[AHEAD][TIED] / 2 + transitionMatrix[TIED][BEHIND] / 2)
                    / (sumAhead + sumTied / 2);

            //printStats(outcomes, transitionMatrix);
        }
    }

    /**
     * Debug DEMO
     *
     * @param outcomes The outcomes, for evaluation of immediate handStrength.
     * @param transitionMatrix The transitionMatrix, for evaluation of (reverse)
     * implied odds.
     */
    private void printStats(double[] outcomes, double[][] transitionMatrix) {

        final int AHEAD = 0;
        final int BEHIND = 1;
        final int TIED = 2;
        double sumBehind = transitionMatrix[BEHIND][BEHIND] + transitionMatrix[BEHIND][AHEAD] + transitionMatrix[BEHIND][TIED];
        double sumAhead = transitionMatrix[AHEAD][BEHIND] + transitionMatrix[AHEAD][AHEAD] + transitionMatrix[AHEAD][TIED];
        double sumTied = transitionMatrix[TIED][BEHIND] + transitionMatrix[TIED][AHEAD] + transitionMatrix[TIED][TIED];

        System.out.println("Handstrength (current board):");
        System.out.println("Ahead weighted sum: " + outcomes[AHEAD]);
        System.out.println("Behind weighted sum: " + outcomes[BEHIND]);
        System.out.println("Tied weighted sum: " + outcomes[TIED]);
        double hs = (outcomes[AHEAD] + outcomes[TIED] / 2) / (outcomes[AHEAD] + outcomes[BEHIND] + outcomes[TIED]);
        System.out.println("Handstrength one opponent: " + hs);

        System.out.println("TRANSITIONS (currently ahead)");
        System.out.println("Total simulations sarting ahead: " + sumAhead);
        System.out.println("Ahead-Ahead: " + transitionMatrix[AHEAD][AHEAD]);
        System.out.println("Ahead-Tied: " + transitionMatrix[AHEAD][TIED]);
        System.out.println("Ahead-Behind: " + transitionMatrix[AHEAD][BEHIND]);

        System.out.println("TRANSITIONS (currently behind)");
        System.out.println("Total simulations sarting behind: " + sumBehind);
        System.out.println("Behind-Ahead: " + transitionMatrix[BEHIND][AHEAD]);
        System.out.println("Behind-Tied: " + transitionMatrix[BEHIND][TIED]);
        System.out.println("Behind-Behind" + transitionMatrix[BEHIND][BEHIND]);

        System.out.println("TRANSITIONS (currently tied)");
        System.out.println("Total simulations sarting tied: " + sumTied);
        System.out.println("Tied-Ahead: " + transitionMatrix[TIED][AHEAD]);
        System.out.println("Tied-Tied: " + transitionMatrix[TIED][TIED]);
        System.out.println("Tied-Behind: " + transitionMatrix[TIED][BEHIND]);
    }
}
