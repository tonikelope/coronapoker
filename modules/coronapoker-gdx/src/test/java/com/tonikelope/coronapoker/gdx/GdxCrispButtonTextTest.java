package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class GdxCrispButtonTextTest {

    @Test
    void snapsTheWorldCoordinateToAReal1440pFramebufferPixel() {
        float snapped = GdxCrispButtonText.snapWorldCoordinate(
                100.25f, 0f, 2560f, 1920f);

        assertEquals(100.5f, snapped, 0.0001f);
        assertEquals(134f, snapped * 2560f / 1920f, 0.0001f);
    }

    @Test
    void respectsViewportOffsetsAnd1080pOneToOneMapping() {
        assertEquals(100f, GdxCrispButtonText.snapWorldCoordinate(
                100.4f, 12f, 1920f, 1920f), 0.0001f);
    }

    @Test
    void invalidViewportMetricsLeaveTheCoordinateUntouched() {
        assertEquals(100.25f, GdxCrispButtonText.snapWorldCoordinate(
                100.25f, 0f, 0f, 1920f));
    }
}
