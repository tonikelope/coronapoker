/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.bot.eval.BotEvaluator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.SplittableRandom;

/** Joint showdown shares for independent ranges conditioned on a legal deal. */
final class MultiwayRangeShowdown {

    private static final int CARDS = 52;
    private static final long DECK = (1L << CARDS) - 1;
    private static final int EXACT_LIMIT = 65_536;
    private static final int MIN_EFFECTIVE_DRAWS = 128;
    private final int[] losses;
    private final int[] ties;
    private final double[] mass;
    private final double[] shares;
    private int deals;
    private double total;
    private double squaredMass;
    private final boolean reliable;

    MultiwayRangeShowdown(BotEvaluator evaluator, int heroPair, int[] board, int[] riverRanks,
            int[] legalPairs, double[][] weights, int draws) {
        Seat[] seats = new Seat[weights.length];
        long known = cardMask(heroPair);
        for (int card : board) {
            known |= 1L << card;
        }
        int product = 1;
        for (int seat = 0; seat < weights.length; seat++) {
            seats[seat] = new Seat(seat, legalPairs, weights[seat]);
            product = (int) Math.min(EXACT_LIMIT + 1L, (long) product * seats[seat].pairs.length);
        }
        Arrays.sort(seats, Comparator.comparingDouble(Seat::effectiveHoldings));
        boolean exact = board.length == 5 && product <= EXACT_LIMIT;
        losses = new int[exact ? product : draws];
        ties = new int[losses.length];
        mass = new double[losses.length];
        shares = new double[1 << seats.length];
        Arrays.fill(shares, Double.NaN);
        shares[0] = 1;
        long live = DECK ^ known;
        if (exact) {
            enumerate(seats, riverRanks, riverRanks[heroPair], 0, live, 1, 0, 0);
        } else if (product != 0) {
            sample(evaluator, heroPair, board, riverRanks, seats, live, draws);
        }
        reliable = total > 0 && (exact || total * total >= MIN_EFFECTIVE_DRAWS * squaredMass);
    }

    double share(int mask) {
        if (Double.isNaN(shares[mask]) && reliable) {
            double payout = 0;
            for (int i = 0; i < deals; i++) {
                if ((losses[i] & mask) == 0) {
                    payout += mass[i] / (1 + Integer.bitCount(ties[i] & mask));
                }
            }
            shares[mask] = payout / total;
        }
        return shares[mask];
    }

    private void enumerate(Seat[] seats, int[] ranks, int hero, int next, long live, double weight, int lost, int tied) {
        if (next == seats.length) {
            record(weight, lost, tied);
            return;
        }
        Seat seat = seats[next];
        for (int i = 0; i < seat.pairs.length; i++) {
            long mask = seat.masks[i];
            if ((mask & live) == mask) {
                int pair = seat.pairs[i];
                int rank = ranks[pair];
                enumerate(seats, ranks, hero, next + 1, live ^ mask, weight * seat.weight(pair),
                        lost | (rank > hero ? seat.bit : 0), tied | (rank == hero ? seat.bit : 0));
            }
        }
    }

    private void sample(BotEvaluator evaluator, int heroPair, int[] board, int[] riverRanks,
            Seat[] seats, long deck, int draws) {
        // Keep the numerical estimate deterministic and independent of the bot's action RNG.
        var rng = new SplittableRandom(0x5B13A572760CC9L);
        int[] dealt = new int[2 * seats.length];
        int[] pairs = new int[seats.length];
        int[] hand = Arrays.copyOf(board, 7);
        for (int draw = 0; draw < draws; draw++) {
            long live = deck;
            double weight = 1;
            for (int i = 0; i < seats.length; i++) {
                Seat seat = seats[i];
                int pair;
                if (seat.weights == null) {
                    int a = randomCard(live, rng);
                    int b = randomCard(live ^ (1L << a), rng);
                    pair = pair(a, b);
                } else {
                    double remaining = seat.remainingMass(dealt, i * 2);
                    double quantile = i == 0 ? (draw + rng.nextDouble()) / draws : rng.nextDouble();
                    pair = remaining > 0 ? seat.draw(live, remaining, quantile, rng) : -1;
                    if (pair < 0) {
                        weight = 0;
                        break;
                    }
                    // Undo the conditional normalizers of the sequential proposal.
                    weight *= remaining / seat.total;
                }
                pairs[i] = pair;
                dealt[i * 2] = pair / CARDS;
                dealt[i * 2 + 1] = pair % CARDS;
                live ^= cardMask(pair);
            }
            if (weight == 0) {
                continue;
            }
            for (int slot = board.length; slot < 5; slot++) {
                hand[slot] = randomCard(live, rng);
                live ^= 1L << hand[slot];
            }
            int hero = rank(evaluator, riverRanks, hand, heroPair);
            int lost = 0;
            int tied = 0;
            for (int i = 0; i < seats.length; i++) {
                int rank = rank(evaluator, riverRanks, hand, pairs[i]);
                if (rank > hero) {
                    lost |= seats[i].bit;
                } else if (rank == hero) {
                    tied |= seats[i].bit;
                }
            }
            record(weight, lost, tied);
        }
    }

    private void record(double weight, int lost, int tied) {
        mass[deals] = weight;
        losses[deals] = lost;
        ties[deals++] = tied;
        total += weight;
        squaredMass += weight * weight;
    }

    private static int rank(BotEvaluator evaluator, int[] riverRanks, int[] hand, int pair) {
        if (riverRanks != null) {
            return riverRanks[pair];
        }
        hand[5] = pair / CARDS;
        hand[6] = pair % CARDS;
        return evaluator.rankHand(hand);
    }

    private static int randomCard(long live, SplittableRandom rng) {
        int index = rng.nextInt(Long.bitCount(live));
        for (int i = 0; i < index; i++) {
            live &= live - 1;
        }
        return Long.numberOfTrailingZeros(live);
    }

    private static int pair(int a, int b) {
        return Math.min(a, b) * CARDS + Math.max(a, b);
    }

    private static long cardMask(int pair) {
        return (1L << (pair / CARDS)) | (1L << (pair % CARDS));
    }

    private static final class Seat {
        private final int bit;
        private final int[] pairs;
        private final long[] masks;
        private final double[] weights;
        private final double[] cdf;
        private final double[] byCard = new double[CARDS];
        private final double total;
        private final double squares;

        Seat(int seat, int[] legalPairs, double[] input) {
            bit = 1 << seat;
            pairs = input == null ? legalPairs : Arrays.stream(legalPairs).filter(p -> input[p] > 0).toArray();
            masks = new long[pairs.length];
            weights = input;
            cdf = input == null ? null : new double[pairs.length];
            double sum = 0;
            double squared = 0;
            for (int i = 0; i < pairs.length; i++) {
                int pair = pairs[i];
                masks[i] = cardMask(pair);
                double weight = weight(pair);
                sum += weight;
                squared += weight * weight;
                byCard[pair / CARDS] += weight;
                byCard[pair % CARDS] += weight;
                if (cdf != null) cdf[i] = sum;
            }
            total = sum;
            squares = squared;
        }

        private double weight(int pair) {
            return weights == null ? 1 : weights[pair];
        }

        private double effectiveHoldings() {
            return squares == 0 ? 0 : total * total / squares;
        }

        private double remainingMass(int[] dealt, int count) {
            double remaining = total;
            for (int i = 0; i < count; i++) {
                remaining -= byCard[dealt[i]];
                for (int j = 0; j < i; j++) {
                    remaining += weights[pair(dealt[i], dealt[j])];
                }
            }
            return Math.min(total, remaining);
        }

        private int draw(long live, double remaining, double quantile, SplittableRandom rng) {
            for (int retry = 0; retry < 16; retry++) {
                int index = Arrays.binarySearch(cdf, quantile * total);
                index = index < 0 ? -index - 1 : index;
                if ((masks[index] & live) == masks[index]) return pairs[index];
                quantile = rng.nextDouble();
            }
            // A bounded retry followed by an exact conditional draw keeps sparse ranges usable.
            double left = rng.nextDouble(remaining);
            int last = -1;
            for (int i = 0; i < pairs.length; i++) {
                if ((masks[i] & live) == masks[i]) {
                    last = pairs[i];
                    left -= weights[last];
                    if (left <= 0) return last;
                }
            }
            return last;
        }
    }
}
