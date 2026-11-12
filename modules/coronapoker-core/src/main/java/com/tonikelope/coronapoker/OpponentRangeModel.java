/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.bot.eval.BotEvaluator;
import com.tonikelope.coronapoker.bot.eval.HandStrengthEvaluator.ShowdownOdds;
import com.tonikelope.coronapoker.core.game.GameOpponentStats;
import com.tonikelope.coronapoker.core.game.GameOpponentStats.PostflopAction;
import java.util.Arrays;

/** Weights legal holdings using a seat's accepted actions on each public board. */
final class OpponentRangeModel {

    private static final int CARDS = 52;
    private static final int PAIR_BITS = 12;
    private static final int PAIR_MASK = (1 << PAIR_BITS) - 1;
    private static final int LOSE = 0;
    private static final int TIE = 1;
    private static final int WIN = 2;
    private final BotEvaluator evaluator;
    private final int[] board;
    private final int[] legalPairs;
    private final int heroPair;
    private final int[][] ranksByStreet = new int[3][];
    private final long[][] orderByStreet = new long[3][];

    OpponentRangeModel(BotEvaluator evaluator, int hole1, int hole2, int[] board) {
        this.evaluator = evaluator;
        this.board = board.clone();
        heroPair = Math.min(hole1, hole2) * CARDS + Math.max(hole1, hole2);
        long blocked = (1L << hole1) | (1L << hole2);
        for (int card : board) {
            blocked |= 1L << card;
        }
        int remaining = CARDS - Long.bitCount(blocked);
        legalPairs = new int[remaining * (remaining - 1) / 2];
        int next = 0;
        for (int first = 0; first < CARDS; first++) {
            if ((blocked & (1L << first)) != 0) {
                continue;
            }
            for (int second = first + 1; second < CARDS; second++) {
                if ((blocked & (1L << second)) == 0) {
                    legalPairs[next++] = first * CARDS + second;
                }
            }
        }
    }

    record HoldingRange(double[] weights, ShowdownOdds odds) {}

    ShowdownOdds estimate(GameOpponentStats stats, boolean raisedPreflop) {
        HoldingRange range = range(stats, raisedPreflop);
        return range == null ? null : range.odds();
    }

    HoldingRange range(GameOpponentStats stats, boolean raisedPreflop) {
        int current = board.length - 3;
        boolean hasObservedAction = false;
        for (int i = 0; i <= current; i++) {
            PostflopAction action = stats.postflopAction(Crupier.FLOP + i);
            hasObservedAction |= action != PostflopAction.NONE && action != PostflopAction.FOLD;
        }
        if (!hasObservedAction || ranks(current) == null) {
            return null;
        }
        double[] weights = new double[CARDS * CARDS];
        double[][] openingRange = raisedPreflop || stats.playedVoluntarilyPreflop()
                ? StartingHandRanges.weights(Math.min(0.85, stats.voluntaryPreflopRate() + (raisedPreflop ? 0.04 : 0.14)))
                : null;
        for (int pair : legalPairs) {
            weights[pair] = openingRange == null ? 1 : openingRange[pair / CARDS][pair % CARDS];
        }
        for (int i = 0; i <= current; i++) {
            PostflopAction action = stats.postflopAction(Crupier.FLOP + i);
            if (action == PostflopAction.NONE || action == PostflopAction.FOLD) {
                continue;
            }
            int street = Crupier.FLOP + i;
            double openingPrior = street == Crupier.FLOP ? (raisedPreflop ? 0.66 : 0.36)
                    : (street == Crupier.TURN ? 0.46 : 0.39);
            double width;
            double floor;
            switch (action) {
                case CHECK -> {
                    width = 1 - stats.openingBetRate(street, openingPrior);
                    floor = 0.58;
                }
                case CALL -> {
                    double foldPrior = stats.isStation() || stats.looksPassiveStation() ? 0
                            : (street == Crupier.FLOP ? 0.42 : 0.52);
                    width = 1 - stats.foldToBetRate(street, false, foldPrior);
                    floor = street == Crupier.FLOP ? 0.50 : 0.35;
                }
                case BET -> {
                    width = stats.openingBetRate(street, openingPrior);
                    floor = street == Crupier.FLOP ? 0.45 : street == Crupier.TURN ? 0.25 : 0.17;
                }
                case RAISE -> {
                    width = stats.raiseToBetRate(street, 0.16);
                    floor = street == Crupier.FLOP ? 0.27 : 0.15;
                }
                default -> throw new IllegalStateException("Unexpected action: " + action);
            }
            if (width < 0.98) {
                condition(weights, order(i), Math.max(0.05, width), floor, action == PostflopAction.CHECK);
            }
        }
        int[] ranks = ranks(current);
        int heroRank = ranks[heroPair];
        double win = 0;
        double tie = 0;
        double total = 0;
        for (int pair : legalPairs) {
            double weight = weights[pair];
            int rank = ranks[pair];
            if (heroRank > rank) {
                win += weight;
            } else if (heroRank == rank) {
                tie += weight;
            }
            total += weight;
        }
        return new HoldingRange(weights, new ShowdownOdds(win / total, tie / total));
    }

    /** Exact two-rival showdown share for these ranges on this board. */
    double twoOpponentShare(HoldingRange first, HoldingRange second) {
        int[] ranks = ranks(board.length - 3);
        int hero = ranks[heroPair];
        double[] totalFirst = new double[3];
        double[] totalSecond = new double[3];
        double[] sameHolding = new double[3];
        double[][] cardsFirst = new double[3][CARDS];
        double[][] cardsSecond = new double[3][CARDS];
        for (int pair : legalPairs) {
            int outcome = ranks[pair] < hero ? WIN : ranks[pair] == hero ? TIE : LOSE;
            int a = pair / CARDS;
            int b = pair % CARDS;
            double left = first.weights()[pair];
            double right = second.weights()[pair];
            totalFirst[outcome] += left;
            totalSecond[outcome] += right;
            cardsFirst[outcome][a] += left;
            cardsFirst[outcome][b] += left;
            cardsSecond[outcome][a] += right;
            cardsSecond[outcome][b] += right;
            sameHolding[outcome] += left * right;
        }
        double total = 0;
        double payout = 0;
        for (int left = LOSE; left <= WIN; left++) {
            for (int right = LOSE; right <= WIN; right++) {
                double mass = totalFirst[left] * totalSecond[right];
                // Subtract overlapping cards. An identical holding was
                // subtracted twice, so add it back once.
                for (int card = 0; card < CARDS; card++) {
                    mass -= cardsFirst[left][card] * cardsSecond[right][card];
                }
                if (left == right) {
                    mass += sameHolding[left];
                }
                total += mass;
                if (left != LOSE && right != LOSE) {
                    int ties = (left == TIE ? 1 : 0) + (right == TIE ? 1 : 0);
                    payout += mass / (1 + ties);
                }
            }
        }
        return payout / total;
    }

    ThreeSeatShowdown threeSeatShowdown(HoldingRange[] holdings) {
        int[] riverRanks = ranks(2);
        double[][] weights = new double[3][];
        for (int seat = 0; seat < weights.length; seat++) {
            weights[seat] = holdings[seat] == null ? null : holdings[seat].weights();
        }
        return new ThreeSeatShowdown(legalPairs, riverRanks, riverRanks[heroPair], weights);
    }

    MultiwayRangeShowdown jointShowdown(HoldingRange[] holdings) {
        int draws = board.length == 5 ? 4096 : 2048;
        double[][] weights = new double[holdings.length][];
        for (int seat = 0; seat < weights.length; seat++) {
            weights[seat] = holdings[seat] == null ? null : holdings[seat].weights();
        }
        return new MultiwayRangeShowdown(evaluator, heroPair, board, board.length == 5 ? ranks(2) : null,
                legalPairs, weights, draws);
    }

    private int[] ranks(int street) {
        if (ranksByStreet[street] == null) {
            ranksByStreet[street] = evaluator.opponentRanks(Arrays.copyOf(board, 3 + street));
        }
        return ranksByStreet[street];
    }

    private long[] order(int street) {
        if (orderByStreet[street] == null) {
            int[] ranks = ranks(street);
            long[] order = new long[legalPairs.length];
            for (int i = 0; i < legalPairs.length; i++) {
                int pair = legalPairs[i];
                order[i] = ((long) ranks[pair] << PAIR_BITS) | pair;
            }
            Arrays.sort(order);
            orderByStreet[street] = order;
        }
        return orderByStreet[street];
    }

    private static void condition(double[] weights, long[] order, double width, double floor, boolean weak) {
        double total = 0;
        for (long entry : order) {
            total += weights[(int) entry & PAIR_MASK];
        }
        double below = 0;
        for (int from = 0; from < order.length;) {
            int to = from + 1;
            long rank = order[from] >>> PAIR_BITS;
            double groupWeight = weights[(int) order[from] & PAIR_MASK];
            while (to < order.length && order[to] >>> PAIR_BITS == rank) {
                groupWeight += weights[(int) order[to++] & PAIR_MASK];
            }
            double percentile = (below + groupWeight / 2) / total;
            double distance = weak ? width - percentile : percentile - (1 - width);
            // Smooth the boundary and keep some weight for draws, traps and bluffs.
            double likelihood = floor + (1 - floor) * Math.max(0, Math.min(1, 0.5 + distance / 0.20));
            for (int index = from; index < to; index++) {
                weights[(int) order[index] & PAIR_MASK] *= likelihood;
            }
            below += groupWeight;
            from = to;
        }
    }
}
