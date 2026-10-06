package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class GdxAdaptiveFontQualityTest {

    @Test
    void keepsConsistentPhysicalSupersamplingAcrossCommonResolutions() {
        assertProfile(1280, 720, 1f, 1f);
        assertProfile(1366, 768, 1.25f, 1f);
        assertProfile(1600, 900, 1.25f, 1f);
        assertProfile(1920, 1080, 1.5f, 1.25f);
        assertProfile(2560, 1440, 2f, 1.5f);
        assertProfile(3840, 2160, 3f, 2.25f);
    }

    @Test
    void separatesNormalAndMemoryHeavyDisplayFontProfiles() {
        GdxAdaptiveFontQuality.Profile profile =
                GdxAdaptiveFontQuality.forBackBuffer(
                        2560, 1440, 1920f, 1080f);

        assertEquals(2f, profile.rasterScaleFor(76));
        assertEquals(1.5f, profile.rasterScaleFor(77));
    }

    @Test
    void invalidDimensionsRetainThePreviousSafeFallback() {
        GdxAdaptiveFontQuality.Profile profile =
                GdxAdaptiveFontQuality.forBackBuffer(
                        0, 0, 1920f, 1080f);

        assertEquals(2f, profile.normalRasterScale());
        assertEquals(1f, profile.largeRasterScale());
    }

    private static void assertProfile(int width, int height,
            float normal, float large) {
        GdxAdaptiveFontQuality.Profile profile =
                GdxAdaptiveFontQuality.forBackBuffer(
                        width, height, 1920f, 1080f);
        assertEquals(normal, profile.normalRasterScale());
        assertEquals(large, profile.largeRasterScale());
    }
}
