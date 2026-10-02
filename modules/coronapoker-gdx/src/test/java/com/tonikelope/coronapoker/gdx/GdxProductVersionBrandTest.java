package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.ApplicationMetadata;
import org.junit.jupiter.api.Test;

final class GdxProductVersionBrandTest {

    @Test
    void fallbackAlwaysIncludesTheProductVersion() {
        assertEquals("CoronaPoker " + ApplicationMetadata.VERSION,
                GdxProductVersionBrand.label(null));
    }

    @Test
    void quickAccessBarStartsOutsideTheCanonicalBrandLane() {
        assertFalse(GdxProductVersionBrand.overlapsQuickAccess(
                CoronaPokerGdxTable.FAST_BAR_Y));
        assertEquals(GdxProductVersionBrand.quickAccessY(),
                CoronaPokerGdxTable.FAST_BAR_Y);
        assertTrue(GdxProductVersionBrand.overlapsQuickAccess(
                GdxProductVersionBrand.top()));
    }

    @Test
    void canonicalBrandHasEnoughWidthForProductAndModMetadata() {
        assertEquals(15, GdxProductVersionBrand.FONT_SIZE);
        assertEquals(16f, GdxProductVersionBrand.X);
        assertEquals(20f, GdxProductVersionBrand.BASELINE_Y);
        assertEquals(620f, GdxProductVersionBrand.MAX_WIDTH);
    }
}
