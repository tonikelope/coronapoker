/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.media.ModMediaCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GdxModSettingsIndicatorTest {

    @Test
    void suffixIsStableAndNeverDuplicated() {
        assertEquals("pepsiman (MOD)",
                GdxModSettingsIndicator.label("pepsiman", true));
        assertEquals("pepsiman (MOD)",
                GdxModSettingsIndicator.label("pepsiman (MOD)", true));
        assertEquals("goliat",
                GdxModSettingsIndicator.label("goliat", false));
        assertTrue(GdxModSettingsIndicator.marked("pepsiman (MOD) *"));
        assertFalse(GdxModSettingsIndicator.marked("goliat"));
    }

    @Test
    void detectsOnlyMediaActuallySuppliedByTheMod(
            @TempDir Path temporary) throws Exception {
        Path installation = Files.createDirectories(temporary.resolve("app"));
        Files.createDirectories(installation.resolve("mod"));
        Files.writeString(installation.resolve("mod/mod.xml"),
                "<mod><name>Visual test</name><version>1</version></mod>");
        Path deck = Files.createDirectories(installation.resolve(
                "mod/decks/pepsiman/hq"));
        Files.writeString(deck.resolve("A_C.jpg"), "card");
        Path shuffle = Files.createDirectories(installation.resolve(
                "mod/decks/pepsiman/gif"));
        Files.writeString(shuffle.resolve("shuffle.gif"), "gif");
        Path allIn = Files.createDirectories(installation.resolve(
                "mod/cinematics/allin"));
        Files.writeString(allIn.resolve("custom.gif"), "gif");
        Path misc = Files.createDirectories(installation.resolve(
                "mod/cinematics/misc"));
        Files.writeString(misc.resolve("game_over.gif"), "gif");

        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            GdxGamePresentationSettings settings =
                    new GdxGamePresentationSettings(preferences,
                            ModMediaCatalog.discover(installation));
            settings.selectDeck("pepsiman", false);

            assertTrue(GdxModSettingsIndicator.deck(settings, "pepsiman"));
            assertTrue(GdxModSettingsIndicator.cardBack(settings,
                    "pepsiman"));
            assertFalse(GdxModSettingsIndicator.deck(settings, "goliat"));
            assertTrue(GdxModSettingsIndicator.option(settings,
                    "animacion_barajado"));
            assertTrue(GdxModSettingsIndicator.option(settings,
                    "cinematicas_allin"));
            assertTrue(GdxModSettingsIndicator.option(settings,
                    "cinematicas_gameover"));
            assertFalse(GdxModSettingsIndicator.option(settings,
                    "animacion_reparto"));
        } finally {
            preferences.close();
        }
    }
}
