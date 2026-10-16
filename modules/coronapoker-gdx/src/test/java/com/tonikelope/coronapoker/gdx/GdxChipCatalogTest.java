package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxChipCatalogTest {

    @Test
    void catalogAlwaysShowsOneChipOfEveryTypeFromHighestToLowest() {
        List<CoronaPokerGdxTable.VisualChipSpec> chips =
                CoronaPokerGdxTable.chipCatalogEntries(0.10d);

        assertEquals(List.of(1.00d, 0.50d, 0.20d, 0.10d),
                chips.stream().map(
                        CoronaPokerGdxTable.VisualChipSpec::amount).toList());
        assertEquals(List.of(2, 0, 3, 1), chips.stream().map(
                CoronaPokerGdxTable.VisualChipSpec::color).toList());
    }

    @Test
    void fullHdCatalogUsesCommunityCardDiameterAndOnlyNecessaryPanel() {
        CoronaPokerGdxTable.ChipCatalogLayout layout =
                CoronaPokerGdxTable.chipCatalogLayout(1_920f, 1_080f);

        assertEquals(140f, layout.chipDiameter(), 0.001f);
        assertEquals(4, layout.chips().size());
        assertEquals(742f, layout.panel().width, 0.001f);
        assertEquals(226f, layout.panel().height, 0.001f);
        for (int index = 1; index < layout.chips().size(); index++) {
            Rectangle previous = layout.chips().get(index - 1);
            Rectangle current = layout.chips().get(index);
            assertTrue(current.x > previous.x + previous.width);
        }
        assertFalse(layout.close().overlaps(layout.chips().get(3)));
    }

    @Test
    void compactScreensReduceAllFourChipsUniformlyWithoutPagination() {
        CoronaPokerGdxTable.ChipCatalogLayout layout =
                CoronaPokerGdxTable.chipCatalogLayout(640f, 360f);

        assertEquals(4, layout.chips().size());
        assertTrue(layout.chipDiameter() < 140f);
        assertTrue(layout.panel().x >= 0f);
        assertTrue(layout.panel().y >= 0f);
        assertTrue(layout.panel().x + layout.panel().width <= 640f);
        assertTrue(layout.panel().y + layout.panel().height <= 360f);
        assertTrue(layout.chips().stream().allMatch(chip
                -> Math.abs(chip.width - layout.chipDiameter()) < 0.001f
                && Math.abs(chip.height - layout.chipDiameter()) < 0.001f));
    }

    @Test
    void revealStartsEdgeOnAndSettlesExactlyFrontal() {
        assertEquals(0f, CoronaPokerGdxTable.chipCatalogRevealScale(0f, 0),
                0.001f);
        assertEquals(0f, CoronaPokerGdxTable.chipCatalogRevealScale(
                CoronaPokerGdxTable.CHIP_CATALOG_REVEAL_STAGGER_SECONDS,
                1), 0.001f);
        assertEquals(1f, CoronaPokerGdxTable.chipCatalogRevealScale(2f, 3),
                0.001f);
    }

    @Test
    void depthSpinMirrorsParallaxAndStopsPerfectlyFrontal() {
        float sample = CoronaPokerGdxTable.CHIP_CATALOG_SPIN_SECONDS * 0.13f;
        float left = CoronaPokerGdxTable.chipCatalogSpinDepthOffset(
                sample, -1f, 140f);
        float right = CoronaPokerGdxTable.chipCatalogSpinDepthOffset(
                sample, 1f, 140f);
        float depthScale = CoronaPokerGdxTable.chipCatalogSpinDepthScale(
                sample);

        assertTrue(depthScale >= 0f && depthScale < 1f);
        assertEquals(-left, right, 0.001f);
        assertEquals(1f, CoronaPokerGdxTable.chipCatalogSpinDepthScale(
                CoronaPokerGdxTable.CHIP_CATALOG_SPIN_SECONDS), 0.001f);
        assertEquals(0f, CoronaPokerGdxTable.chipCatalogSpinDepthOffset(
                CoronaPokerGdxTable.CHIP_CATALOG_SPIN_SECONDS,
                1f, 140f), 0.001f);
    }
}
