package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxStackChipLayoutTest {

    @Test
    void keepsEveryVisualCompositionExactAndInsideFourColumns() {
        for (double amount : List.of(0.01d, 0.10d, 0.20d, 0.30d, 1.30d,
                9.80d, 10.00d, 10.05d, 39.00d, 90.00d, 9_999.99d)) {
            GdxStackChipLayout.Layout layout = GdxStackChipLayout.compose(
                    amount, 0.10d);

            assertEquals(GdxStackChipLayout.COLUMN_COUNT,
                    layout.columns().size());
            assertEquals(Math.round(amount * 100d),
                    layout.representedMinor(), "amount=" + amount);
            for (int column = 0; column < GdxStackChipLayout.COLUMN_COUNT;
                    column++) {
                assertTrue(layout.columnHeight(column) >= 0);
            }
        }
    }

    @Test
    void equalStacksAlwaysReceiveTheSameFourColourRack() {
        GdxStackChipLayout.Layout first = GdxStackChipLayout.compose(10d,
                0.10d);
        GdxStackChipLayout.Layout second = GdxStackChipLayout.compose(10d,
                0.10d);

        assertEquals(first, second);
        assertEquals(List.of(5, 5, 5, 6), columnHeights(first));
        assertTrue(first.columns().stream().allMatch(
                column -> !column.chips().isEmpty()));
    }

    @Test
    void blindsAlterOnlyTheNecessaryDeterministicRackComposition() {
        GdxStackChipLayout.Layout untouched = GdxStackChipLayout.compose(10d,
                0.10d, 10d);
        GdxStackChipLayout.Layout smallBlind = GdxStackChipLayout.compose(9.9d,
                0.10d, 10d);
        GdxStackChipLayout.Layout bigBlind = GdxStackChipLayout.compose(9.8d,
                0.10d, 10d);

        assertEquals(List.of(5, 5, 5, 6), columnHeights(untouched));
        assertEquals(990L, smallBlind.representedMinor());
        assertEquals(980L, bigBlind.representedMinor());
    }

    @Test
    void tenPlayerWinnerBalancesCompressionAcrossAllFourColumns() {
        GdxStackChipLayout.Layout initial = GdxStackChipLayout.compose(10d,
                0.10d, 10d);
        GdxStackChipLayout.Layout wholeTable = GdxStackChipLayout.compose(100d,
                0.10d, 10d);

        assertEquals(List.of(5, 5, 5, 6), columnHeights(initial));
        assertEquals(List.of(55, 55, 55, 56),
                columnHeights(wholeTable));
        assertEquals(10_000L, wholeTable.representedMinor());
    }

    @Test
    void accumulatedStackCanPayWithRealBlackHighValueChips() {
        GdxStackChipLayout.Layout wholeTable = GdxStackChipLayout.compose(100d,
                0.10d, 10d);

        List<GdxStackChipLayout.Chip> payment = GdxStackChipLayout.payment(
                wholeTable, 10d, 0.10d);

        assertEquals(10, payment.size());
        assertTrue(payment.stream().allMatch(chip -> chip.color()
                == GdxChipDenominations.color(3)));
        assertEquals(1_000L, payment.stream()
                .mapToLong(GdxStackChipLayout.Chip::amountMinor).sum());
    }

    @Test
    void emptyOrInvalidStacksKeepFiveEmptyColumnAnchors() {
        for (double amount : List.of(0d, -1d, Double.NaN)) {
            GdxStackChipLayout.Layout layout = GdxStackChipLayout.compose(
                    amount, 0.10d);
            assertEquals(List.of(0, 0, 0, 0), columnHeights(layout));
        }
    }

    @Test
    void paymentsUseTheFewestExactChipsAlreadyPresentInTheRack() {
        GdxStackChipLayout.Layout rack = GdxStackChipLayout.compose(10d,
                0.10d, 10d);

        List<GdxStackChipLayout.Chip> bigBlind =
                GdxStackChipLayout.payment(rack, 0.20d, 0.10d);
        assertEquals(List.of(new GdxStackChipLayout.Chip(20L,
                GdxChipDenominations.color(1))), bigBlind);

        List<GdxStackChipLayout.Chip> mixed =
                GdxStackChipLayout.payment(rack, 1.80d, 0.10d);
        assertEquals(4, mixed.size());
        assertEquals(180L, mixed.stream()
                .mapToLong(GdxStackChipLayout.Chip::amountMinor).sum());
        assertEquals(4L, mixed.stream().map(GdxStackChipLayout.Chip::color)
                .distinct().count());

        List<GdxStackChipLayout.Chip> allIn =
                GdxStackChipLayout.payment(rack, 10d, 0.10d);
        assertEquals(rack.chipCount(), allIn.size());
        assertEquals(rack.representedMinor(), allIn.stream()
                .mapToLong(GdxStackChipLayout.Chip::amountMinor).sum());
    }

    @Test
    void removalAndArrivalOnlyChangeTheirExactColourColumn() {
        GdxStackChipLayout.Layout rack = GdxStackChipLayout.compose(10d,
                0.10d, 10d);
        int gold = GdxChipDenominations.color(1);
        GdxStackChipLayout.Layout paid = GdxStackChipLayout.remove(rack,
                20L, gold);

        assertEquals(List.of(5, 4, 5, 6), columnHeights(paid));
        assertEquals(980L, paid.representedMinor());

        GdxStackChipLayout.Layout collected = GdxStackChipLayout.add(paid,
                20L, gold);
        assertEquals(rack, collected);
    }

    @Test
    void exactPaymentMakesOnlyTheNecessaryChangeWhenNoSubsetExists() {
        int green = GdxChipDenominations.color(3);
        GdxStackChipLayout.Layout rack = new GdxStackChipLayout.Layout(List.of(
                new GdxStackChipLayout.Column(
                        GdxChipDenominations.color(0), List.of()),
                new GdxStackChipLayout.Column(
                        GdxChipDenominations.color(1), List.of()),
                new GdxStackChipLayout.Column(
                        GdxChipDenominations.color(2), List.of()),
                new GdxStackChipLayout.Column(green,
                        List.of(new GdxStackChipLayout.Chip(100L, green)))));

        GdxStackChipLayout.Payment payment =
                GdxStackChipLayout.preparePayment(rack, 0.30d, 0.10d);
        assertEquals(List.of(
                new GdxStackChipLayout.Chip(10L,
                        GdxChipDenominations.color(0)),
                new GdxStackChipLayout.Chip(20L,
                        GdxChipDenominations.color(1))),
                payment.chips());
        assertEquals(100L, payment.preparedLayout().representedMinor());

        GdxStackChipLayout.Layout afterDeparture = payment.preparedLayout();
        for (GdxStackChipLayout.Chip chip : payment.chips()) {
            afterDeparture = GdxStackChipLayout.remove(afterDeparture,
                    chip.amountMinor(), chip.color());
        }
        assertEquals(70L, afterDeparture.representedMinor());
        assertEquals(List.of(0, 1, 1, 0),
                columnHeights(afterDeparture));
    }

    @Test
    void changeDescendsOnePhysicalDenominationStageAtATime() {
        assertTrue(GdxChipDenominations.breakDown(10L, 0.1d).isEmpty());
        assertEquals(List.of(
                new GdxChipDenominations.Chip(10L,
                        GdxChipDenominations.color(0)),
                new GdxChipDenominations.Chip(10L,
                        GdxChipDenominations.color(0))),
                GdxChipDenominations.breakDown(20L, 0.1d));
        assertEquals(List.of(20L, 20L, 10L),
                GdxChipDenominations.breakDown(50L, 0.1d).stream()
                        .map(GdxChipDenominations.Chip::amountMinor).toList());
        assertEquals(List.of(50L, 50L),
                GdxChipDenominations.breakDown(100L, 0.1d).stream()
                        .map(GdxChipDenominations.Chip::amountMinor).toList());
    }

    private static List<Integer> columnHeights(
            GdxStackChipLayout.Layout layout) {
        return layout.columns().stream()
                .map(column -> column.chips().size()).toList();
    }
}
