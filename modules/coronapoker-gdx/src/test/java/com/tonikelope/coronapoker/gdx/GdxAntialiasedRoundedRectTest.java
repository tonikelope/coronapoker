package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GdxAntialiasedRoundedRectTest {

    @Test
    void coverageFringeTracksPhysicalPixelsAcrossDisplayScales() {
        assertEquals(1.25f,
                GdxAntialiasedRoundedRect.coverageWidthForBackBufferHeight(
                        1080));
        assertEquals(0.625f,
                GdxAntialiasedRoundedRect.coverageWidthForBackBufferHeight(
                        2160));
        assertEquals(1.875f,
                GdxAntialiasedRoundedRect.coverageWidthForBackBufferHeight(
                        720));
        assertEquals(1.25f,
                GdxAntialiasedRoundedRect.coverageWidthForBackBufferHeight(0));
    }

    @Test
    void coverageStaysBoundedAndCornersHaveEnoughGeometry() {
        assertEquals(0.5f,
                GdxAntialiasedRoundedRect.coverageWidthForBackBufferHeight(
                        10000));
        assertEquals(2.5f,
                GdxAntialiasedRoundedRect.coverageWidthForBackBufferHeight(
                        300));
        assertTrue(GdxAntialiasedRoundedRect.CORNER_SEGMENTS >= 16);
    }
}
