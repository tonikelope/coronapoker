package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
                assertTrue(layout.columnHeight(column)
                        <= GdxStackChipLayout.MAX_CHIPS_PER_COLUMN);
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
        assertEquals(List.of(4, 5, 5, 6), columnHeights(smallBlind));
        assertEquals(List.of(5, 4, 5, 6), columnHeights(bigBlind));
    }

    @Test
    void colourUpReducesAllColumnsBeforeTheyCanOverflow() {
        GdxStackChipLayout.Layout fullRack = GdxStackChipLayout.compose(10.8d,
                0.10d);
        GdxStackChipLayout.Layout colouredUp = GdxStackChipLayout.compose(10.9d,
                0.10d);

        assertEquals(List.of(6, 6, 6, 6), columnHeights(fullRack));
        assertEquals(List.of(3, 3, 3, 3), columnHeights(colouredUp));
        assertNotEquals(fullRack, colouredUp);
        assertEquals(1_080L, fullRack.representedMinor());
        assertEquals(1_090L, colouredUp.representedMinor());
    }

    @Test
    void emptyOrInvalidStacksKeepFourEmptyColumnAnchors() {
        for (double amount : List.of(0d, -1d, Double.NaN)) {
            GdxStackChipLayout.Layout layout = GdxStackChipLayout.compose(
                    amount, 0.10d);
            assertEquals(List.of(0, 0, 0, 0), columnHeights(layout));
        }
    }

    private static List<Integer> columnHeights(
            GdxStackChipLayout.Layout layout) {
        return layout.columns().stream()
                .map(column -> column.chips().size()).toList();
    }
}
