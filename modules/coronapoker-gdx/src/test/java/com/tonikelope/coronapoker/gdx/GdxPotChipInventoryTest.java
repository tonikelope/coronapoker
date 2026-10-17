package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GdxPotChipInventoryTest {

    @Test
    void recoveryFallbackBuildsBalancedRackInsteadOfMinimumGreenTower() {
        List<CoronaPokerGdxTable.VisualChipSpec> recovered =
                CoronaPokerGdxTable.balancedVisualChips(
                        10d, 0.1d, 500L);
        GdxStackChipLayout.Layout layout = CoronaPokerGdxTable
                .visualChipLayout(recovered);

        assertEquals(10d, amount(recovered), 0.000_001d);
        assertEquals(List.of(5, 5, 5, 6), columnHeights(layout));
        assertEquals(4L, recovered.stream().map(
                CoronaPokerGdxTable.VisualChipSpec::color).distinct().count());
        assertNotEquals(CoronaPokerGdxTable.dynamicVisualChips(
                10d, 0.1d, 500L), recovered,
                "recovery is a rack reconstruction, not a minimum-chip payment");
    }

    @Test
    void rebuyAllInPotAndPayoutKeepTheExactFourColourPack() {
        List<CoronaPokerGdxTable.VisualChipSpec> rebuy =
                CoronaPokerGdxTable.rebuyVisualChips(10d, 0.1d, 600L);
        GdxStackChipLayout.Layout rebought = CoronaPokerGdxTable
                .visualChipLayout(rebuy);
        GdxStackChipLayout.Payment allIn = GdxStackChipLayout
                .prepareAuthoritativePayment(rebought, 10d, 10d,
                        0.1d, 10d);
        assertTrue(GdxStackChipLayout.isExactPhysicalPayment(allIn, 10d));

        GdxPotChipInventory pot = new GdxPotChipInventory();
        pot.deposit(allIn.chips().stream().map(chip ->
                new CoronaPokerGdxTable.VisualChipSpec(
                        chip.amountMinor() / 100d, chip.color())).toList());
        List<CoronaPokerGdxTable.VisualChipSpec> payout = pot.withdraw(
                10d, 0.1d, 601L);
        List<CoronaPokerGdxTable.PayoutVisualAssignment> assigned =
                CoronaPokerGdxTable.allocatePayoutVisualChips(List.of(
                        new TableVisualEvent.PayoutBatch.Transfer(
                                "rebought-player", 10d, 0d, 10d)),
                        payout, 0.1d, 602L);

        GdxStackChipLayout.Layout landed = landIntoRack(
                assigned.get(0).chips(), 0.1d,
                CoronaPokerGdxTable.relaxedRackCapacities(
                        CoronaPokerGdxTable.stackChipRackGeometry(true)));
        assertEquals(1_000L, landed.representedMinor());
        assertEquals(List.of(5, 5, 5, 6), columnHeights(landed));
        assertEquals(4L, assigned.get(0).chips().stream().map(
                CoronaPokerGdxTable.VisualChipSpec::color).distinct().count());
        assertEquals(0d, pot.amount(), 0.000_001d);
    }

    @Test
    void compressedBetBatchColoursUpPotWithoutChangingItsValue() {
        GdxPotChipInventory inventory = new GdxPotChipInventory();
        ArrayList<CoronaPokerGdxTable.VisualChipSpec> smallChips =
                new ArrayList<>();
        for (int chip = 0; chip < 74; chip++) {
            smallChips.add(chip(0.1d,
                    GdxChipDenominations.color(0)));
        }
        inventory.deposit(smallChips);

        inventory.relieveCompressedColumns(0.1d,
                CoronaPokerGdxTable.relaxedRackCapacities(
                        CoronaPokerGdxTable.potChipRackGeometry()));

        assertEquals(7.4d, inventory.amount(), 0.000_001d);
        GdxStackChipLayout.Layout layout = CoronaPokerGdxTable
                .visualChipLayout(inventory.snapshot());
        assertEquals(List.of(4, 5, 4, 4), List.of(
                layout.columnHeight(0), layout.columnHeight(1),
                layout.columnHeight(2), layout.columnHeight(3)));
        assertTrue(colors(inventory.snapshot()).containsAll(
                List.of(0, 1, 2, 3)));
    }

    @Test
    void uncompressedPotPreservesEveryArrivingChipColour() {
        GdxPotChipInventory inventory = new GdxPotChipInventory();
        List<CoronaPokerGdxTable.VisualChipSpec> deposited = List.of(
                chip(0.1d, GdxChipDenominations.color(0)),
                chip(0.1d, GdxChipDenominations.color(0)),
                chip(0.2d, GdxChipDenominations.color(1)),
                chip(0.5d, GdxChipDenominations.color(2)));
        inventory.deposit(deposited);

        inventory.relieveCompressedColumns(0.1d,
                CoronaPokerGdxTable.relaxedRackCapacities(
                        CoronaPokerGdxTable.potChipRackGeometry()));

        assertEquals(deposited, inventory.snapshot());
    }

    @Test
    void totalBetRackUsesTheSamePeakCompressionReliefAsEveryOtherRack() {
        ArrayList<CoronaPokerGdxTable.VisualChipSpec> greenTower =
                new ArrayList<>();
        for (int chip = 0; chip < 40; chip++) {
            greenTower.add(chip(1d, GdxChipDenominations.color(3)));
        }
        int[] capacities = CoronaPokerGdxTable.relaxedRackCapacities(
                CoronaPokerGdxTable.stackChipRackGeometry(true));
        GdxStackChipLayout.Layout before = CoronaPokerGdxTable
                .visualChipLayout(greenTower);

        List<CoronaPokerGdxTable.VisualChipSpec> relieved =
                GdxPotChipInventory.relieveCompressedColumns(greenTower,
                        0.1d, capacities);
        GdxStackChipLayout.Layout after = CoronaPokerGdxTable
                .visualChipLayout(relieved);

        assertEquals(40d, amount(relieved), 0.000_001d);
        assertTrue(GdxStackChipLayout.compressionPeakOverflow(after,
                capacities) < GdxStackChipLayout.compressionPeakOverflow(
                        before, capacities));
        assertTrue(after.columns().stream().allMatch(
                column -> !column.chips().isEmpty()));
    }

    @Test
    void runItTwiceReservesTwoExactBalancedBoardInventories() {
        ArrayList<CoronaPokerGdxTable.VisualChipSpec> fullPot =
                new ArrayList<>();
        for (int chip = 0; chip < 30; chip++) {
            fullPot.add(chip(1d, GdxChipDenominations.color(3)));
        }
        for (int chip = 0; chip < 3; chip++) {
            fullPot.add(chip(0.2d, GdxChipDenominations.color(1)));
        }
        int[] capacities = CoronaPokerGdxTable.relaxedRackCapacities(
                CoronaPokerGdxTable.potChipRackGeometry());

        GdxPotChipInventory.RunItTwiceSplit split = GdxPotChipInventory
                .splitForRunItTwice(fullPot, 15.3d, 0.1d,
                        capacities, 91L);
        GdxStackChipLayout.Layout sideA = CoronaPokerGdxTable
                .visualChipLayout(split.sideA());
        GdxStackChipLayout.Layout sideB = CoronaPokerGdxTable
                .visualChipLayout(split.sideB());

        assertEquals(15.3d, amount(split.sideA()), 0.000_001d);
        assertEquals(15.3d, amount(split.sideB()), 0.000_001d);
        assertTrue(split.remainder().isEmpty());
        assertEquals(columnHeights(sideA), columnHeights(sideB));
        assertTrue(sideA.columns().stream().allMatch(
                column -> !column.chips().isEmpty()));
        assertTrue(sideB.columns().stream().allMatch(
                column -> !column.chips().isEmpty()));
    }

    @Test
    void runItTwiceKeepsTheOddPhysicalRemainderOutsideBothBoards() {
        ArrayList<CoronaPokerGdxTable.VisualChipSpec> fullPot =
                new ArrayList<>();
        for (int chip = 0; chip < 30; chip++) {
            fullPot.add(chip(1d, GdxChipDenominations.color(3)));
        }
        fullPot.add(chip(0.5d, GdxChipDenominations.color(2)));
        fullPot.add(chip(0.2d, GdxChipDenominations.color(1)));

        GdxPotChipInventory.RunItTwiceSplit split = GdxPotChipInventory
                .splitForRunItTwice(fullPot, 15.3d, 0.1d,
                        CoronaPokerGdxTable.relaxedRackCapacities(
                                CoronaPokerGdxTable.potChipRackGeometry()),
                        92L);

        assertEquals(15.3d, amount(split.sideA()), 0.000_001d);
        assertEquals(15.3d, amount(split.sideB()), 0.000_001d);
        assertEquals(0.1d, amount(split.remainder()), 0.000_001d);
    }

    @Test
    void runItTwiceDerivedPayoutsConserveEveryBoardAndRemainderChip() {
        List<CoronaPokerGdxTable.VisualChipSpec> fullPot =
                CoronaPokerGdxTable.balancedVisualChips(
                        30.7d, 0.1d, 700L);
        int[] capacities = CoronaPokerGdxTable.relaxedRackCapacities(
                CoronaPokerGdxTable.potChipRackGeometry());
        GdxPotChipInventory.RunItTwiceSplit split = GdxPotChipInventory
                .splitForRunItTwice(fullPot, 15.3d, 0.1d,
                        capacities, 701L);

        List<TableVisualEvent.PayoutBatch.Transfer> sideA = List.of(
                new TableVisualEvent.PayoutBatch.Transfer(
                        "main-winner", 10.2d, 0d, 10.2d),
                new TableVisualEvent.PayoutBatch.Transfer(
                        "side-winner", 5.1d, 0d, 5.1d));
        List<TableVisualEvent.PayoutBatch.Transfer> sideB = List.of(
                new TableVisualEvent.PayoutBatch.Transfer(
                        "main-winner", 10.3d, 0d, 20.5d),
                new TableVisualEvent.PayoutBatch.Transfer(
                        "uncalled-return", 5d, 5d, 5d));

        List<CoronaPokerGdxTable.PayoutVisualAssignment> assignedA =
                CoronaPokerGdxTable.allocatePayoutVisualChips(
                        sideA, split.sideA(), 0.1d, 702L);
        List<CoronaPokerGdxTable.PayoutVisualAssignment> assignedB =
                CoronaPokerGdxTable.allocatePayoutVisualChips(
                        sideB, split.sideB(), 0.1d, 703L);

        assertEquals(15.3d, assignedAmount(assignedA), 0.000_001d);
        assertEquals(15.3d, assignedAmount(assignedB), 0.000_001d);
        assertEquals(0.1d, amount(split.remainder()), 0.000_001d);
        assertEquals(30.7d, assignedAmount(assignedA)
                + assignedAmount(assignedB) + amount(split.remainder()),
                0.000_001d);
        assertEquals(List.of("main-winner", "uncalled-return"),
                CoronaPokerGdxTable.payoutRackRecipients(sideB));
    }

    @Test
    void wholePayoutReturnsTheExactStoredChipMultiset() {
        GdxPotChipInventory inventory = new GdxPotChipInventory();
        List<CoronaPokerGdxTable.VisualChipSpec> deposited = List.of(
                chip(0.1d, 1), chip(0.2d, 3), chip(0.5d, 0),
                chip(1d, 2));
        inventory.deposit(deposited);

        List<CoronaPokerGdxTable.VisualChipSpec> paid =
                inventory.withdraw(1.8d, 0.1d, 41L);

        assertEquals(0, inventory.size());
        assertEquals(1.8d, amount(paid), 0.000_001d);
        assertEquals(colors(deposited), colors(paid));
    }

    @Test
    void successiveSplitPayoutsLeaveSubMinimumOddChipInThePot() {
        GdxPotChipInventory inventory = new GdxPotChipInventory();
        List<CoronaPokerGdxTable.VisualChipSpec> deposited = List.of(
                chip(0.1d, 1), chip(0.1d, 1), chip(0.2d, 3),
                chip(0.5d, 0), chip(1d, 2));
        inventory.deposit(deposited);

        List<CoronaPokerGdxTable.VisualChipSpec> first =
                inventory.withdraw(0.85d, 0.1d, 7L);
        List<CoronaPokerGdxTable.VisualChipSpec> second =
                inventory.withdraw(1d, 0.1d, 8L);
        List<CoronaPokerGdxTable.VisualChipSpec> all = new ArrayList<>(first);
        all.addAll(second);

        assertEquals(0.8d, amount(first), 0.000_001d);
        assertEquals(1d, amount(second), 0.000_001d);
        assertEquals(0.1d, inventory.amount(), 0.000_001d);
        assertEquals(deposited.size(), all.size() + inventory.size());
        all.addAll(inventory.snapshot());
        assertEquals(colors(deposited), colors(all));
    }

    @Test
    void rebuyPackMatchesTheExactInitialFourColumnRack() {
        List<CoronaPokerGdxTable.VisualChipSpec> first =
                CoronaPokerGdxTable.rebuyVisualChips(10d, 0.1d, 11L);
        List<CoronaPokerGdxTable.VisualChipSpec> second =
                CoronaPokerGdxTable.rebuyVisualChips(10d, 0.1d, 12L);

        assertEquals(21, first.size());
        assertEquals(10d, amount(first), 0.000_001d);
        assertEquals(5, countColor(first, 1));
        assertEquals(5, countColor(first, 3));
        assertEquals(5, countColor(first, 0));
        assertEquals(6, countColor(first, 2));
        assertTrue(colors(first).containsAll(List.of(0, 1, 2, 3)));
        assertEquals(colors(first), colors(second));
        assertNotEquals(first, second,
                "The fixed pack composition must launch in varied order");
    }

    @Test
    void seededShuffleVariesOrderWithoutChangingComposition() {
        List<Integer> source = List.of(0, 1, 2, 3, 4, 5, 6, 7);
        ArrayList<Integer> first = new ArrayList<>(source);
        ArrayList<Integer> second = new ArrayList<>(source);

        GdxPotChipInventory.shuffle(first, 100L);
        GdxPotChipInventory.shuffle(second, 200L);

        assertNotEquals(first, second);
        assertEquals(source.stream().sorted().toList(),
                first.stream().sorted().toList());
        assertEquals(source.stream().sorted().toList(),
                second.stream().sorted().toList());
    }

    @Test
    void multiWinnerPayoutUsesEveryRememberedChipExactlyOnce() {
        List<CoronaPokerGdxTable.VisualChipSpec> pot = List.of(
                chip(0.1d, 1), chip(0.2d, 3), chip(0.5d, 0),
                chip(1d, 2), chip(0.1d, 1), chip(0.1d, 1));
        List<TableVisualEvent.PayoutBatch.Transfer> transfers = List.of(
                new TableVisualEvent.PayoutBatch.Transfer(
                        "winner-a", 0.7d, 0d, 10.7d),
                new TableVisualEvent.PayoutBatch.Transfer(
                        "winner-b", 1.3d, 0d, 11.3d));

        List<CoronaPokerGdxTable.PayoutVisualAssignment> assignments =
                CoronaPokerGdxTable.allocatePayoutVisualChips(
                        transfers, pot, 0.1d, 91L);
        List<CoronaPokerGdxTable.VisualChipSpec> paid = assignments.stream()
                .flatMap(assignment -> assignment.chips().stream()).toList();

        assertEquals(2, assignments.size());
        assertEquals(0.7d, amount(assignments.get(0).chips()), 0.000_001d);
        assertEquals(1.3d, amount(assignments.get(1).chips()), 0.000_001d);
        assertEquals(pot.size(), paid.size());
        assertEquals(colors(pot), colors(paid));
    }

    @Test
    void splitPotBreaksOnlyTheNecessaryBlackChipBeforeFlights() {
        List<CoronaPokerGdxTable.VisualChipSpec> pot = List.of(
                chip(1d, 2));
        List<TableVisualEvent.PayoutBatch.Transfer> transfers = List.of(
                new TableVisualEvent.PayoutBatch.Transfer(
                        "winner-a", 0.3d, 0d, 10.3d),
                new TableVisualEvent.PayoutBatch.Transfer(
                        "winner-b", 0.7d, 0d, 10.7d));

        List<CoronaPokerGdxTable.PayoutVisualAssignment> assignments =
                CoronaPokerGdxTable.allocatePayoutVisualChips(
                        transfers, pot, 0.1d, 123L);
        List<CoronaPokerGdxTable.VisualChipSpec> paid = assignments.stream()
                .flatMap(assignment -> assignment.chips().stream()).toList();

        assertEquals(0.3d, amount(assignments.get(0).chips()), 0.000_001d);
        assertEquals(0.7d, amount(assignments.get(1).chips()), 0.000_001d);
        assertEquals(1d, amount(paid), 0.000_001d);
        assertEquals(0, countColor(paid, 2));
        assertTrue(colors(paid).containsAll(List.of(0, 1, 3)));
    }

    @Test
    void exactHighValuePayoutKeepsTheBlackChipIntact() {
        GdxPotChipInventory inventory = new GdxPotChipInventory();
        CoronaPokerGdxTable.VisualChipSpec green = chip(1d, 2);
        inventory.deposit(List.of(green));

        assertEquals(List.of(green), inventory.withdraw(
                1d, 0.1d, 999L));
        assertEquals(0d, inventory.amount(), 0.000_001d);
    }

    @Test
    void everyDenominationExceptBlueCanBeBrokenAsNeeded() {
        for (int column = 1; column < GdxChipDenominations.COLUMN_COUNT;
                column++) {
            GdxPotChipInventory inventory = new GdxPotChipInventory();
            double amount = GdxChipDenominations.units(column) / 10d;
            inventory.deposit(List.of(chip(amount,
                    GdxChipDenominations.color(column))));

            List<CoronaPokerGdxTable.VisualChipSpec> paid =
                    inventory.withdraw(0.1d, 0.1d, 100L + column);

            assertEquals(0.1d, amount(paid), 0.000_001d);
            assertEquals(1, paid.size());
            assertEquals(GdxChipDenominations.color(0), paid.get(0).color());
            assertEquals(amount - 0.1d, inventory.amount(), 0.000_001d);
        }
    }

    @Test
    void theSmallestChipIsNeverBroken() {
        GdxPotChipInventory inventory = new GdxPotChipInventory();
        CoronaPokerGdxTable.VisualChipSpec blue = chip(0.1d,
                GdxChipDenominations.color(0));
        inventory.deposit(List.of(blue));

        assertTrue(inventory.withdraw(0.05d, 0.1d, 77L).isEmpty());
        assertEquals(List.of(blue), inventory.snapshot());
    }

    @Test
    void payoutAllocationRejectsAStaleIncompletePotInventory() {
        List<TableVisualEvent.PayoutBatch.Transfer> transfers = List.of(
                new TableVisualEvent.PayoutBatch.Transfer(
                        "winner", 1d, 0d, 1d));

        assertThrows(IllegalStateException.class,
                () -> CoronaPokerGdxTable.allocatePayoutVisualChips(
                        transfers,
                        List.of(chip(0.5d,
                                GdxChipDenominations.color(2))),
                        0.1d, 800L));
    }

    private static CoronaPokerGdxTable.VisualChipSpec chip(double amount,
            int color) {
        return new CoronaPokerGdxTable.VisualChipSpec(amount, color);
    }

    private static double amount(
            List<CoronaPokerGdxTable.VisualChipSpec> chips) {
        return chips.stream().mapToDouble(
                CoronaPokerGdxTable.VisualChipSpec::amount).sum();
    }

    private static long countColor(
            List<CoronaPokerGdxTable.VisualChipSpec> chips, int color) {
        return chips.stream().filter(chip -> chip.color() == color).count();
    }

    private static List<Integer> colors(
            List<CoronaPokerGdxTable.VisualChipSpec> chips) {
        return chips.stream().map(CoronaPokerGdxTable.VisualChipSpec::color)
                .sorted().toList();
    }

    private static List<Integer> columnHeights(
            GdxStackChipLayout.Layout layout) {
        return layout.columns().stream()
                .map(GdxStackChipLayout.Column::chips)
                .map(List::size).toList();
    }

    private static GdxStackChipLayout.Layout landIntoRack(
            List<CoronaPokerGdxTable.VisualChipSpec> chips,
            double smallBlind, int[] capacities) {
        GdxStackChipLayout.Layout rack = GdxStackChipLayout.compose(
                0d, smallBlind, 10d);
        for (CoronaPokerGdxTable.VisualChipSpec chip : chips) {
            rack = GdxStackChipLayout.add(rack,
                    Math.round(chip.amount() * 100d), chip.color());
            rack = GdxStackChipLayout.relieveCompression(rack,
                    rack.representedMinor() / 100d, smallBlind, 10d,
                    capacities);
        }
        return rack;
    }

    private static double assignedAmount(
            List<CoronaPokerGdxTable.PayoutVisualAssignment> assignments) {
        return assignments.stream().flatMap(
                assignment -> assignment.chips().stream()).mapToDouble(
                CoronaPokerGdxTable.VisualChipSpec::amount).sum();
    }
}
