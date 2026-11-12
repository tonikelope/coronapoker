/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.bot.eval;

import com.tonikelope.coronapoker.bot.eval.HandStrengthEvaluator.ShowdownOdds;
import java.util.Arrays;
import org.alberta.poker.Card;
import org.alberta.poker.Hand;
import org.alberta.poker.HandEvaluator;

/** Exhaustive heads-up runouts, reusing ranks across assignments of the same cards. */
final class MemoizedHandPotential {

    private static final int AHEAD = 0;
    private static final int TIED = 1;
    private static final int BEHIND = 2;
    private static final int[][] COMBINATIONS = combinations();

    private final HandEvaluator evaluator;
    private long rankedBoard;
    private boolean rankedTwoCards;
    private int[] terminalRanks = new int[0];

    MemoizedHandPotential(HandEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    synchronized PostflopEvaluation evaluate(int hole1, int hole2, int[] board, boolean twoCards, int opponents) {
        long boardMask = boardMask(board);
        int[] live = remainingCards(includeCard(includeCard(boardMask, hole1), hole2), board.length);
        int n = live.length;
        prepareTerminalRanks(boardMask, twoCards);
        Hand opponent = hand(board);
        Hand hero = new Hand(opponent);
        hero.addCard(hole1);
        hero.addCard(hole2);
        int heroRank = evaluator.rankHand(hero);

        Runouts runouts = new Runouts(twoCards ? n * n : n, n, opponents);
        for (int a = 0; a < n; a++) {
            hero.addCard(live[a]);
            if (twoCards) {
                for (int b = a + 1; b < n; b++) {
                    hero.addCard(live[b]);
                    runouts.heroRanks[a * n + b] = evaluator.rankHand(hero);
                    hero.removeCard();
                }
            } else {
                runouts.heroRanks[a] = evaluator.rankHand(hero);
            }
            hero.removeCard();
        }

        for (int a = 0; a < n - 1; a++) {
            opponent.addCard(live[a]);
            for (int b = a + 1; b < n; b++) {
                opponent.addCard(live[b]);
                int outcome = outcome(heroRank, evaluator.rankHand(opponent));
                runouts.current[a * n + b] = outcome;
                runouts.currentTotals[outcome]++;
                opponent.removeCard();
            }
            opponent.removeCard();
        }

        // A terminal opponent rank depends on the set of three or four added
        // cards. Rank that set once, then count every legal split between the
        // opponent's hole cards and the public runout.
        for (int a = 0; a < n - 2; a++) {
            opponent.addCard(live[a]);
            for (int b = a + 1; b < n - 1; b++) {
                opponent.addCard(live[b]);
                for (int c = b + 1; c < n; c++) {
                    opponent.addCard(live[c]);
                    int triple = live[a] + COMBINATIONS[2][live[b]] + COMBINATIONS[3][live[c]];
                    if (twoCards) {
                        for (int d = c + 1; d < n; d++) {
                            int key = triple + COMBINATIONS[4][live[d]];
                            int cached = terminalRanks[key];
                            if (cached == 0) {
                                opponent.addCard(live[d]);
                                cached = evaluator.rankHand(opponent) + 1;
                                terminalRanks[key] = cached;
                                opponent.removeCard();
                            }
                            int rank = cached - 1;
                            int ab = a * n + b, ac = a * n + c, ad = a * n + d;
                            int bc = b * n + c, bd = b * n + d, cd = c * n + d;
                            runouts.record(ab, c, d, rank);
                            runouts.record(ac, b, d, rank);
                            runouts.record(ad, b, c, rank);
                            runouts.record(bc, a, d, rank);
                            runouts.record(bd, a, c, rank);
                            runouts.record(cd, a, b, rank);
                        }
                    } else {
                        int cached = terminalRanks[triple];
                        if (cached == 0) {
                            cached = evaluator.rankHand(opponent) + 1;
                            terminalRanks[triple] = cached;
                        }
                        int rank = cached - 1;
                        runouts.record(a, b, c, rank);
                        runouts.record(b, a, c, rank);
                        runouts.record(c, a, b, rank);
                    }
                    opponent.removeCard();
                }
                opponent.removeCard();
            }
            opponent.removeCard();
        }

        return runouts.evaluation(n, twoCards, opponents);
    }

    PostflopEvaluation evaluateRiver(int hole1, int hole2, int[] board, int opponents) {
        long used = includeCard(includeCard(boardMask(board), hole1), hole2);
        int[] live = remainingCards(used, board.length);
        Hand opponent = hand(board);
        Hand hero = new Hand(opponent);
        hero.addCard(hole1);
        hero.addCard(hole2);
        int heroRank = evaluator.rankHand(hero);
        RunoutOdds odds = new RunoutOdds(1, live.length, opponents);
        for (int a = 0; a < live.length - 1; a++) {
            opponent.addCard(live[a]);
            for (int b = a + 1; b < live.length; b++) {
                opponent.addCard(live[b]);
                odds.record(0, a, b, outcome(heroRank, evaluator.rankHand(opponent)));
                opponent.removeCard();
            }
            opponent.removeCard();
        }
        double[] equity = new double[opponents + 1];
        equity[0] = 1;
        odds.addEquity(equity, 0, live.length);
        return new PostflopEvaluation(odds.showdownOdds(0, live.length), Potential.ZERO, equity);
    }

    private static long boardMask(int[] board) {
        long mask = 0;
        for (int card : board) {
            mask = includeCard(mask, card);
        }
        return mask;
    }

    private static Hand hand(int[] board) {
        Hand hand = new Hand();
        for (int card : board) {
            hand.addCard(card);
        }
        return hand;
    }

    private static int outcome(int hero, int opponent) {
        return hero > opponent ? AHEAD : hero == opponent ? TIED : BEHIND;
    }

    private static int[] remainingCards(long used, int boardSize) {
        int[] live = new int[Card.NUM_CARDS - 2 - boardSize];
        int next = 0;
        for (int card = 0; card < Card.NUM_CARDS; card++) {
            if ((used & (1L << card)) == 0) {
                live[next++] = card;
            }
        }
        return live;
    }

    private static long includeCard(long used, int card) {
        long bit = 1L << card;
        if (card < 0 || card >= Card.NUM_CARDS || (used & bit) != 0) {
            throw new IllegalArgumentException("Invalid or duplicate card index: " + card);
        }
        return used | bit;
    }

    private static int combinationsOfTwo(int cards) {
        return cards * (cards - 1) / 2;
    }

    private void prepareTerminalRanks(long board, boolean twoCards) {
        if (rankedBoard != board || rankedTwoCards != twoCards) {
            int slots = COMBINATIONS[twoCards ? 4 : 3][Card.NUM_CARDS];
            if (terminalRanks.length < slots) {
                terminalRanks = new int[slots];
            } else {
                Arrays.fill(terminalRanks, 0, slots, 0);
            }
            rankedBoard = board;
            rankedTwoCards = twoCards;
        }
    }

    private static int[][] combinations() {
        int[][] counts = new int[5][Card.NUM_CARDS + 1];
        Arrays.fill(counts[0], 1);
        for (int cards = 1; cards <= Card.NUM_CARDS; cards++) {
            for (int chosen = 1; chosen <= 4; chosen++) {
                counts[chosen][cards] = counts[chosen][cards - 1] + counts[chosen - 1][cards - 1];
            }
        }
        return counts;
    }

    private static final class Runouts {
        private final int[] heroRanks;
        private final int[] current;
        private final RunoutOdds terminal;
        private final int cards;
        private final int[] currentTotals = new int[3];
        private final int[][] transitions = new int[3][3];

        private Runouts(int runoutSlots, int cards, int opponents) {
            heroRanks = new int[runoutSlots];
            current = new int[cards * cards];
            terminal = new RunoutOdds(runoutSlots, cards, opponents);
            this.cards = cards;
        }

        private void record(int runout, int first, int second, int opponentRank) {
            int result = outcome(heroRanks[runout], opponentRank);
            transitions[current[first * cards + second]][result]++;
            terminal.record(runout, first, second, result);
        }

        private PostflopEvaluation evaluation(int cards, boolean twoCards, int opponents) {
            int currentPairs = combinationsOfTwo(cards);
            ShowdownOdds odds = new ShowdownOdds(
                    (double) currentTotals[AHEAD] / currentPairs, (double) currentTotals[TIED] / currentPairs);
            int runsPerOpponent = twoCards ? combinationsOfTwo(cards - 2) : cards - 2;
            double tied = currentTotals[TIED] * 0.5;
            double gainDenominator = runsPerOpponent * (currentTotals[BEHIND] + tied);
            double lossDenominator = runsPerOpponent * (currentTotals[AHEAD] + tied);
            double gain = transitions[BEHIND][AHEAD]
                    + 0.5 * (transitions[BEHIND][TIED] + transitions[TIED][AHEAD]);
            double loss = transitions[AHEAD][BEHIND]
                    + 0.5 * (transitions[AHEAD][TIED] + transitions[TIED][BEHIND]);
            Potential potential = new Potential(gainDenominator > 0 ? gain / gainDenominator : 0,
                    lossDenominator > 0 ? loss / lossDenominator : 0);

            double[] equity = new double[opponents + 1];
            equity[0] = 1;
            if (opponents > 0) {
                int availableCards = cards - (twoCards ? 2 : 1);
                int runoutCount = twoCards ? combinationsOfTwo(cards) : cards;
                for (int a = 0; a < cards; a++) {
                    if (twoCards) {
                        for (int b = a + 1; b < cards; b++) {
                            terminal.addEquity(equity, a * cards + b, availableCards);
                        }
                    } else {
                        terminal.addEquity(equity, a, availableCards);
                    }
                }
                for (int i = 1; i <= opponents; i++) {
                    equity[i] /= runoutCount;
                }
            }
            return new PostflopEvaluation(odds, potential, equity);
        }
    }

    private static final class RunoutOdds {
        private final int[] wins;
        private final int[] ties;
        private final byte[] winsByCard;
        private final byte[] tiesByCard;
        private final int cards;

        private RunoutOdds(int runouts, int cards, int opponents) {
            this.cards = cards;
            wins = new int[runouts];
            ties = new int[runouts];
            winsByCard = opponents >= 2 ? new byte[runouts * cards] : null;
            tiesByCard = opponents >= 2 ? new byte[runouts * cards] : null;
        }

        private void record(int runout, int first, int second, int result) {
            if (result == AHEAD) {
                wins[runout]++;
                countCards(winsByCard, runout, first, second);
            } else if (result == TIED) {
                ties[runout]++;
                countCards(tiesByCard, runout, first, second);
            }
        }

        private void countCards(byte[] counts, int runout, int first, int second) {
            if (counts != null) {
                int offset = runout * cards;
                counts[offset + first]++;
                counts[offset + second]++;
            }
        }

        private ShowdownOdds showdownOdds(int runout, int availableCards) {
            int pairs = combinationsOfTwo(availableCards);
            return new ShowdownOdds((double) wins[runout] / pairs, (double) ties[runout] / pairs);
        }

        private void addEquity(double[] equity, int runout, int availableCards) {
            int opponentPairs = combinationsOfTwo(availableCards);
            double win = (double) wins[runout] / opponentPairs;
            double unbeaten = (double) (wins[runout] + ties[runout]) / opponentPairs;
            double shareVsTwo = equity.length > 2 ? exactShareVsTwo(runout, opponentPairs, availableCards) : 1;
            double sum = 1;
            double winPower = 1;
            for (int i = 1; i < equity.length; i++) {
                winPower *= win;
                sum = sum * unbeaten + winPower;
                if (i == 1) {
                    equity[i] += sum / 2;
                } else if (i == 2) {
                    equity[i] += shareVsTwo;
                } else {
                    equity[i] += Math.min(shareVsTwo, sum / (i + 1));
                }
            }
        }

        private double exactShareVsTwo(int runout, int pairs, int availableCards) {
            int win = wins[runout];
            int tie = ties[runout];
            int winWin = win * win + win;
            int winTie = win * tie;
            int tieTie = tie * tie + tie;
            int end = (runout + 1) * cards;
            // Subtract ordered pairs of holdings sharing a card. Identical
            // holdings are counted twice by their cards, hence +win and +tie.
            for (int i = runout * cards; i < end; i++) {
                int w = winsByCard[i];
                int t = tiesByCard[i];
                winWin -= w * w;
                winTie -= w * t;
                tieTie -= t * t;
            }
            // Either order of a win and a tie pays half, and two ties pay a third.
            return (winWin + winTie + tieTie / 3.0) / (pairs * combinationsOfTwo(availableCards - 2));
        }
    }
}
