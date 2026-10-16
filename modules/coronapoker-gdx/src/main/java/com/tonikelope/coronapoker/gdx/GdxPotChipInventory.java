/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.MoneyMath;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Presentation-only inventory of the physical-looking chips currently in the
 * table pot. Poker accounting remains exclusively in the canonical model.
 */
final class GdxPotChipInventory {

    private final ArrayList<CoronaPokerGdxTable.VisualChipSpec> chips
            = new ArrayList<>();

    void clear() {
        chips.clear();
    }

    void deposit(List<CoronaPokerGdxTable.VisualChipSpec> incoming) {
        if (incoming != null) chips.addAll(incoming);
    }

    void replace(double exactAmount,
            List<CoronaPokerGdxTable.VisualChipSpec> replacement) {
        chips.clear();
        chips.addAll(rebalance(replacement, cents(exactAmount)));
    }

    /**
     * Keeps the existing chip identities while matching an authoritative pot
     * value, for example the odd-chip remainder carried into a new hand.
     */
    void reconcile(double exactAmount,
            List<CoronaPokerGdxTable.VisualChipSpec> fallback) {
        long target = cents(exactAmount);
        if (target <= 0L) {
            chips.clear();
        } else if (chips.isEmpty()) {
            chips.addAll(rebalance(fallback, target));
        } else if (cents(amount()) != target) {
            List<CoronaPokerGdxTable.VisualChipSpec> adjusted =
                    rebalance(chips, target);
            chips.clear();
            chips.addAll(adjusted);
        }
    }

    /**
     * Removes a deterministic, shuffled share of the stored physical chips.
     * Colours and total chip count are conserved across successive payouts;
     * represented amounts are rebalanced only to keep counter interpolation
     * exact when a pot is split between boards or several winners.
     */
    List<CoronaPokerGdxTable.VisualChipSpec> withdraw(double exactAmount,
            long seed) {
        long available = cents(amount());
        long requested = Math.min(available, cents(exactAmount));
        if (requested <= 0L || chips.isEmpty()) return List.of();

        ArrayList<CoronaPokerGdxTable.VisualChipSpec> shuffled =
                new ArrayList<>(chips);
        shuffle(shuffled, seed ^ 0xa54ff53a5f1d36f1L);
        int selectedCount;
        if (requested >= available) {
            selectedCount = shuffled.size();
        } else {
            selectedCount = Math.round(shuffled.size()
                    * requested / (float) available);
            selectedCount = Math.max(1,
                    Math.min(shuffled.size() - 1, selectedCount));
        }

        ArrayList<CoronaPokerGdxTable.VisualChipSpec> selected =
                new ArrayList<>(shuffled.subList(0, selectedCount));
        ArrayList<CoronaPokerGdxTable.VisualChipSpec> remaining =
                new ArrayList<>(shuffled.subList(selectedCount,
                        shuffled.size()));
        chips.clear();
        chips.addAll(rebalance(remaining, available - requested));
        List<CoronaPokerGdxTable.VisualChipSpec> result =
                rebalance(selected, requested);
        ArrayList<CoronaPokerGdxTable.VisualChipSpec> randomized =
                new ArrayList<>(result);
        shuffle(randomized, seed ^ 0x510e527fade682d1L);
        return List.copyOf(randomized);
    }

    int size() {
        return chips.size();
    }

    double amount() {
        long total = 0L;
        for (CoronaPokerGdxTable.VisualChipSpec chip : chips) {
            total += cents(chip.amount());
        }
        return total / 100d;
    }

    List<CoronaPokerGdxTable.VisualChipSpec> snapshot() {
        return List.copyOf(chips);
    }

    static List<CoronaPokerGdxTable.VisualChipSpec> rebalance(
            List<CoronaPokerGdxTable.VisualChipSpec> source,
            double exactAmount) {
        return rebalance(source, cents(exactAmount));
    }

    private static List<CoronaPokerGdxTable.VisualChipSpec> rebalance(
            List<CoronaPokerGdxTable.VisualChipSpec> source,
            long targetMinor) {
        if (source == null || source.isEmpty() || targetMinor <= 0L) {
            return List.of();
        }
        long originalTotal = 0L;
        for (CoronaPokerGdxTable.VisualChipSpec chip : source) {
            originalTotal += cents(chip.amount());
        }
        ArrayList<CoronaPokerGdxTable.VisualChipSpec> result =
                new ArrayList<>(source.size());
        long assigned = 0L;
        long cumulativeOriginal = 0L;
        for (int index = 0; index < source.size(); index++) {
            CoronaPokerGdxTable.VisualChipSpec chip = source.get(index);
            long cumulativeTarget;
            if (index == source.size() - 1) {
                cumulativeTarget = targetMinor;
            } else if (originalTotal > 0L) {
                cumulativeOriginal += cents(chip.amount());
                cumulativeTarget = Math.round(cumulativeOriginal
                        * targetMinor / (double) originalTotal);
            } else {
                cumulativeTarget = (index + 1L) * targetMinor
                        / source.size();
            }
            long represented = Math.max(0L, cumulativeTarget - assigned);
            result.add(new CoronaPokerGdxTable.VisualChipSpec(
                    represented / 100d, chip.color()));
            assigned += represented;
        }
        return List.copyOf(result);
    }

    static <T> void shuffle(List<T> values, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        for (int index = values.size() - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1);
            T value = values.get(index);
            values.set(index, values.get(swap));
            values.set(swap, value);
        }
    }

    private static long cents(double amount) {
        if (!Double.isFinite(amount) || amount <= 0d) return 0L;
        return Math.max(0L, Math.round(MoneyMath.clean(amount) * 100d));
    }
}
