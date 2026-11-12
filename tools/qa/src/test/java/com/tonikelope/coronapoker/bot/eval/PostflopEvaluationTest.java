/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.bot.eval;

import java.util.Arrays;
import org.alberta.poker.Hand;
import org.alberta.poker.HandEvaluator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("bot-regression")
class PostflopEvaluationTest {

    private final BotEvaluator reference = new AlbertaEvaluatorAdapter();
    private final BotEvaluator evaluator = new MemoizedAlbertaEvaluator();

    @Test
    void sharesTheSameRunoutAcrossEveryOpponentOnTheTurn() {
        // Ace-high flush draw. Completing the draw changes everyone's
        // chance to beat us at the same time.
        int[] turn = {26, 31, 7, 41};
        PostflopEvaluation actual = assertMatchesRunoutEnumeration(38, 37, turn);
        assertTrue(actual.equity(5) > Math.pow(actual.equity(1), 5) + 0.10);
    }

    @Test
    void countsBothFutureCardsOnTheFlopAndPreservesTies() {
        // Two low cards with a suited Broadway flop. One river can put
        // the royal flush on the board and make every seat split the pot.
        assertMatchesRunoutEnumeration(13, 26, new int[]{8, 9, 10});
    }

    @Test
    void theOnlyAceOfClubsCannotBeInBothOpponentsHands() {
        int[] river = {0, 2, 5, 9, 24};
        PostflopEvaluation actual = evaluator.evaluatePostflop(11, 14, river, 9);
        assertEquals(43.0 / 45, actual.equity(1), 1e-12);
        assertEquals(41.0 / 45, actual.equity(2), 1e-12);
        assertEquals(39.0 / 45, actual.equity(3), 1e-12);
        for (int rivals = 4; rivals <= 9; rivals++) {
            double exact = (45 - 2.0 * rivals) / 45;
            assertEquals(exact, actual.equity(rivals), 1e-12);
        }
    }

    @Test
    void royalFlushOnBoardSplitsExactlyAcrossEveryTableSize() {
        var actual = evaluator.evaluatePostflop(13, 26, new int[]{8, 9, 10, 11, 12}, 9);
        for (int rivals = 1; rivals <= 9; rivals++) {
            assertEquals(1.0 / (rivals + 1), actual.equity(rivals), 1e-12);
        }
    }

    @Test
    void threeRiverOpponentsMatchExhaustiveDisjointHoldingsWithChops() {
        int[] river = {0, 14, 31, 35, 22};
        int hole1 = 23, hole2 = 45;
        int[] ranks = evaluator.opponentRanks(river);
        int hero = ranks[hole1 * 52 + hole2];
        long[] masks = new long[990];
        int[] tied = new int[990];
        long heroCards = (1L << hole1) | (1L << hole2);
        int candidates = 0;
        for (int a = 0; a < 51; a++) {
            for (int b = a + 1; b < 52; b++) {
                long mask = (1L << a) | (1L << b);
                int rank = ranks[a * 52 + b];
                if ((mask & heroCards) == 0 && rank >= 0 && rank <= hero) {
                    masks[candidates] = mask;
                    tied[candidates++] = rank == hero ? 1 : 0;
                }
            }
        }
        long payout = 0;
        for (int a = 0; a < candidates - 2; a++) {
            for (int b = a + 1; b < candidates - 1; b++) {
                if ((masks[a] & masks[b]) != 0) {
                    continue;
                }
                long used = masks[a] | masks[b];
                for (int c = b + 1; c < candidates; c++) {
                    if ((used & masks[c]) == 0) {
                        payout += 72 / (1 + tied[a] + tied[b] + tied[c]);
                    }
                }
            }
        }
        double expected = payout / (12.0 * 990 * 903 * 820);
        assertTrue(expected > 0 && expected < 1);
        assertEquals(expected, evaluator.evaluatePostflop(hole1, hole2, river, 3).equity(3), 1e-12);
    }

    @Test
    void ranksEachTerminalCardSetOnlyOnce() {
        CountingEvaluator ranks = new CountingEvaluator();
        MemoizedHandPotential potential = new MemoizedHandPotential(ranks);
        int[] board = {26, 31, 7};
        potential.evaluate(38, 37, board, true, 9);
        int opponentPairs = 47 * 46 / 2;
        int terminalSets = 47 * 46 * 45 * 44 / 24;
        assertEquals(1 + 2 * opponentPairs + terminalSets, ranks.calls);

        ranks.calls = 0;
        PostflopEvaluation second = potential.evaluate(13, 41, board, true, 9);
        int sharedSets = 45 * 44 * 43 * 42 / 24;
        assertEquals(1 + 2 * opponentPairs + terminalSets - sharedSets, ranks.calls);
        assertEquals(evaluator.evaluatePostflop(13, 41, board, 9).equity(9), second.equity(9));

        int[] newBoard = {26, 31, 6};
        PostflopEvaluation nextHand = potential.evaluate(13, 41, newBoard, true, 9);
        assertEquals(evaluator.evaluatePostflop(13, 41, newBoard, 9).equity(9), nextHand.equity(9));
    }

    private PostflopEvaluation assertMatchesRunoutEnumeration(int hole1, int hole2, int[] board) {
        PostflopEvaluation actual = evaluator.evaluatePostflop(hole1, hole2, board, 9);
        assertEquals(reference.showdownOdds(hole1, hole2, board), actual.currentOdds());
        assertEquals(1, actual.equity(0));

        boolean[] used = new boolean[52];
        used[hole1] = used[hole2] = true;
        for (int card : board) {
            used[card] = true;
        }
        int[] river = Arrays.copyOf(board, 5);
        double[] expected = new double[10];
        int runouts = 0;
        for (int first = 0; first < 52; first++) {
            if (used[first]) {
                continue;
            }
            river[board.length] = first;
            if (board.length == 4) {
                addRunout(expected, hole1, hole2, river);
                runouts++;
            } else {
                for (int second = first + 1; second < 52; second++) {
                    if (!used[second]) {
                        river[4] = second;
                        addRunout(expected, hole1, hole2, river);
                        runouts++;
                    }
                }
            }
        }
        for (int opponents = 1; opponents <= 2; opponents++) {
            assertEquals(expected[opponents] / runouts, actual.equity(opponents), 1e-12,
                    "pot share vs " + opponents);
        }
        for (int opponents = 3; opponents <= 9; opponents++) {
            assertTrue(actual.equity(opponents) >= 0);
            assertTrue(actual.equity(opponents) <= actual.equity(opponents - 1));
        }
        return actual;
    }

    private void addRunout(double[] expected, int hole1, int hole2, int[] river) {
        int[] cards = Arrays.copyOf(river, 7);
        cards[5] = hole1;
        cards[6] = hole2;
        int heroRank = reference.rankHand(cards);
        long used = (1L << hole1) | (1L << hole2);
        for (int card : river) {
            used |= 1L << card;
        }
        int pairs = 0, wins = 0, ties = 0;
        long[] masks = new long[990];
        boolean[] draws = new boolean[990];
        for (int a = 0; a < 52; a++) {
            if ((used & (1L << a)) != 0) {
                continue;
            }
            cards[5] = a;
            for (int b = a + 1; b < 52; b++) {
                if ((used & (1L << b)) != 0) {
                    continue;
                }
                cards[6] = b;
                int rank = reference.rankHand(cards);
                if (heroRank >= rank) {
                    masks[pairs] = (1L << a) | (1L << b);
                    draws[pairs] = rank == heroRank;
                    pairs++;
                    if (rank == heroRank) {
                        ties++;
                    } else {
                        wins++;
                    }
                }
            }
        }
        int threeWayUnits = 0;
        for (int a = 0; a < pairs; a++) {
            for (int b = a + 1; b < pairs; b++) {
                if ((masks[a] & masks[b]) == 0) {
                    int tied = (draws[a] ? 1 : 0) + (draws[b] ? 1 : 0);
                    // Each unordered pair has two orders. Six units per pot.
                    threeWayUnits += 12 / (tied + 1);
                }
            }
        }
        double exactThreeWay = threeWayUnits / (6.0 * 990 * 903);
        double win = wins / 990.0;
        double tie = ties / 990.0;
        expected[1] += win + tie / 2;
        expected[2] += exactThreeWay;
    }

    private static final class CountingEvaluator extends HandEvaluator {
        private int calls;

        @Override
        public int rankHand(Hand hand) {
            calls++;
            return super.rankHand(hand);
        }
    }
}
