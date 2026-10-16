package com.tonikelope.coronapoker.gdx;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the four compact denomination columns painted beside a player stack.
 * The numeric stack remains the sole accounting authority; this class only
 * creates a deterministic, exact visual rack from that amount.
 */
final class GdxStackChipLayout {

    static final int COLUMN_COUNT = GdxChipDenominations.COLUMN_COUNT;
    static final int MAX_CHIPS_PER_COLUMN = 6;

    private static final int MAX_RACK_UNITS = MAX_CHIPS_PER_COLUMN
            * (1 + 2 + 5 + 10);

    private GdxStackChipLayout() {
    }

    static Layout compose(double amount, double smallBlind) {
        return compose(amount, smallBlind, amount);
    }

    static Layout compose(double amount, double smallBlind,
            double referenceAmount) {
        if (!Double.isFinite(amount) || amount <= 0d) {
            return Layout.EMPTY;
        }

        long totalMinor = Math.max(1L, Math.round(amount * 100d));
        long referenceMinor = Double.isFinite(referenceAmount)
                && referenceAmount > 0d
                        ? Math.max(1L, Math.round(referenceAmount * 100d))
                        : totalMinor;
        long baseMinor = GdxChipDenominations.baseMinor(smallBlind);
        long rackUnitMinor = rackUnitMinor(Math.max(totalMinor,
                referenceMinor), baseMinor);
        int representedUnits = (int) Math.min(MAX_RACK_UNITS,
                totalMinor / rackUnitMinor);
        int referenceUnits = (int) Math.min(MAX_RACK_UNITS,
                referenceMinor / rackUnitMinor);
        long remainderMinor = totalMinor
                - representedUnits * rackUnitMinor;
        int[] referenceCounts = balancedCounts(referenceUnits, null);
        int[] counts = balancedCounts(representedUnits, referenceCounts);

        ArrayList<Column> columns = new ArrayList<>(COLUMN_COUNT);
        for (int column = 0; column < COLUMN_COUNT; column++) {
            ArrayList<Chip> chips = new ArrayList<>(counts[column]);
            long denominationMinor = rackUnitMinor
                    * GdxChipDenominations.units(column);
            for (int index = 0; index < counts[column]; index++) {
                chips.add(new Chip(denominationMinor,
                        GdxChipDenominations.color(column)));
            }
            columns.add(new Column(GdxChipDenominations.color(column), chips));
        }

        // Split pots can leave a remainder below the current colour-up unit.
        // Fold it into one existing physical chip so the columns stay bounded
        // while their represented sum remains cent-exact.
        if (remainderMinor > 0L) {
            int remainderColumn = firstOccupiedColumn(columns);
            if (remainderColumn < 0) {
                int color = GdxChipDenominations.color(0);
                columns.set(0, new Column(color,
                        List.of(new Chip(remainderMinor, color))));
            } else {
                Column column = columns.get(remainderColumn);
                ArrayList<Chip> adjusted = new ArrayList<>(column.chips());
                Chip top = adjusted.get(adjusted.size() - 1);
                adjusted.set(adjusted.size() - 1, new Chip(
                        top.amountMinor() + remainderMinor, top.color()));
                columns.set(remainderColumn,
                        new Column(column.color(), adjusted));
            }
        }
        return new Layout(columns);
    }

    /** Selects the first casino-like 1/2/5 scale whose rack can hold it. */
    private static long rackUnitMinor(long totalMinor, long baseMinor) {
        long decade = 1L;
        while (true) {
            for (long factor : new long[]{1L, 2L, 5L}) {
                long multiplier = safeMultiply(decade, factor);
                long candidate = safeMultiply(baseMinor, multiplier);
                if (candidate == Long.MAX_VALUE
                        || totalMinor / candidate <= MAX_RACK_UNITS) {
                    return candidate;
                }
            }
            if (decade > Long.MAX_VALUE / 10L) return Long.MAX_VALUE;
            decade *= 10L;
        }
    }

    private static long safeMultiply(long value, long multiplier) {
        return value > Long.MAX_VALUE / multiplier
                ? Long.MAX_VALUE : value * multiplier;
    }

    /**
     * Searches the tiny 7^4 rack space. The winning exact composition is the
     * one whose four heights are closest to a balanced rack fill. This gives
     * equal stacks identical chips and produces natural colour-up boundaries:
     * columns grow locally, then exchange several low chips for a higher one.
     */
    private static int[] balancedCounts(int representedUnits,
            int[] referenceCounts) {
        int[] best = new int[COLUMN_COUNT];
        double bestScore = Double.POSITIVE_INFINITY;
        int bestCount = -1;
        double targetHeight = MAX_CHIPS_PER_COLUMN
                * representedUnits / (double) MAX_RACK_UNITS;
        for (int blue = 0; blue <= MAX_CHIPS_PER_COLUMN; blue++) {
            for (int gold = 0; gold <= MAX_CHIPS_PER_COLUMN; gold++) {
                for (int red = 0; red <= MAX_CHIPS_PER_COLUMN; red++) {
                    for (int green = 0; green <= MAX_CHIPS_PER_COLUMN;
                            green++) {
                        int units = blue * GdxChipDenominations.units(0)
                                + gold * GdxChipDenominations.units(1)
                                + red * GdxChipDenominations.units(2)
                                + green * GdxChipDenominations.units(3);
                        if (units != representedUnits) continue;
                        int chipCount = blue + gold + red + green;
                        double balanceScore = squaredDistance(blue,
                                targetHeight)
                                + squaredDistance(gold, targetHeight)
                                + squaredDistance(red, targetHeight)
                                + squaredDistance(green, targetHeight);
                        double referenceScore = referenceCounts == null ? 0d
                                : squaredDistance(blue, referenceCounts[0])
                                + squaredDistance(gold, referenceCounts[1])
                                + squaredDistance(red, referenceCounts[2])
                                + squaredDistance(green, referenceCounts[3]);
                        double score = referenceScore * 100d + balanceScore;
                        if (score < bestScore - 0.000001d
                                || Math.abs(score - bestScore) < 0.000001d
                                && chipCount > bestCount) {
                            bestScore = score;
                            bestCount = chipCount;
                            best = new int[]{blue, gold, red, green};
                        }
                    }
                }
            }
        }
        return best;
    }

    private static double squaredDistance(int count, double target) {
        double distance = count - target;
        return distance * distance;
    }

    private static int firstOccupiedColumn(List<Column> columns) {
        for (int index = 0; index < columns.size(); index++) {
            if (!columns.get(index).chips().isEmpty()) return index;
        }
        return -1;
    }

    record Chip(long amountMinor, int color) {
    }

    record Column(int color, List<Chip> chips) {

        Column {
            chips = List.copyOf(chips);
        }
    }

    record Layout(List<Column> columns) {

        private static final Layout EMPTY = new Layout(List.of(
                new Column(GdxChipDenominations.color(0), List.of()),
                new Column(GdxChipDenominations.color(1), List.of()),
                new Column(GdxChipDenominations.color(2), List.of()),
                new Column(GdxChipDenominations.color(3), List.of())));

        Layout {
            columns = List.copyOf(columns);
            if (columns.size() != COLUMN_COUNT) {
                throw new IllegalArgumentException(
                        "A stack rack requires four denomination columns");
            }
        }

        long representedMinor() {
            return columns.stream().flatMap(column -> column.chips().stream())
                    .mapToLong(Chip::amountMinor).sum();
        }

        int columnHeight(int column) {
            return columns.get(column).chips().size();
        }

        int chipCount() {
            return columns.stream().mapToInt(
                    column -> column.chips().size()).sum();
        }
    }
}
