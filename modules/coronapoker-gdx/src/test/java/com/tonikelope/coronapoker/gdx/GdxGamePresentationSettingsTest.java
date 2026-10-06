package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tonikelope.coronapoker.core.ApplicationMetadata;
import com.tonikelope.coronapoker.core.PreferencesService;
import com.tonikelope.coronapoker.core.media.ModMediaCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GdxGamePresentationSettingsTest {

    @Test
    void animationSpeedSelectorsFeedTheLiveGdxTimelines(
            @TempDir Path temporary) {
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        GdxGamePresentationSettings settings =
                new GdxGamePresentationSettings(preferences);
        Properties properties = preferences.properties();

        assertEquals(100, settings.dealSpeed());
        assertEquals(320, settings.swapAnimationDuration());
        assertEquals(0.620f, CoronaPokerGdxTable.cardFlipSeconds(properties),
                0.000_001f);

        GdxAppearanceOptions.adjust(
                GdxAppearanceOptions.ANIMATION_CHOICES.get(0), properties, 1);
        GdxAppearanceOptions.adjust(
                GdxAppearanceOptions.ANIMATION_CHOICES.get(1), properties, 1);
        GdxAppearanceOptions.adjust(
                GdxAppearanceOptions.ANIMATION_CHOICES.get(3), properties, 1);

        assertEquals(60, settings.dealSpeed());
        assertEquals(200, settings.swapAnimationDuration());
        assertEquals(0.480f, CoronaPokerGdxTable.cardFlipSeconds(properties),
                0.000_001f);
        assertEquals(0.090f, CoronaPokerGdxTable
                .liveDealFlightSeconds(10, settings.dealSpeed()),
                0.000_001f);
    }

    @Test
    void gameplayAnimationsHonorTheGlobalMasterAndSpecificSwitches(
            @TempDir Path temporary) {
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.properties().setProperty("animaciones", "false");
        preferences.properties().setProperty("animacion_fuego_allin", "false");
        preferences.properties().setProperty("animacion_efecto_ganador", "false");
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
        assertFalse(settings.allInFireAnimation());
        assertFalse(settings.winnerEffectAnimation());

        preferences.properties().setProperty("animaciones", "true");
        preferences.properties().setProperty("animacion_reparto", "true");
        preferences.properties().setProperty("animacion_fuego_allin", "true");
        preferences.properties().setProperty("animacion_efecto_ganador", "true");
        assertTrue(settings.dealAnimation());
        assertTrue(settings.allInFireAnimation());
        assertTrue(settings.winnerEffectAnimation());
        assertFalse(settings.flipAnimation());

        preferences.properties().setProperty("animaciones", "false");
        assertFalse(settings.dealAnimation());
        assertFalse(settings.allInFireAnimation());
        assertFalse(settings.winnerEffectAnimation());
        assertFalse(settings.cinematics());
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
    void funnySoundsPreferenceIsObservedLiveByTheCanonicalDealer(
            @TempDir Path temporary) {
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        GdxGamePresentationSettings settings =
                new GdxGamePresentationSettings(preferences);

        assertFalse(settings.sillySounds());
        preferences.properties().setProperty("sonidos_chorra", "true");
        assertTrue(settings.sillySounds());
        preferences.properties().setProperty("sonido_efectos", "false");
        assertTrue(settings.sillySounds(),
                "Swing keeps funny sounds independent from the effects family");
        preferences.properties().setProperty("sonidos_chorra", "false");
        assertFalse(settings.sillySounds());
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
    void frameRateLimitDefaultsToMonitorRefreshWithoutVsync(
            @TempDir Path temporary) throws Exception {
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            GdxGamePresentationSettings settings =
                    new GdxGamePresentationSettings(preferences);

            assertEquals(0, settings.requestedFrameRateLimit());
            assertFalse(settings.vsyncEnabled());
            assertEquals("120 FPS (VS OFF)",
                    settings.frameRateOverlayLabel(120));
            assertEquals(240, settings.effectiveFrameRateLimit(240));
            assertEquals(60, settings.effectiveFrameRateLimit(60));
            assertEquals(60, settings.selectNextFrameRateLimit(false));
            assertEquals(60, settings.effectiveFrameRateLimit(240));
            preferences.properties().setProperty("gdx_fps_limit", "240");
            assertEquals(240, settings.effectiveFrameRateLimit(144));
            assertTrue(settings.toggleVsync(false));
            assertEquals("144 FPS (VS ON)",
                    settings.frameRateOverlayLabel(144));
            assertEquals(145, settings.effectiveFrameRateLimit(144));
            preferences.properties().setProperty("gdx_fps_limit", "60");
            assertEquals(60, settings.effectiveFrameRateLimit(144));
            preferences.properties().setProperty("gdx_fps_limit", "0");
            assertEquals(145, settings.effectiveFrameRateLimit(144));
            assertFalse(settings.toggleVsync(false));
            preferences.properties().setProperty("gdx_fps_limit", "240");
            assertEquals(240, settings.effectiveFrameRateLimit(144));
            preferences.properties().setProperty("gdx_fps_limit", "999");
            assertEquals(0, settings.requestedFrameRateLimit());
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
            assertTrue(settings.secretFelt());
            assertEquals("azul", settings.selectNextFelt());
            assertFalse(settings.secretFelt());
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
    void fiveAlternatingFeltChangesActivateTheSecretVariant(
            @TempDir Path temporary) throws Exception {
        PreferencesService preferences = new PreferencesService(
                temporary.resolve("coronapoker.properties"));
        preferences.start();
        try {
            GdxGamePresentationSettings settings =
                    new GdxGamePresentationSettings(preferences);

            assertEquals("azul", settings.selectFelt("azul", false));
            assertEquals("verde", settings.selectFelt("verde", false));
            assertEquals("azul", settings.selectFelt("azul", false));
            assertEquals("verde", settings.selectFelt("verde", false));
            assertFalse(settings.secretFelt());
            assertEquals("azul", settings.selectFelt("azul", false));
            assertTrue(settings.secretFelt());
            assertEquals("azul*", preferences.properties()
                    .getProperty("color_tapete"));

            settings.selectFelt("verde", false);
            assertEquals("verde", settings.felt());
            assertFalse(settings.secretFelt());

            settings.selectFelt("azul", false);
            settings.selectFelt("rojo", false);
            settings.selectFelt("azul", false);
            settings.selectFelt("rojo", false);
            settings.selectFelt("azul", false);
            assertFalse(settings.secretFelt());
            settings.selectFelt("rojo", false);
            assertTrue(settings.secretFelt());
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
                """
                <mod><name>Test</name><version>1</version>
                  <updateurl>https://example.test/mod-version.txt</updateurl>
                  <updatepassword>secret</updatepassword>
                </mod>
                """);
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
            assertEquals("Test v1", settings.modDisplayName());
            assertEquals("1", settings.modVersion());
            assertEquals("https://example.test/mod-version.txt",
                    settings.modUpdateUri().orElseThrow().toString());
            assertEquals("secret", settings.modUpdatePassword());
            assertEquals("CoronaPoker " + ApplicationMetadata.VERSION
                    + " (Test v1)", settings.productVersionLabel());
            assertEquals("v" + ApplicationMetadata.VERSION + " (Test v1)",
                    CoronaPokerGdxTable.productVersionLabelFor(settings));
        } finally {
            preferences.close();
        }
    }

    @Test
    void modVersionPrefixIsNotDuplicated() {
        assertEquals("Chilean MOD v1.0",
                GdxGamePresentationSettings.modDisplayName(
                        "Chilean MOD", "v1.0"));
        assertEquals("Chilean MOD",
                GdxGamePresentationSettings.modDisplayName(
                        "Chilean MOD", ""));
    }

    @Test
    void tableVersionLabelFallsBackWhenPresentationSettingsAreUnavailable() {
        assertEquals("v" + ApplicationMetadata.VERSION,
                CoronaPokerGdxTable.productVersionLabelFor(null));
    }
}
