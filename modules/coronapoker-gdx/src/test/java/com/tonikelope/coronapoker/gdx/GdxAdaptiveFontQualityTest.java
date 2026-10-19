package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
    void variableHudTypographyHasEveryExactNativeIntegerSize() {
        int[] sizes = CoronaPokerGdxTable.localOutcomeNativeSizes();
        assertEquals(51, sizes.length);
        assertEquals(68, sizes[0]);
        assertEquals(18, sizes[sizes.length - 1]);
        for (int index = 1; index < sizes.length; index++) {
            assertEquals(sizes[index - 1] - 1, sizes[index],
                    "the native family must not leave fitting gaps");
        }
        assertEquals(1.15f, CoronaPokerGdxTable.localOutcomeBorder(68),
                0.0001f);
        assertEquals(1.15f * 33f / 68f,
                CoronaPokerGdxTable.localOutcomeBorder(33), 0.0001f,
                "native tiers must retain one optical border ratio");
    }

    @Test
    void compactVariableHudAtlasCoversEverySemanticLabel() {
        String characters = CoronaPokerGdxTable.localOutcomeCharacters();
        assertEquals(characters.codePointCount(0, characters.length()),
                characters.codePoints().distinct().count(),
                "duplicate glyphs waste every atlas in the dense family");
        assertTrue(characters.chars().noneMatch(Character::isLowerCase),
                "the variable HUD renders its semantic labels in uppercase");

        List<String> keys = List.of(
                "hand.high_card", "hand.one_pair", "hand.two_pair",
                "hand.three_of_a_kind", "hand.straight", "hand.flush",
                "hand.full_house", "hand.four_of_a_kind",
                "hand.straight_flush", "hand.royal_flush",
                "action.label.fold", "action.label.check",
                "action.label.call", "action.label.bet",
                "action.label.raise", "action.label.fold2",
                "action.label.check2", "action.label.call2",
                "action.label.bet2", "action.label.raise2",
                "action.label.allin", "gdx.table.hud.win",
                "gdx.table.hud.lose", "gdx.table.hud.shows_cards",
                "gdx.table.hud.waiting_turn", "ui.muestras",
                "ui.suffix_close", "game.calentando",
                "player.espectador", "ui.expulsar_jugador");
        for (String language : List.of("es", "en")) {
            GdxGameText text = new GdxGameText(language);
            for (String key : keys) {
                assertCovered(characters,
                        text.translate(key).toUpperCase(Locale.ROOT));
            }
            assertCovered(characters,
                    CoronaPokerGdxTable.localHandOutcomeLabel(true,
                            List.of(1, 2, 3), text));
            assertCovered(characters,
                    CoronaPokerGdxTable.localShownOutcomeLabel(false, true,
                            List.of(1, 2, 3), text));
            assertCovered(characters,
                    CoronaPokerGdxTable.localHudIdleMessage(false, true,
                            false, false, text));
        }
        for (String dynamicValue : List.of(
                "-1234567.89", "+1234567,89", "BOTE #1+#2+#3",
                "POT #1+#2+#3", "COLOR (100%)", "SUBES (+1234.56)",
                "RAISE (+1234.56)", "TIMEOUT", "RABBIT")) {
            assertCovered(characters, dynamicValue);
        }
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

    private static void assertCovered(String repertoire, String value) {
        value.codePoints().forEach(codePoint -> assertTrue(
                repertoire.indexOf(codePoint) >= 0,
                () -> "missing glyph U+" + Integer.toHexString(codePoint)
                        .toUpperCase(Locale.ROOT) + " in: " + value));
    }
}
