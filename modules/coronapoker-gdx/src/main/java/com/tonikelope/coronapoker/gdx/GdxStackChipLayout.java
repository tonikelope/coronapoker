package com.tonikelope.coronapoker.gdx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the compact chip composition painted beside a player's stack.
 *
 * <p>The numeric stack remains authoritative. This class only translates that
 * amount into a casino-like, exact visual composition. It starts from the
 * smallest useful representation and splits high denominations while there is
 * physical room, so small chips visibly accumulate and are automatically
 * coloured up before either of the two rendered columns can overflow.</p>
 */
final class GdxStackChipLayout {

    static final int MAX_VISIBLE_CHIPS = 10;
    static final int MAX_CHIPS_PER_COLUMN = MAX_VISIBLE_CHIPS / 2;

    private static final int RED = 0;
    private static final int BLUE = 1;
    private static final int GREEN = 2;
    private static final int GOLD = 3;

    private GdxStackChipLayout() {
    }

    static Layout compose(double amount, double smallBlind) {
        if (!Double.isFinite(amount) || amount <= 0d) {
            return Layout.EMPTY;
        }

        long totalMinor = Math.max(1L, Math.round(amount * 100d));
        long baseMinor = Double.isFinite(smallBlind) && smallBlind > 0d
                ? Math.max(1L, Math.round(smallBlind * 100d)) : 1L;
        List<Denomination> denominations = denominations(totalMinor,
                baseMinor);
        ArrayList<Chip> chips = greedyComposition(totalMinor, denominations);

        if (chips.size() > MAX_VISIBLE_CHIPS) {
            chips = condenseOverflow(chips);
        }
        enrichWithinCapacity(chips, denominations);
        chips.sort(Comparator.comparingLong(Chip::amountMinor).reversed());
        return new Layout(chips);
    }

    private static List<Denomination> denominations(long totalMinor,
            long baseMinor) {
        ArrayList<Denomination> result = new ArrayList<>();
        long scale = baseMinor;
        while (scale > 0L && scale <= totalMinor) {
            addDenomination(result, scale, BLUE);
            addDenomination(result, safeMultiply(scale, 2L), GOLD);
            addDenomination(result, safeMultiply(scale, 5L), RED);
            addDenomination(result, safeMultiply(scale, 10L), GREEN);
            if (scale > Long.MAX_VALUE / 10L) break;
            scale *= 10L;
        }
        result.removeIf(denomination -> denomination.amountMinor() <= 0L
                || denomination.amountMinor() > totalMinor);
        result.sort(Comparator.comparingLong(Denomination::amountMinor)
                .reversed());
        return List.copyOf(result);
    }

    private static long safeMultiply(long value, long multiplier) {
        return value > Long.MAX_VALUE / multiplier
                ? Long.MAX_VALUE : value * multiplier;
    }

    private static void addDenomination(List<Denomination> denominations,
            long amountMinor, int color) {
        if (amountMinor <= 0L || denominations.stream().anyMatch(
                denomination -> denomination.amountMinor() == amountMinor)) {
            return;
        }
        denominations.add(new Denomination(amountMinor, color));
    }

    private static ArrayList<Chip> greedyComposition(long totalMinor,
            List<Denomination> denominations) {
        long remaining = totalMinor;
        ArrayList<Chip> result = new ArrayList<>();
        for (Denomination denomination : denominations) {
            long count = remaining / denomination.amountMinor();
            for (long index = 0L; index < count; index++) {
                result.add(new Chip(denomination.amountMinor(),
                        denomination.color()));
            }
            remaining -= count * denomination.amountMinor();
        }
        // Odd-cent remainders are legitimate (for example after split pots)
        // even when they are below the original small blind. One blue bank
        // chip keeps the visual sum exact without inventing game money.
        if (remaining > 0L) {
            result.add(new Chip(remaining, BLUE));
        }
        return result;
    }

    private static ArrayList<Chip> condenseOverflow(List<Chip> chips) {
        ArrayList<Chip> ordered = new ArrayList<>(chips);
        ordered.sort(Comparator.comparingLong(Chip::amountMinor).reversed());
        ArrayList<Chip> result = new ArrayList<>(MAX_VISIBLE_CHIPS);
        for (int index = 0; index < MAX_VISIBLE_CHIPS - 1; index++) {
            result.add(ordered.get(index));
        }
        long banked = 0L;
        for (int index = MAX_VISIBLE_CHIPS - 1;
                index < ordered.size(); index++) {
            banked += ordered.get(index).amountMinor();
        }
        result.add(new Chip(banked, GREEN));
        return result;
    }

    private static void enrichWithinCapacity(ArrayList<Chip> chips,
            List<Denomination> denominations) {
        while (chips.size() < MAX_VISIBLE_CHIPS) {
            Split best = null;
            for (int chipIndex = 0; chipIndex < chips.size(); chipIndex++) {
                List<Chip> replacement = split(chips.get(chipIndex),
                        denominations);
                if (replacement.isEmpty()) continue;
                int added = replacement.size() - 1;
                if (chips.size() + added > MAX_VISIBLE_CHIPS) continue;
                Split candidate = new Split(chipIndex, replacement,
                        chips.get(chipIndex).amountMinor());
                if (best == null || candidate.originalAmountMinor()
                        > best.originalAmountMinor()) {
                    best = candidate;
                }
            }
            if (best == null) return;
            chips.remove(best.index());
            chips.addAll(best.replacement());
        }
    }

    private static List<Chip> split(Chip chip,
            List<Denomination> denominations) {
        long remaining = chip.amountMinor();
        ArrayList<Chip> replacement = new ArrayList<>();
        for (Denomination denomination : denominations) {
            if (denomination.amountMinor() >= chip.amountMinor()) continue;
            long count = remaining / denomination.amountMinor();
            for (long index = 0L; index < count; index++) {
                replacement.add(new Chip(denomination.amountMinor(),
                        denomination.color()));
            }
            remaining -= count * denomination.amountMinor();
        }
        return remaining == 0L && replacement.size() > 1
                ? List.copyOf(replacement) : List.of();
    }

    record Chip(long amountMinor, int color) {
    }

    record Layout(List<Chip> chips) {

        private static final Layout EMPTY = new Layout(List.of());

        Layout {
            chips = List.copyOf(chips);
        }

        long representedMinor() {
            return chips.stream().mapToLong(Chip::amountMinor).sum();
        }

        int columnHeight(int column) {
            if (column < 0 || column > 1) return 0;
            return (chips.size() + (column == 0 ? 1 : 0)) / 2;
        }
    }

    private record Denomination(long amountMinor, int color) {
    }

    private record Split(int index, List<Chip> replacement,
            long originalAmountMinor) {
    }
}
