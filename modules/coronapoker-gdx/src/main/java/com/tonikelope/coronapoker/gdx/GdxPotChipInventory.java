/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.game.MoneyMath;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Presentation-only inventory of the physical-looking chips currently in the
 * table pot. Poker accounting remains exclusively in the canonical model.
 */
final class GdxPotChipInventory {

    private final ArrayList<CoronaPokerGdxTable.VisualChipSpec> chips
            = new ArrayList<>();
    private List<CoronaPokerGdxTable.VisualChipSpec> snapshot = List.of();
    private boolean snapshotDirty;

    void clear() {
        if (chips.isEmpty()) return;
        chips.clear();
        snapshotDirty = true;
    }

    void deposit(List<CoronaPokerGdxTable.VisualChipSpec> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        chips.addAll(incoming);
        snapshotDirty = true;
    }

    /**
     * Performs a dealer colour-up only when it actually relieves a compressed
     * column. Otherwise every landed chip keeps its physical denomination.
     */
    void relieveCompressedColumns(double smallBlind,
            int[] relaxedCapacities) {
        double exactAmount = amount();
        GdxStackChipLayout.Layout current = CoronaPokerGdxTable
                .visualChipLayout(chips);
        GdxStackChipLayout.Layout layout = GdxStackChipLayout
                .relieveCompression(current, exactAmount, smallBlind,
                        exactAmount, relaxedCapacities);
        if (layout == current) return;
        chips.clear();
        for (GdxStackChipLayout.Column column : layout.columns()) {
            for (GdxStackChipLayout.Chip chip : column.chips()) {
                chips.add(new CoronaPokerGdxTable.VisualChipSpec(
                        chip.amountMinor() / 100d, chip.color()));
            }
        }
        snapshotDirty = true;
    }

    static List<CoronaPokerGdxTable.VisualChipSpec> relieveCompressedColumns(
            List<CoronaPokerGdxTable.VisualChipSpec> source,
            double smallBlind, int[] relaxedCapacities) {
        GdxPotChipInventory inventory = new GdxPotChipInventory();
        inventory.deposit(source);
        inventory.relieveCompressedColumns(smallBlind, relaxedCapacities);
        return inventory.snapshot();
    }

    static RunItTwiceSplit splitForRunItTwice(
            List<CoronaPokerGdxTable.VisualChipSpec> source,
            double exactBoardAmount, double smallBlind,
            int[] relaxedCapacities, long seed) {
        GdxPotChipInventory inventory = new GdxPotChipInventory();
        inventory.deposit(source);
        // Remove the two halves from the real physical inventory first so an
        // odd remainder is retained exactly. The dealer then colours up both
        // equal board piles to the same deterministic rack: SIDE-B must never
        // inherit a random-looking residue merely because SIDE-A was paid
        // first.
        List<CoronaPokerGdxTable.VisualChipSpec> rawSideA =
                inventory.withdraw(exactBoardAmount, smallBlind,
                        seed ^ 0x243f6a8885a308d3L);
        List<CoronaPokerGdxTable.VisualChipSpec> rawSideB =
                inventory.withdraw(exactBoardAmount, smallBlind,
                        seed ^ 0x13198a2e03707344L);
        List<CoronaPokerGdxTable.VisualChipSpec> sideA =
                exactBoardRack(rawSideA, exactBoardAmount, smallBlind,
                        relaxedCapacities);
        List<CoronaPokerGdxTable.VisualChipSpec> sideB =
                exactBoardRack(rawSideB, exactBoardAmount, smallBlind,
                        relaxedCapacities);
        return new RunItTwiceSplit(
                sideA, sideB,
                inventory.snapshot());
    }

    private static List<CoronaPokerGdxTable.VisualChipSpec> exactBoardRack(
            List<CoronaPokerGdxTable.VisualChipSpec> withdrawn,
            double exactBoardAmount, double smallBlind,
            int[] relaxedCapacities) {
        if (cents(amount(withdrawn)) != cents(exactBoardAmount)) {
            return withdrawn;
        }
        GdxStackChipLayout.Layout layout = GdxStackChipLayout.compose(
                exactBoardAmount, smallBlind, exactBoardAmount);
        ArrayList<CoronaPokerGdxTable.VisualChipSpec> result =
                new ArrayList<>(layout.chipCount());
        for (GdxStackChipLayout.Column column : layout.columns()) {
            for (GdxStackChipLayout.Chip chip : column.chips()) {
                result.add(new CoronaPokerGdxTable.VisualChipSpec(
                        chip.amountMinor() / 100d, chip.color()));
            }
        }
        return relieveCompressedColumns(result, smallBlind,
                relaxedCapacities);
    }

    private static double amount(
            List<CoronaPokerGdxTable.VisualChipSpec> values) {
        long total = 0L;
        for (CoronaPokerGdxTable.VisualChipSpec chip : values) {
            total += cents(chip.amount());
        }
        return total / 100d;
    }

    void replace(double exactAmount,
            List<CoronaPokerGdxTable.VisualChipSpec> replacement) {
        chips.clear();
        chips.addAll(rebalance(replacement, cents(exactAmount)));
        snapshotDirty = true;
    }

    /**
     * Keeps the existing chip identities while matching an authoritative pot
     * value, for example the odd-chip remainder carried into a new hand.
     */
    void reconcile(double exactAmount,
            List<CoronaPokerGdxTable.VisualChipSpec> fallback) {
        long target = cents(exactAmount);
        if (target <= 0L) {
            clear();
        } else if (chips.isEmpty()) {
            chips.addAll(rebalance(fallback, target));
            snapshotDirty = true;
        } else if (cents(amount()) != target) {
            chips.clear();
            chips.addAll(rebalance(fallback, target));
            snapshotDirty = true;
        }
    }

    /**
     * Removes an exact physical payment. Existing chips are preserved whenever
     * they can make the amount exactly. Otherwise only the smallest overpaying
     * subset is changed into canonical lower denominations before anything
     * flies, exactly as a dealer would break a large chip for a split pot.
     */
    List<CoronaPokerGdxTable.VisualChipSpec> withdraw(double exactAmount,
            double smallBlind, long seed) {
        long available = cents(amount());
        long baseMinor = GdxChipDenominations.baseMinor(smallBlind);
        long rawRequested = Math.min(available, cents(exactAmount));
        // An odd remainder smaller than the x1 chip stays in the pot, matching
        // real-table odd-chip handling instead of breaking the smallest chip.
        long requested = rawRequested - rawRequested % baseMinor;
        if (requested <= 0L || chips.isEmpty()) return List.of();

        while (true) {
            Map<Long, Pick> best = bestPicks();
            Pick selection = best.get(requested);
            if (selection != null) {
                ArrayList<CoronaPokerGdxTable.VisualChipSpec> selected =
                        removeSelection(selection);
                shuffle(selected, seed ^ 0x510e527fade682d1L);
                return List.copyOf(selected);
            }

            Map.Entry<Long, Pick> overpay = best.entrySet().stream()
                    .filter(entry -> entry.getKey() > requested)
                    .min((left, right) -> {
                        int amountOrder = Long.compare(left.getKey(),
                                right.getKey());
                        return amountOrder != 0 ? amountOrder
                                : Integer.compare(left.getValue().count(),
                                        right.getValue().count());
                    }).orElse(null);
            if (overpay == null) return List.of();

            int chipToBreak = selectedIndices(overpay.getValue()).stream()
                    .filter(index -> !GdxChipDenominations.breakDown(
                            cents(chips.get(index).amount()), smallBlind)
                            .isEmpty())
                    .min(java.util.Comparator.comparingLong(index ->
                            cents(chips.get(index).amount())))
                    .orElse(-1);
            if (chipToBreak < 0) return List.of();

            CoronaPokerGdxTable.VisualChipSpec original =
                    chips.remove(chipToBreak);
            for (GdxChipDenominations.Chip replacement
                    : GdxChipDenominations.breakDown(
                            cents(original.amount()), smallBlind)) {
                chips.add(new CoronaPokerGdxTable.VisualChipSpec(
                        replacement.amountMinor() / 100d,
                        replacement.color()));
            }
            snapshotDirty = true;
        }
    }

    private Map<Long, Pick> bestPicks() {
        Map<Long, Pick> best = new LinkedHashMap<>();
        best.put(0L, Pick.EMPTY);
        for (int index = 0; index < chips.size(); index++) {
            long chipAmount = cents(chips.get(index).amount());
            Map<Long, Pick> next = new LinkedHashMap<>(best);
            for (Map.Entry<Long, Pick> entry : best.entrySet()) {
                long amount = entry.getKey() + chipAmount;
                Pick candidate = new Pick(entry.getValue().count() + 1,
                        entry.getValue(), index);
                Pick current = next.get(amount);
                if (current == null || candidate.count() < current.count()) {
                    next.put(amount, candidate);
                }
            }
            best = next;
        }
        return best;
    }

    private ArrayList<Integer> selectedIndices(Pick selection) {
        ArrayList<Integer> selectedIndices = new ArrayList<>(
                selection.count());
        for (Pick pick = selection; pick.index() >= 0;
                pick = pick.previous()) {
            selectedIndices.add(pick.index());
        }
        return selectedIndices;
    }

    private ArrayList<CoronaPokerGdxTable.VisualChipSpec> removeSelection(
            Pick selection) {
        ArrayList<Integer> selectedIndices = selectedIndices(selection);
        selectedIndices.sort(java.util.Comparator.reverseOrder());
        ArrayList<CoronaPokerGdxTable.VisualChipSpec> selected =
                new ArrayList<>(selectedIndices.size());
        for (int index : selectedIndices) selected.add(chips.remove(index));
        if (!selected.isEmpty()) snapshotDirty = true;
        return selected;
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
        if (snapshotDirty) {
            snapshot = List.copyOf(chips);
            snapshotDirty = false;
        }
        return snapshot;
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

    private record Pick(int count, Pick previous, int index) {

        private static final Pick EMPTY = new Pick(0, null, -1);
    }

    record RunItTwiceSplit(
            List<CoronaPokerGdxTable.VisualChipSpec> sideA,
            List<CoronaPokerGdxTable.VisualChipSpec> sideB,
            List<CoronaPokerGdxTable.VisualChipSpec> remainder) {

        RunItTwiceSplit {
            sideA = List.copyOf(sideA);
            sideB = List.copyOf(sideB);
            remainder = List.copyOf(remainder);
        }
    }
}
