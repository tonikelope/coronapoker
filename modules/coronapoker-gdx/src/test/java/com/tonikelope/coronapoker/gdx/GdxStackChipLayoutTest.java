package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class GdxStackChipLayoutTest {

    @Test
    void keepsEveryVisualCompositionExactAndInsideBothColumns() {
        for (double amount : List.of(0.01d, 0.10d, 0.20d, 0.30d, 1.30d,
                9.80d, 10.00d, 10.05d, 39.00d, 90.00d, 9_999.99d)) {
            GdxStackChipLayout.Layout layout = GdxStackChipLayout.compose(
                    amount, 0.10d);

            assertEquals(Math.round(amount * 100d),
                    layout.representedMinor(), "amount=" + amount);
            assertTrue(layout.chips().size()
                    <= GdxStackChipLayout.MAX_VISIBLE_CHIPS);
            assertTrue(layout.columnHeight(0)
                    <= GdxStackChipLayout.MAX_CHIPS_PER_COLUMN);
            assertTrue(layout.columnHeight(1)
                    <= GdxStackChipLayout.MAX_CHIPS_PER_COLUMN);
        }
    }

    @Test
    void accumulatesSmallChipsThenColoursThemUpAtCapacity() {
        GdxStackChipLayout.Layout below = GdxStackChipLayout.compose(0.90d,
                0.10d);
        GdxStackChipLayout.Layout atCapacity = GdxStackChipLayout.compose(1d,
                0.10d);
        GdxStackChipLayout.Layout above = GdxStackChipLayout.compose(1.10d,
                0.10d);

        assertEquals(9, below.chips().size());
        assertEquals(10, atCapacity.chips().size());
        assertEquals(10, above.chips().size());
        assertTrue(above.chips().stream().anyMatch(chip -> chip.color() != 1),
                "the eleventh small chip must be exchanged for a higher value");
    }

    @Test
    void usesTheEstablishedFourColourPaletteForNormalStacks() {
        GdxStackChipLayout.Layout layout = GdxStackChipLayout.compose(9.80d,
                0.10d);

        assertTrue(layout.chips().stream().allMatch(
                chip -> chip.color() >= 0 && chip.color() <= 3));
        assertTrue(IntStream.rangeClosed(0, 3).allMatch(color ->
                layout.chips().stream().anyMatch(chip -> chip.color() == color)));
    }

    @Test
    void emptyOrInvalidStacksDoNotInventChips() {
        assertTrue(GdxStackChipLayout.compose(0d, 0.10d).chips().isEmpty());
        assertTrue(GdxStackChipLayout.compose(-1d, 0.10d).chips().isEmpty());
        assertTrue(GdxStackChipLayout.compose(Double.NaN, 0.10d)
                .chips().isEmpty());
    }
}
