package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.media.ModMediaCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GdxGamePresentationSettingsTest {

    @Test
    void gameplayAnimationsIgnoreTheExcludedSwingMasterAndHonorSpecificSwitches(
            @TempDir Path temporary) {
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.properties().setProperty("animaciones", "false");
        preferences.properties().setProperty("animacion_ciegas_dealer", "false");
        preferences.properties().setProperty("animacion_apuestas", "false");
        preferences.properties().setProperty("animacion_contadores", "false");
        preferences.properties().setProperty("animacion_barajado", "false");
        preferences.properties().setProperty("animacion_reparto", "false");
        preferences.properties().setProperty("animacion_destape", "false");
        preferences.properties().setProperty("animacion_swap", "false");

        GdxGamePresentationSettings settings =
                new GdxGamePresentationSettings(preferences);

        assertFalse(settings.blindDealerAnimation());
        assertFalse(settings.betAnimation());
        assertFalse(settings.counterAnimation());
        assertFalse(settings.shuffleAnimation());
        assertFalse(settings.dealAnimation());
        assertFalse(settings.flipAnimation());
        assertFalse(settings.swapAnimation());

        preferences.properties().setProperty("animaciones", "true");
        preferences.properties().setProperty("animacion_reparto", "true");
        assertTrue(settings.dealAnimation());
        assertFalse(settings.flipAnimation());

        preferences.properties().setProperty("animaciones", "false");
        assertTrue(settings.dealAnimation(),
                "the hidden Swing master must not disable GDX animations");
    }

    @Test
    void swingZoomAndCompactPreferencesCannotAlterTheGdxTable(
            @TempDir Path temporary) {
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.properties().setProperty("vista_compacta", "3");
        preferences.properties().setProperty("zoom_level", "9");
        preferences.properties().setProperty("dialog_zoom", "1.75");

        GdxGamePresentationSettings settings =
                new GdxGamePresentationSettings(preferences);

        assertEquals(0, settings.compactView());
        assertEquals(1f, settings.zoomFactor());
        assertEquals(1f, settings.dialogZoomFactor());
        assertEquals("3", preferences.properties().getProperty(
                "vista_compacta"), "the Swing preference must be preserved");
        assertEquals("9", preferences.properties().getProperty("zoom_level"));
        assertEquals("1.75", preferences.properties().getProperty(
                "dialog_zoom"));
    }

    @Test
    void automaticRebuyIsMutableSessionStateAndStartsDisabled(
            @TempDir Path temporary) {
        GdxGamePresentationSettings settings = new GdxGamePresentationSettings(
                new PreferencesService(temporary.resolve("coronapoker.properties")));

        assertFalse(settings.autoRebuyOnBroke());
        settings.setAutoRebuyOnBroke(true);
        assertTrue(settings.autoRebuyOnBroke());
        settings.setAutoRebuyOnBroke(false);
        assertFalse(settings.autoRebuyOnBroke());
    }

    @Test
    void automaticFullscreenSettingFeedsTheCanonicalDealerLive(
            @TempDir Path temporary) {
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        GdxGamePresentationSettings settings =
                new GdxGamePresentationSettings(preferences);

        assertTrue(settings.autoFullscreen());
        preferences.properties().setProperty("auto_fullscreen", "false");
        assertFalse(settings.autoFullscreen());
        preferences.properties().setProperty("auto_fullscreen", "true");
        assertTrue(settings.autoFullscreen());
    }

    @Test
    void gameplaySoundSwitchesMatchTheSwingPreferenceMatrix(
            @TempDir Path temporary) {
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        GdxGamePresentationSettings settings
                = new GdxGamePresentationSettings(preferences);

        assertTrue(settings.shuffleSound());
        assertTrue(settings.dealSound());
        assertTrue(settings.flipSound());
        assertFalse(settings.ownHoleFlipSound());
        assertTrue(settings.checkSound());
        assertTrue(settings.foldSound());
        assertTrue(settings.callSound());
        assertTrue(settings.betSound());
        assertTrue(settings.allInSound());
        assertTrue(settings.warningSound());
        assertTrue(settings.turnWarningSound());

        preferences.properties().setProperty("sonido_barajado", "false");
        preferences.properties().setProperty("sonido_reparto", "false");
        preferences.properties().setProperty("sonido_destape", "false");
        preferences.properties().setProperty("sonido_destape_mis_cartas", "true");
        preferences.properties().setProperty("sonido_pasar", "false");
        preferences.properties().setProperty("sonido_fold", "false");
        preferences.properties().setProperty("sonido_igualar", "false");
        preferences.properties().setProperty("sonido_apostar", "false");
        preferences.properties().setProperty("sonido_allin", "false");
        preferences.properties().setProperty("sonido_aviso_tiempo", "false");

        assertFalse(settings.shuffleSound());
        assertFalse(settings.dealSound());
        assertFalse(settings.flipSound());
        assertFalse(settings.ownHoleFlipSound());
        assertFalse(settings.checkSound());
        assertFalse(settings.foldSound());
        assertFalse(settings.callSound());
        assertFalse(settings.betSound());
        assertFalse(settings.allInSound());
        assertTrue(settings.warningSound());
        assertFalse(settings.turnWarningSound());

        preferences.properties().setProperty("sonido_destape", "true");
        assertTrue(settings.ownHoleFlipSound());
        preferences.properties().setProperty("sonido_efectos", "false");
        assertFalse(settings.ownHoleFlipSound());
        assertFalse(settings.checkSound());
        assertFalse(settings.allInSound());
    }

    @Test
    void autoActionPreferencesAreObservedLiveByTheCanonicalDealer(
            @TempDir Path temporary) {
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        GdxGamePresentationSettings settings
                = new GdxGamePresentationSettings(preferences);

        assertFalse(settings.autoActionButtons());
        assertTrue(settings.autoActionPersist());

        preferences.properties().setProperty("auto_action_buttons", "true");
        preferences.properties().setProperty("auto_action_persist", "false");

        assertTrue(settings.autoActionButtons());
        assertFalse(settings.autoActionPersist());
    }

    @Test
    void grantedMsaaSamplesAreRecordedWithoutAcceptingInvalidCounts(
            @TempDir Path temporary) {
        GdxGamePresentationSettings settings = new GdxGamePresentationSettings(
                new PreferencesService(temporary.resolve("coronapoker.properties")));

        assertEquals(0, settings.actualMsaaSamples());
        settings.setActualMsaaSamples(4);
        assertEquals(4, settings.actualMsaaSamples());
        settings.setActualMsaaSamples(-1);
        assertEquals(0, settings.actualMsaaSamples());
    }

    @Test
    void msaaDefaultsToFourAndCyclesOnlyThroughSupportedLevels(
            @TempDir Path temporary) throws Exception {
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            GdxGamePresentationSettings settings =
                    new GdxGamePresentationSettings(preferences);

            assertEquals(4, settings.requestedMsaaSamples());
            assertEquals(8, settings.selectNextMsaaSamples());
            assertEquals(0, settings.selectNextMsaaSamples());
            assertEquals(2, settings.selectNextMsaaSamples());
            preferences.properties().setProperty("gdx_msaa_samples", "17");
            assertEquals(4, settings.requestedMsaaSamples());
        } finally {
            preferences.close();
        }
    }

    @Test
    void settingsPreviewChangesRuntimePropertiesWithoutWritingToDisk(
            @TempDir Path temporary) throws Exception {
        Path file = temporary.resolve("coronapoker.properties");
        PreferencesService preferences = new PreferencesService(file);
        preferences.start();
        preferences.properties().setProperty("baraja", "goliat");
        preferences.properties().setProperty("trasera", "default");
        preferences.properties().setProperty("color_tapete", "verde");
        preferences.properties().setProperty("gdx_msaa_samples", "4");
        preferences.save();

        GdxGamePresentationSettings settings =
                new GdxGamePresentationSettings(preferences);
        assertEquals("coronapoker", settings.selectNextDeck(false));
        assertEquals("goliat", settings.selectNextCardBack(false));
        assertEquals("azul", settings.selectNextFelt(false));
        assertEquals(8, settings.selectNextMsaaSamples(false));
        assertEquals("coronapoker", preferences.properties()
                .getProperty("baraja"));
        preferences.close();

        PreferencesService reopened = new PreferencesService(file);
        assertEquals("goliat", reopened.properties().getProperty("baraja"));
        assertEquals("default", reopened.properties().getProperty("trasera"));
        assertEquals("verde", reopened.properties()
                .getProperty("color_tapete"));
        assertEquals("4", reopened.properties()
                .getProperty("gdx_msaa_samples"));
        reopened.close();
    }

    @Test
    void concreteDeckSelectionUsesOnlyBundledOfficialDecks(
            @TempDir Path temporary) throws Exception {
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            GdxGamePresentationSettings settings =
                    new GdxGamePresentationSettings(preferences);

            assertEquals("goliat", settings.deck());
            assertEquals("coronapoker", settings.selectDeck("CoronaPoker"));
            assertEquals("coronapoker", settings.deck());
            assertEquals("goliat4", settings.selectDeck("goliat4"));
            assertEquals("goliat4", settings.deck());
            assertEquals("coronapoker", settings.selectPreviousDeck());
            assertEquals("goliat4", settings.selectNextDeck());

            // A deck belonging to an external mod must never become an
            // implicit bundled option in the portable GDX distribution.
            assertEquals("goliat4", settings.selectDeck("pepsiman"));
            assertEquals("goliat4", settings.deck());
        } finally {
            preferences.close();
        }
    }

    @Test
    void feltUsesSwingOrderAndNormalizesLegacySecretVariant(
            @TempDir Path temporary) throws Exception {
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            preferences.properties().setProperty("color_tapete", "verde*");
            GdxGamePresentationSettings settings =
                    new GdxGamePresentationSettings(preferences);

            assertEquals("verde", settings.felt());
            assertEquals("azul", settings.selectNextFelt());
            assertEquals("azul", settings.felt());
            assertEquals("rojo", settings.selectNextFelt());
            assertEquals("negro", settings.selectNextFelt());
            assertEquals("madera", settings.selectNextFelt());
            assertEquals("verde", settings.selectNextFelt());
            assertEquals("madera", settings.selectPreviousFelt());
            assertEquals("rojo", settings.selectFelt("ROJO"));
            assertEquals("rojo", settings.felt());
        } finally {
            preferences.close();
        }
    }

    @Test
    void cardBackCanFollowTheDeckOrUseAnIndependentSwingDeck(
            @TempDir Path temporary) throws Exception {
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            GdxGamePresentationSettings settings =
                    new GdxGamePresentationSettings(preferences);
            settings.selectDeck("goliat4");
            assertEquals("default", settings.cardBack());
            assertEquals("goliat4", settings.cardBackDeck());
            assertEquals("goliat", settings.selectNextCardBack());
            assertEquals("goliat", settings.cardBackDeck());
            assertEquals("default", settings.selectPreviousCardBack());
            assertEquals("goliat4", settings.cardBackDeck());
            assertEquals("interstate60",
                    settings.selectCardBack("Interstate60"));
            assertEquals("interstate60", settings.cardBackDeck());
            assertEquals("default", settings.selectCardBack("default"));
            assertEquals("goliat4", settings.cardBackDeck());
            assertEquals("default", settings.selectCardBack("missing"));
        } finally {
            preferences.close();
        }
    }

    @Test
    void externallyInstalledModDeckParticipatesWithoutBeingBundled(
            @TempDir Path temporary) throws Exception {
        Path installation = Files.createDirectories(temporary.resolve("app"));
        Path mod = Files.createDirectories(installation.resolve(
                "mod/decks/pepsiman/hq"));
        Files.writeString(installation.resolve("mod/mod.xml"),
                "<mod><name>Test</name><version>1</version></mod>");
        Files.writeString(mod.resolve("A_C.jpg"), "external");
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            GdxGamePresentationSettings settings =
                    new GdxGamePresentationSettings(preferences,
                            ModMediaCatalog.discover(installation));

            assertTrue(settings.availableDecks().contains("pepsiman"));
            assertEquals("pepsiman", settings.selectDeck("pepsiman"));
            assertTrue(settings.modDeck("pepsiman"));
            assertTrue(settings.modAsset("decks/pepsiman/hq/A_C.jpg")
                    .isPresent());
        } finally {
            preferences.close();
        }
    }
}
