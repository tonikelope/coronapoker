/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.util.List;

/** Chip EV for the bot's current-street action, using the table's side-pot rules. */
final class BotPotOdds {

    private BotPotOdds() {}

    record Seat(long committed, long addedIfCalled, double foldProbability) {}

    /**
     * Expected payout minus new chips committed by the hero. The full proposed
     * wager is debited here, and its uncalled part is returned by the payout.
     */
    static double ev(long heroCommitted, long cost, long pot, List<Seat> seats, double[] equity) {
        long heroFinal = heroCommitted + cost;
        long alreadyCommitted = heroCommitted;
        int optionalCallers = 0;
        boolean sameDepth = true;
        for (Seat seat : seats) {
            alreadyCommitted += seat.committed();
            if (seat.foldProbability() < 1) {
                sameDepth &= seat.committed() + seat.addedIfCalled() == heroFinal;
                if (seat.foldProbability() > 0) {
                    optionalCallers++;
                }
            }
        }
        long carry = Math.max(0, pot - alreadyCommitted);
        double payout = sameDepth && optionalCallers >= 5
                ? singlePotPayout(alreadyCommitted + cost + carry, seats, equity)
                : payouts(0, heroFinal, carry, seats, new long[seats.size()], 0, equity);
        return (payout - cost) / PotMath.CENTS_PER_UNIT;
    }

    private static double singlePotPayout(long base, List<Seat> seats, double[] equity) {
        // With equal caps the identity of the callers only changes the added
        // chips. Track their probability and weighted additions by count.
        double[] probability = new double[equity.length];
        double[] additions = new double[equity.length];
        probability[0] = 1;
        int maxCallers = 0;
        for (Seat seat : seats) {
            double fold = seat.foldProbability();
            if (fold == 1) {
                continue;
            }
            for (int k = ++maxCallers; k > 0; k--) {
                additions[k] = fold * additions[k] + (1 - fold)
                        * (additions[k - 1] + probability[k - 1] * seat.addedIfCalled());
                probability[k] = fold * probability[k] + (1 - fold) * probability[k - 1];
            }
            probability[0] *= fold;
        }
        double payout = 0;
        for (int k = 0; k <= maxCallers; k++) {
            payout += equity[k] * (probability[k] * base + additions[k]);
        }
        return payout;
    }

    // There are at most nine other seats. Forced folds and all-ins have only
    // one branch, so an ordinary heads-up bet has just two outcomes.
    private static double payouts(int index, long heroFinal, long carry, List<Seat> seats,
            long[] contributions, int live, double[] equity) {
        if (index == seats.size()) {
            return payout(heroFinal, carry, contributions, live, equity);
        }
        Seat seat = seats.get(index);
        double fold = seat.foldProbability();
        double result = 0;
        if (fold > 0) {
            contributions[index] = seat.committed();
            result = fold * payouts(index + 1, heroFinal, carry, seats, contributions, live, equity);
        }
        if (fold < 1) {
            contributions[index] = seat.committed() + seat.addedIfCalled();
            result += (1 - fold) * payouts(index + 1, heroFinal, carry, seats, contributions,
                    live | (1 << index), equity);
        }
        return result;
    }

    private static double payout(long heroFinal, long carry, long[] contributions, int live,
            double[] equity) {
        double result = carry * equity[Integer.bitCount(live)];
        long deepest = heroFinal;
        for (int i = 0; i < contributions.length; i++) {
            if ((live & (1 << i)) != 0) {
                deepest = Math.max(deepest, contributions[i]);
            }
        }
        if (deepest == 0) {
            // With no live commitments, all dead chips belong to the main pot.
            for (long chips : contributions) {
                result += chips * equity[Integer.bitCount(live)];
            }
            return result;
        }
        // Match CoreGamePot: cut at live contribution levels only. Any dead
        // money above the last cut goes to the deepest pot, never to a layer
        // the hero cannot win. A lone eligible hero gets an uncalled bet back.
        for (long lower = 0; lower < heroFinal;) {
            long upper = heroFinal;
            int opponents = 0;
            for (int i = 0; i < contributions.length; i++) {
                if ((live & (1 << i)) != 0 && contributions[i] > lower) {
                    upper = Math.min(upper, contributions[i]);
                    opponents++;
                }
            }
            long layer = upper - lower;
            for (int i = 0; i < contributions.length; i++) {
                long capped = upper == deepest ? contributions[i] : Math.min(upper, contributions[i]);
                layer += Math.max(0, capped - lower);
            }
            result += equity[opponents] * layer;
            lower = upper;
        }
        return result;
    }
}
