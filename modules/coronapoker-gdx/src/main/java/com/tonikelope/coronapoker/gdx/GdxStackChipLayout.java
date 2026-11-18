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

    /**
     * Keeps a physical rack only while it represents the authoritative
     * balance exactly. Lifecycle transitions such as busted spectator ->
     * rebuy can legitimately reuse the same seat object; carrying the old
     * rack through that transition would make the next payment invent a
     * minimum-denomination fallback that never existed in the new stack.
     */
    static Layout reconcile(Layout current, double exactAmount,
            double smallBlind, double referenceAmount) {
        long exactMinor = Double.isFinite(exactAmount) && exactAmount > 0d
                ? Math.max(1L, Math.round(exactAmount * 100d)) : 0L;
        if (current != null && current.representedMinor() == exactMinor) {
            return current;
        }
        return compose(exactAmount, smallBlind, referenceAmount);
    }

    /**
     * Preserves the physical denominations already present until the rack
     * genuinely needs a colour-up. Uneven column heights are intentional:
     * chips that have just arrived retain their colour while every column
     * still fits at its relaxed spacing. A new exact composition is accepted
     * only when one column is compressed, another has relaxed capacity left,
     * and the exchange strictly reduces the worst compression pressure.
     */
    static Layout relieveCompression(Layout current, double exactAmount,
            double smallBlind, double referenceAmount,
            int[] relaxedCapacities) {
        if (relaxedCapacities == null
                || relaxedCapacities.length != COLUMN_COUNT) {
            throw new IllegalArgumentException(
                    "A stack rack requires four relaxed capacities");
        }
        long exactMinor = Double.isFinite(exactAmount) && exactAmount > 0d
                ? Math.max(1L, Math.round(exactAmount * 100d)) : 0L;
        if (current == null || current.representedMinor() != exactMinor) {
            return compose(exactAmount, smallBlind, referenceAmount);
        }

        boolean compressed = false;
        boolean hasRelaxedRoom = false;
        for (int column = 0; column < COLUMN_COUNT; column++) {
            int capacity = Math.max(0, relaxedCapacities[column]);
            int height = current.columnHeight(column);
            compressed |= height > capacity;
            hasRelaxedRoom |= height < capacity;
        }
        if (!compressed || !hasRelaxedRoom) return current;

        Layout exact = compose(exactAmount, smallBlind, referenceAmount);
        return hasLowerCompressionPressure(exact, current,
                relaxedCapacities) ? exact : current;
    }

    static int pilesNeeded(Layout layout) {
        return layout.columns().stream().mapToInt(
                column -> GdxTableChipSlots.pilesNeeded(column.chips().size())).sum();
    }

    /** Prefer an exact colour-up before overflowing the available ten-chip piles. */
    static Layout colourUpForPiles(Layout current, double smallBlind, int pileLimit) {
        if (pilesNeeded(current) <= pileLimit) return current;

        long baseMinor = GdxChipDenominations.baseMinor(smallBlind);
        int[] original = new int[COLUMN_COUNT];
        int[] fixed = new int[COLUMN_COUNT];
        long units = 0L;
        for (int column = 0; column < COLUMN_COUNT; column++) {
            int value = GdxChipDenominations.units(column);
            for (Chip chip : current.columns().get(column).chips()) {
                if (chip.amountMinor() == baseMinor * value) {
                    original[column]++;
                    units += value;
                } else {
                    // A split-pot remainder is not a denomination to trade.
                    fixed[column]++;
                }
            }
        }
        int[] fitted = fitTenChipPiles(units, original, fixed, pileLimit);
        if (fitted == null) {
            // A very large pot may require taller piles even in the largest denomination.
            fitted = new int[COLUMN_COUNT];
            for (int column = COLUMN_COUNT - 1; column >= 0; column--) {
                int value = GdxChipDenominations.units(column);
                fitted[column] = (int) (units / value);
                units %= value;
            }
        }
        if (java.util.Arrays.equals(original, fitted)) return current;
        ArrayList<Column> exchanged = new ArrayList<>(COLUMN_COUNT);
        for (int column = 0; column < COLUMN_COUNT; column++) {
            Column source = current.columns().get(column);
            long denomination = baseMinor * GdxChipDenominations.units(column);
            ArrayList<Chip> chips = new ArrayList<>(fitted[column] + fixed[column]);
            int remaining = fitted[column];
            for (Chip chip : source.chips()) {
                if (chip.amountMinor() != denomination) chips.add(chip);
                else if (remaining > 0) {
                    chips.add(chip);
                    remaining--;
                }
            }
            for (int index = 0; index < remaining; index++) {
                chips.add(new Chip(denomination, source.color()));
            }
            exchanged.add(new Column(source.color(), chips));
        }
        return new Layout(exchanged);
    }

    private static int[] fitTenChipPiles(long units, int[] original, int[] fixed, int pileLimit) {
        int height = GdxTableChipRack.CHIPS_PER_PILE;
        if (units > (long) pileLimit * height * 10) return null;
        int[] best = null;
        int bestChanges = Integer.MAX_VALUE;
        int originalCount = original[0] + original[1] + original[2] + original[3];
        int bestCount = Integer.MAX_VALUE;
        // There are only four denominations. Once x1, x2 and x5 are chosen,
        // the exact amount determines the x10 count.
        for (int ones = 0; ones <= Math.min(units, pileLimit * height - fixed[0]); ones++) {
            int afterOnes = pileLimit - GdxTableChipSlots.pilesNeeded(ones + fixed[0]);
            long afterOneValue = units - ones;
            for (int twos = 0; twos <= Math.min(afterOneValue / 2, afterOnes * height - fixed[1]); twos++) {
                int afterTwos = afterOnes - GdxTableChipSlots.pilesNeeded(twos + fixed[1]);
                long afterTwoValue = afterOneValue - 2L * twos;
                for (int fives = 0; fives <= Math.min(afterTwoValue / 5, afterTwos * height - fixed[2]); fives++) {
                    long remainder = afterTwoValue - 5L * fives;
                    if (remainder % 10 != 0) continue;
                    int tens = (int) (remainder / 10);
                    int afterFives = afterTwos - GdxTableChipSlots.pilesNeeded(fives + fixed[2]);
                    if (tens + fixed[3] > afterFives * height) continue;
                    int count = ones + twos + fives + tens;
                    int changes = Math.abs(ones - original[0]) + Math.abs(twos - original[1])
                            + Math.abs(fives - original[2]) + Math.abs(tens - original[3]);
                    boolean reduces = count < originalCount;
                    boolean bestReduces = bestCount < originalCount;
                    if (best == null || (reduces != bestReduces ? reduces
                            : changes < bestChanges || changes == bestChanges && count < bestCount)) {
                        bestChanges = changes;
                        bestCount = count;
                        best = new int[]{ones, twos, fives, tens};
                    }
                }
            }
        }
        return best;
    }

    /**
     * Prefers lowering the tallest compressed tower before minimizing the
     * aggregate overflow. A colour-up can legitimately create more physical
     * chips overall while spreading them across four much shorter columns;
     * comparing only the total overflow kept an extreme x10 tower unchanged
     * even when the other denominations were empty.
     */
    private static boolean hasLowerCompressionPressure(Layout candidate,
            Layout current, int[] relaxedCapacities) {
        int candidatePeak = compressionPeakOverflow(candidate,
                relaxedCapacities);
        int currentPeak = compressionPeakOverflow(current,
                relaxedCapacities);
        if (candidatePeak != currentPeak) return candidatePeak < currentPeak;

        long candidateEnergy = compressionOverflowEnergy(candidate,
                relaxedCapacities);
        long currentEnergy = compressionOverflowEnergy(current,
                relaxedCapacities);
        if (candidateEnergy != currentEnergy) {
            return candidateEnergy < currentEnergy;
        }
        return compressionOverflow(candidate, relaxedCapacities)
                < compressionOverflow(current, relaxedCapacities);
    }

    static int compressionPeakOverflow(Layout layout,
            int[] relaxedCapacities) {
        validateCompressionArguments(layout, relaxedCapacities);
        int peak = 0;
        for (int column = 0; column < COLUMN_COUNT; column++) {
            peak = Math.max(peak, Math.max(0, layout.columnHeight(column)
                    - Math.max(0, relaxedCapacities[column])));
        }
        return peak;
    }

    static long compressionOverflowEnergy(Layout layout,
            int[] relaxedCapacities) {
        validateCompressionArguments(layout, relaxedCapacities);
        long energy = 0L;
        for (int column = 0; column < COLUMN_COUNT; column++) {
            long overflow = Math.max(0, layout.columnHeight(column)
                    - Math.max(0, relaxedCapacities[column]));
            energy += overflow * overflow;
        }
        return energy;
    }

    static int compressionOverflow(Layout layout, int[] relaxedCapacities) {
        validateCompressionArguments(layout, relaxedCapacities);
        int overflow = 0;
        for (int column = 0; column < COLUMN_COUNT; column++) {
            overflow += Math.max(0, layout.columnHeight(column)
                    - Math.max(0, relaxedCapacities[column]));
        }
        return overflow;
    }

    private static void validateCompressionArguments(Layout layout,
            int[] relaxedCapacities) {
        if (layout == null || relaxedCapacities == null
                || relaxedCapacities.length != COLUMN_COUNT) {
            throw new IllegalArgumentException(
                    "A stack rack requires four relaxed capacities");
        }
    }

    /** Selects the fewest exact chips that physically exist in this rack. */
    static List<Chip> payment(Layout layout, double exactAmount,
            double smallBlind) {
        return preparePayment(layout, exactAmount, smallBlind).chips();
    }

    /**
     * Reconciles a rack with its authoritative balance and prepares a payment
     * that is guaranteed to exist in the returned physical layout. If an
     * otherwise exact rack cannot make change, the rack is exchanged first;
     * no chip is ever returned without also being present in that layout.
     */
    static Payment prepareAuthoritativePayment(Layout current,
            double exactBalance, double exactAmount, double smallBlind,
            double referenceAmount) {
        Layout reconciled = reconcile(current, exactBalance, smallBlind,
                referenceAmount);
        Payment direct = preparePayment(reconciled, exactAmount, smallBlind);
        if (isExactPhysicalPayment(direct, exactAmount)) return direct;

        long balanceMinor = cents(exactBalance);
        long paymentMinor = cents(exactAmount);
        if (paymentMinor <= 0L || paymentMinor > balanceMinor) {
            return new Payment(reconciled, List.of());
        }

        List<GdxChipDenominations.Chip> exactChips =
                GdxChipDenominations.minimum(exactAmount, smallBlind);
        long selectedMinor = exactChips.stream().mapToLong(
                GdxChipDenominations.Chip::amountMinor).sum();
        if (selectedMinor != paymentMinor) {
            return new Payment(reconciled, List.of());
        }

        Layout exchanged = compose((balanceMinor - paymentMinor) / 100d,
                smallBlind, referenceAmount);
        ArrayList<Chip> selected = new ArrayList<>(exactChips.size());
        for (GdxChipDenominations.Chip chip : exactChips) {
            Chip physical = new Chip(chip.amountMinor(), chip.color());
            exchanged = add(exchanged, physical.amountMinor(),
                    physical.color());
            selected.add(physical);
        }
        Payment repaired = new Payment(exchanged, selected);
        return isExactPhysicalPayment(repaired, exactAmount)
                ? repaired : new Payment(reconciled, List.of());
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
        if (layout == null) return new Payment(Layout.EMPTY, List.of());
        long targetMinor = Math.max(1L, Math.round(exactAmount * 100d));
        if (targetMinor > layout.representedMinor()) {
            return new Payment(layout, List.of());
        }
        Layout prepared = layout;
        while (true) {
            ArrayList<Chip> available = new ArrayList<>();
            for (int column = COLUMN_COUNT - 1; column >= 0; column--) {
                available.addAll(prepared.columns().get(column).chips());
            }
            var choice = GdxChipPaymentSelection.find(available, Chip::amountMinor, targetMinor);
            if (choice == null) return new Payment(prepared, List.of());
            List<Chip> selected = selectedChips(available, choice.pick());
            if (choice.exact()) return new Payment(prepared, selected);

            Chip toBreak = selected.stream()
                    .filter(chip -> !GdxChipDenominations.breakDown(
                            chip.amountMinor(), smallBlind).isEmpty())
                    .min(java.util.Comparator.comparingLong(
                            Chip::amountMinor)).orElse(null);
            if (toBreak == null) return new Payment(prepared, List.of());

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

    /**
     * Picks the cheapest sufficient set of visible chips, keeping only top-down
     * prefixes of each denomination. A prefix can include a split-pot remainder.
     */
    static int[] previewPaymentCounts(Layout layout, int[] visibleCounts, long targetMinor) {
        if (targetMinor <= 0L) return new int[COLUMN_COUNT];
        long available = 0L;
        for (int column = 0; column < COLUMN_COUNT; column++) {
            List<Chip> chips = layout.columns().get(column).chips();
            for (int index = 0; index < visibleCounts[column]; index++) {
                available += chips.get(index).amountMinor();
            }
        }
        if (targetMinor >= available) return visibleCounts.clone();

        var empty = new PreviewPick(-1, 0, 0, null);
        Map<Long, PreviewPick> partials = Map.of(0L, empty);
        PreviewPick best = null;
        long bestAmount = Long.MAX_VALUE;
        for (int column = COLUMN_COUNT - 1; column >= 0; column--) {
            List<Chip> chips = layout.columns().get(column).chips();
            var next = new LinkedHashMap<>(partials);
            for (var entry : partials.entrySet()) {
                long amount = entry.getKey();
                PreviewPick previous = entry.getValue();
                for (int count = 1; count <= visibleCounts[column]; count++) {
                    amount += chips.get(visibleCounts[column] - count).amountMinor();
                    int chipCount = previous.chipCount() + count;
                    if (amount >= targetMinor) {
                        if (best == null || amount < bestAmount
                                || (amount == bestAmount && chipCount < best.chipCount())) {
                            best = new PreviewPick(column, count, chipCount, previous);
                            bestAmount = amount;
                        }
                        break;
                    }
                    PreviewPick current = next.get(amount);
                    if (current == null || chipCount < current.chipCount()) {
                        next.put(amount, new PreviewPick(column, count, chipCount, previous));
                    }
                }
            }
            partials = next;
        }
        int[] selected = new int[COLUMN_COUNT];
        for (PreviewPick pick = best; pick != empty; pick = pick.previous()) {
            selected[pick.column()] = pick.count();
        }
        return selected;
    }

    private record PreviewPick(int column, int count, int chipCount, PreviewPick previous) { }

    static boolean isExactPhysicalPayment(Payment payment,
            double exactAmount) {
        if (payment == null) return false;
        long targetMinor = cents(exactAmount);
        if (targetMinor <= 0L || payment.chips().stream().mapToLong(
                Chip::amountMinor).sum() != targetMinor) {
            return false;
        }
        Layout remaining = payment.preparedLayout();
        long before = remaining.representedMinor();
        for (Chip chip : payment.chips()) {
            Layout reduced = remove(remaining, chip.amountMinor(),
                    chip.color());
            if (reduced == remaining) return false;
            remaining = reduced;
        }
        return before - remaining.representedMinor() == targetMinor;
    }

    private static long cents(double amount) {
        return Double.isFinite(amount) && amount > 0d
                ? Math.max(1L, Math.round(amount * 100d)) : 0L;
    }

    private static List<Chip> selectedChips(List<Chip> available,
            GdxChipPaymentSelection.Pick selection) {
        ArrayList<Chip> result = new ArrayList<>(selection.count());
        for (var cursor = selection; cursor.index() >= 0;
                cursor = cursor.previous()) {
            result.add(available.get(cursor.index()));
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
        SearchResult best = new SearchResult();
        searchCounts(COLUMN_COUNT - 1,
                representedUnits, compressionTargets, referenceTargets,
                current, best);
        if (best.chipCount == -1) {
            best.counts[0] = (int) Math.min(Integer.MAX_VALUE, representedUnits);
        }
        return best.counts;
    }

    private static void searchCounts(int column, long remainingUnits,
            double[] compressionTargets, double[] referenceTargets,
            int[] current, SearchResult best) {
        if (column == 0) {
            if (remainingUnits < 0L || remainingUnits > Integer.MAX_VALUE) {
                return;
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
            if (compressionScore < best.compressionScore - 0.000_001d
                    || Math.abs(compressionScore - best.compressionScore)
                    < 0.000_001d
                    && (referenceScore < best.referenceScore - 0.000_001d
                    || Math.abs(referenceScore - best.referenceScore)
                    < 0.000_001d && chipCount > best.chipCount)) {
                System.arraycopy(current, 0, best.counts, 0, COLUMN_COUNT);
                best.compressionScore = compressionScore;
                best.referenceScore = referenceScore;
                best.chipCount = chipCount;
            }
            return;
        }
        int centre = (int) Math.max(0d,
                Math.round(compressionTargets[column]));
        int minimum = Math.max(0, centre - PROPORTION_SEARCH_RADIUS);
        int maximum = centre + PROPORTION_SEARCH_RADIUS;
        int units = GdxChipDenominations.units(column);
        maximum = (int) Math.min(maximum, remainingUnits / units);
        for (int count = minimum; count <= maximum; count++) {
            current[column] = count;
            searchCounts(column - 1,
                    remainingUnits - (long) count * units,
                    compressionTargets, referenceTargets, current, best);
        }
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

    private static final class SearchResult {
        private final int[] counts = new int[COLUMN_COUNT];
        private double compressionScore = Double.POSITIVE_INFINITY;
        private double referenceScore = Double.POSITIVE_INFINITY;
        private int chipCount = -1;
    }
}
