package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxAdaptiveFontQualityTest {

    @Test
    void matchesThePhysicalDensityAtCommonResolutions() {
        assertProfile(1280, 720, 2f / 3f);
        assertProfile(1366, 768, 768f / 1080f);
        assertProfile(1600, 900, 5f / 6f);
        assertProfile(1920, 1080, 1f);
        assertProfile(2560, 1440, 4f / 3f);
        assertProfile(3840, 2160, 2f);
    }

    @Test
    void calculatesContinuousDensityInsteadOfUsingResolutionPresets() {
        assertProfile(1437, 811, 1437f / 1920f);
        assertProfile(3440, 1440, 1440f / 1080f);
        assertProfile(640, 360, 1f / 3f);
        assertProfile(7680, 4320, 4f);
    }

    @Test
    void usesOnePhysicalDensityForEveryLogicalFontSize() {
        GdxAdaptiveFontQuality.Profile profile =
                GdxAdaptiveFontQuality.forBackBuffer(
                        2560, 1440, 1920f, 1080f);

        assertEquals(4f / 3f, profile.rasterScaleFor(15));
        assertEquals(4f / 3f, profile.rasterScaleFor(173));
    }

    @Test
    void roundsEachAtlasToItsExactFinalPhysicalPixelSize() {
        GdxAdaptiveFontQuality.Profile low =
                GdxAdaptiveFontQuality.forBackBuffer(
                        1280, 720, 1920f, 1080f);
        GdxAdaptiveFontQuality.Profile high =
                GdxAdaptiveFontQuality.forBackBuffer(
                        2560, 1440, 1920f, 1080f);
        GdxAdaptiveFontQuality.Profile ultra =
                GdxAdaptiveFontQuality.forBackBuffer(
                        3840, 2160, 1920f, 1080f);

        assertEquals(10, GdxAdaptiveFontQuality.rasterSizeFor(15, low));
        assertEquals(32, GdxAdaptiveFontQuality.rasterSizeFor(24, high));
        assertEquals(48, GdxAdaptiveFontQuality.rasterSizeFor(24, ultra));
    }

    @Test
    void invalidDimensionsRetainThePreviousSafeFallback() {
        GdxAdaptiveFontQuality.Profile profile =
                GdxAdaptiveFontQuality.forBackBuffer(
                        0, 0, 1920f, 1080f);

        assertEquals(1f, profile.physicalPixelsPerWorldUnit());
    }

    @Test
    void finalSummaryUsesMcLarenAtStartupAndAfterAdaptiveRebuild()
            throws IOException {
        String source = Files.readString(Path.of("src", "main", "java",
                "com", "tonikelope", "coronapoker", "gdx",
                "CoronaPokerGdxTable.java"));
        String startup = section(source,
                "private boolean createNextReportAndLogFont()",
                "private void disposeCreationFontGenerator()");
        String rebuild = section(source,
                "private void rebuildFonts(",
                "private void rebuildButtonFontTiers()");

        assertTrue(startup.contains("fonts/McLaren-Regular.ttf"));
        assertTrue(rebuild.contains("fonts/McLaren-Regular.ttf"));
        assertFalse(startup.contains("Montserrat-Bold.ttf"));
        assertFalse(startup.contains("Inter-Medium.ttf"));
        assertFalse(rebuild.contains("Montserrat-Bold.ttf"));
        assertFalse(rebuild.contains("Inter-Medium.ttf"));
    }

    private static String section(String source, String startMarker,
            String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static void assertProfile(int width, int height,
            float expected) {
        GdxAdaptiveFontQuality.Profile profile =
                GdxAdaptiveFontQuality.forBackBuffer(
                        width, height, 1920f, 1080f);
        assertEquals(expected, profile.physicalPixelsPerWorldUnit(), 0.0001f);
    }
}
