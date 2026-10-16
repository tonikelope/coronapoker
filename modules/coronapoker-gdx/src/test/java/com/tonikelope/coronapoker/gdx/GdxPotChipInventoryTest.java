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
                inventory.withdraw(1.8d, 41L);

        assertEquals(0, inventory.size());
        assertEquals(1.8d, amount(paid), 0.000_001d);
        assertEquals(colors(deposited), colors(paid));
    }

    @Test
    void successiveSplitPayoutsConserveCountColorsAndOddRemainder() {
        GdxPotChipInventory inventory = new GdxPotChipInventory();
        List<CoronaPokerGdxTable.VisualChipSpec> deposited = List.of(
                chip(0.1d, 1), chip(0.1d, 1), chip(0.2d, 3),
                chip(0.5d, 0), chip(1d, 2));
        inventory.deposit(deposited);

        List<CoronaPokerGdxTable.VisualChipSpec> first =
                inventory.withdraw(0.85d, 7L);
        List<CoronaPokerGdxTable.VisualChipSpec> second =
                inventory.withdraw(1d, 8L);
        List<CoronaPokerGdxTable.VisualChipSpec> all = new ArrayList<>(first);
        all.addAll(second);

        assertEquals(0.85d, amount(first), 0.000_001d);
        assertEquals(1d, amount(second), 0.000_001d);
        assertEquals(0.05d, inventory.amount(), 0.000_001d);
        assertEquals(deposited.size(), all.size() + inventory.size());
        all.addAll(inventory.snapshot());
        assertEquals(colors(deposited), colors(all));
    }

    @Test
    void fixedRebuyPackHasEveryColorAndMoreSmallChips() {
        List<CoronaPokerGdxTable.VisualChipSpec> first =
                CoronaPokerGdxTable.rebuyVisualChips(10d, 11L);
        List<CoronaPokerGdxTable.VisualChipSpec> second =
                CoronaPokerGdxTable.rebuyVisualChips(10d, 12L);

        assertEquals(14, first.size());
        assertEquals(10d, amount(first), 0.000_001d);
        assertEquals(6, countColor(first, 1));
        assertEquals(4, countColor(first, 3));
        assertEquals(3, countColor(first, 0));
        assertEquals(1, countColor(first, 2));
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
                        transfers, pot, 91L);
        List<CoronaPokerGdxTable.VisualChipSpec> paid = assignments.stream()
                .flatMap(assignment -> assignment.chips().stream()).toList();

        assertEquals(2, assignments.size());
        assertEquals(0.7d, amount(assignments.get(0).chips()), 0.000_001d);
        assertEquals(1.3d, amount(assignments.get(1).chips()), 0.000_001d);
        assertEquals(pot.size(), paid.size());
        assertEquals(colors(pot), colors(paid));
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
