package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.table.TableVisualEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GdxPotChipInventoryTest {

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
}
