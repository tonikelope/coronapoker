package com.tonikelope.coronapoker.gdx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the four compact denomination columns painted beside a player stack.
 * The numeric stack remains the sole accounting authority; this class only
 * creates a deterministic, exact visual rack from that amount.
 */
final class GdxStackChipLayout {

    static final int COLUMN_COUNT = GdxChipDenominations.COLUMN_COUNT;
    private static final int PROPORTION_SEARCH_RADIUS = 4;

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
        long representedUnits = totalMinor / baseMinor;
        long referenceUnits = Math.max(1L, referenceMinor / baseMinor);
        long remainderMinor = totalMinor - representedUnits * baseMinor;
        int[] referenceCounts = balancedReferenceCounts(referenceUnits);
        int[] counts = proportionalCounts(representedUnits, referenceUnits,
                referenceCounts);

        ArrayList<Column> columns = new ArrayList<>(COLUMN_COUNT);
        for (int column = 0; column < COLUMN_COUNT; column++) {
            ArrayList<Chip> chips = new ArrayList<>(counts[column]);
            long denominationMinor = baseMinor
                    * GdxChipDenominations.units(column);
            for (int index = 0; index < counts[column]; index++) {
                chips.add(new Chip(denominationMinor,
                        GdxChipDenominations.color(column)));
            }
            columns.add(new Column(GdxChipDenominations.color(column), chips));
        }

        // Split pots can leave a remainder below the current colour-up unit.
        // Fold it into one existing physical chip so the represented sum
        // remains cent-exact without inventing a fifth denomination.
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

    /** Selects the fewest exact chips that physically exist in this rack. */
    static List<Chip> payment(Layout layout, double exactAmount,
            double smallBlind) {
        return preparePayment(layout, exactAmount, smallBlind).chips();
    }

    /**
     * Prepares and selects an exact physical payment. If the rack cannot make
     * exact change, only the smallest necessary set of chips is exchanged;
     * the rack's represented value is unchanged until the selected chip flies.
     */
    static Payment preparePayment(Layout layout, double exactAmount,
            double smallBlind) {
        if (!Double.isFinite(exactAmount) || exactAmount <= 0d) {
            return new Payment(layout == null ? Layout.EMPTY : layout,
                    List.of());
        }
        if (layout == null) {
            List<Chip> fallback = GdxChipDenominations.minimum(exactAmount,
                    smallBlind).stream().map(chip -> new Chip(
                            chip.amountMinor(), chip.color())).toList();
            return new Payment(Layout.EMPTY, fallback);
        }
        long targetMinor = Math.max(1L, Math.round(exactAmount * 100d));
        Layout prepared = layout;
        while (true) {
            ArrayList<Chip> available = new ArrayList<>();
            for (int column = COLUMN_COUNT - 1; column >= 0; column--) {
                available.addAll(prepared.columns().get(column).chips());
            }
            Map<Long, Selection> best = bestSelections(available);
            Selection selection = best.get(targetMinor);
            if (selection != null) {
                return new Payment(prepared, selectedChips(selection));
            }
            Map.Entry<Long, Selection> overpay = best.entrySet().stream()
                    .filter(entry -> entry.getKey() > targetMinor)
                    .min((left, right) -> {
                        int amountOrder = Long.compare(left.getKey(),
                                right.getKey());
                        return amountOrder != 0 ? amountOrder
                                : Integer.compare(left.getValue().count(),
                                        right.getValue().count());
                    }).orElse(null);
            if (overpay == null) return fallbackPayment(prepared,
                    exactAmount, smallBlind);

            Chip toBreak = selectedChips(overpay.getValue()).stream()
                    .filter(chip -> !GdxChipDenominations.breakDown(
                            chip.amountMinor(), smallBlind).isEmpty())
                    .min(java.util.Comparator.comparingLong(
                            Chip::amountMinor)).orElse(null);
            if (toBreak == null) return fallbackPayment(prepared,
                    exactAmount, smallBlind);

            List<GdxChipDenominations.Chip> change =
                    GdxChipDenominations.breakDown(
                            toBreak.amountMinor(), smallBlind);
            prepared = remove(prepared, toBreak.amountMinor(),
                    toBreak.color());
            for (GdxChipDenominations.Chip chip : change) {
                prepared = add(prepared, chip.amountMinor(), chip.color());
            }
        }
    }

    private static Map<Long, Selection> bestSelections(List<Chip> available) {
        Map<Long, Selection> best = new LinkedHashMap<>();
        best.put(0L, Selection.EMPTY);
        for (Chip chip : available) {
            Map<Long, Selection> next = new LinkedHashMap<>(best);
            for (Map.Entry<Long, Selection> entry : best.entrySet()) {
                long amount = entry.getKey() + chip.amountMinor();
                Selection candidate = new Selection(
                        entry.getValue().count() + 1,
                        entry.getValue(), chip);
                Selection current = next.get(amount);
                if (current == null || candidate.count() < current.count()) {
                    next.put(amount, candidate);
                }
            }
            best = next;
        }
        return best;
    }

    private static Payment fallbackPayment(Layout prepared,
            double exactAmount, double smallBlind) {
        List<Chip> fallback = GdxChipDenominations.minimum(
                exactAmount, smallBlind).stream()
                .map(chip -> new Chip(chip.amountMinor(),
                        chip.color())).toList();
        return new Payment(prepared, fallback);
    }

    private static List<Chip> selectedChips(Selection selection) {
        ArrayList<Chip> result = new ArrayList<>(selection.count());
        for (Selection cursor = selection; cursor.chip() != null;
                cursor = cursor.previous()) {
            result.add(cursor.chip());
        }
        return List.copyOf(result);
    }

    static Layout remove(Layout layout, long amountMinor, int color) {
        if (layout == null) return Layout.EMPTY;
        ArrayList<Column> columns = new ArrayList<>(layout.columns());
        for (int index = 0; index < columns.size(); index++) {
            Column column = columns.get(index);
            if (column.color() != color || column.chips().isEmpty()) continue;
            ArrayList<Chip> chips = new ArrayList<>(column.chips());
            int matching = -1;
            for (int chip = chips.size() - 1; chip >= 0; chip--) {
                if (chips.get(chip).amountMinor() == amountMinor) {
                    matching = chip;
                    break;
                }
            }
            if (matching < 0) continue;
            chips.remove(matching);
            columns.set(index, new Column(color, chips));
            return new Layout(columns);
        }
        return layout;
    }

    static Layout add(Layout layout, long amountMinor, int color) {
        ArrayList<Column> columns = new ArrayList<>(
                layout == null ? Layout.EMPTY.columns() : layout.columns());
        for (int index = 0; index < columns.size(); index++) {
            Column column = columns.get(index);
            if (column.color() != color) continue;
            ArrayList<Chip> chips = new ArrayList<>(column.chips());
            chips.add(new Chip(Math.max(1L, amountMinor), color));
            columns.set(index, new Column(color, chips));
            return new Layout(columns);
        }
        return new Layout(columns);
    }

    /**
     * Builds the reference rack by giving all four denominations a similar
     * physical presence. This establishes the secondary reference proportion;
     * later colour-ups prioritize balanced visual compression.
     */
    private static int[] balancedReferenceCounts(long referenceUnits) {
        double equalHeight = referenceUnits
                / (double) GdxChipDenominations.totalUnits();
        double[] targets = new double[COLUMN_COUNT];
        java.util.Arrays.fill(targets, equalHeight);
        return closestExactCounts(referenceUnits, targets, targets);
    }

    /**
     * Primarily balances the four physical column heights so their compression
     * remains visually even. Similarity to the initial rack is only a
     * secondary tie-breaker; exact represented value always wins.
     */
    private static int[] proportionalCounts(long representedUnits,
            long referenceUnits, int[] referenceCounts) {
        double equalHeight = representedUnits
                / (double) GdxChipDenominations.totalUnits();
        double[] compressionTargets = new double[COLUMN_COUNT];
        java.util.Arrays.fill(compressionTargets, equalHeight);
        double scale = representedUnits / (double) referenceUnits;
        double[] referenceTargets = new double[COLUMN_COUNT];
        for (int column = 0; column < COLUMN_COUNT; column++) {
            referenceTargets[column] = referenceCounts[column] * scale;
        }
        return closestExactCounts(representedUnits, compressionTargets,
                referenceTargets);
    }

    private static int[] closestExactCounts(long representedUnits,
            double[] compressionTargets, double[] referenceTargets) {
        int[] current = new int[COLUMN_COUNT];
        SearchResult best = new SearchResult(null,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, -1);
        SearchResult result = searchCounts(COLUMN_COUNT - 1,
                representedUnits, compressionTargets, referenceTargets,
                current, best);
        if (result.counts() != null) return result.counts();
        int[] fallback = new int[COLUMN_COUNT];
        fallback[0] = (int) Math.min(Integer.MAX_VALUE, representedUnits);
        return fallback;
    }

    private static SearchResult searchCounts(int column, long remainingUnits,
            double[] compressionTargets, double[] referenceTargets,
            int[] current, SearchResult best) {
        if (column == 0) {
            if (remainingUnits < 0L || remainingUnits > Integer.MAX_VALUE) {
                return best;
            }
            current[0] = (int) remainingUnits;
            double compressionScore = 0d;
            double referenceScore = 0d;
            int chipCount = 0;
            for (int index = 0; index < COLUMN_COUNT; index++) {
                compressionScore += squaredDistance(current[index],
                        compressionTargets[index]);
                referenceScore += squaredDistance(current[index],
                        referenceTargets[index]);
                chipCount += current[index];
            }
            if (compressionScore < best.compressionScore() - 0.000_001d
                    || Math.abs(compressionScore - best.compressionScore())
                    < 0.000_001d
                    && (referenceScore < best.referenceScore() - 0.000_001d
                    || Math.abs(referenceScore - best.referenceScore())
                    < 0.000_001d && chipCount > best.chipCount())) {
                return new SearchResult(current.clone(), compressionScore,
                        referenceScore, chipCount);
            }
            return best;
        }
        int centre = (int) Math.max(0d,
                Math.round(compressionTargets[column]));
        int minimum = Math.max(0, centre - PROPORTION_SEARCH_RADIUS);
        int maximum = centre + PROPORTION_SEARCH_RADIUS;
        int units = GdxChipDenominations.units(column);
        maximum = (int) Math.min(maximum, remainingUnits / units);
        SearchResult result = best;
        for (int count = minimum; count <= maximum; count++) {
            current[column] = count;
            result = searchCounts(column - 1,
                    remainingUnits - (long) count * units,
                    compressionTargets, referenceTargets, current, result);
        }
        return result;
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

        private static final Layout EMPTY = emptyLayout();

        Layout {
            columns = List.copyOf(columns);
            if (columns.size() != COLUMN_COUNT) {
                throw new IllegalArgumentException(
                        "A stack rack requires four denomination columns");
            }
        }

        private static Layout emptyLayout() {
            ArrayList<Column> columns = new ArrayList<>(COLUMN_COUNT);
            for (int column = 0; column < COLUMN_COUNT; column++) {
                columns.add(new Column(GdxChipDenominations.color(column),
                        List.of()));
            }
            return new Layout(columns);
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

    record Payment(Layout preparedLayout, List<Chip> chips) {

        Payment {
            chips = List.copyOf(chips);
        }
    }

    private record Selection(int count, Selection previous, Chip chip) {

        private static final Selection EMPTY = new Selection(0, null, null);
    }

    private record SearchResult(int[] counts, double compressionScore,
            double referenceScore, int chipCount) {
    }
}
