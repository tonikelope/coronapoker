package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class GdxSettingsContractTest {

    @Test
    void masterVolumeHasOneValidatedDefaultAcrossEverySettingsSurface() {
        Properties properties = new Properties();
        assertEquals(0.8f, GdxSettingsContract.masterVolume(properties));

        properties.setProperty("master_volume", "0.35");
        assertEquals(0.35f, GdxSettingsContract.masterVolume(properties));

        for (String invalid : java.util.List.of("-0.1", "1.1", "NaN",
                "Infinity", "not-a-number")) {
            properties.setProperty("master_volume", invalid);
            assertEquals(0.8f, GdxSettingsContract.masterVolume(properties),
                    invalid);
        }
    }

    @Test
    void msaaSummaryIsSharedAndFollowsTheLiveLanguage() {
        assertEquals("4X  ·  ACTIVO", GdxSettingsContract.msaaStatusLabel(
                4, 4, new GdxGameText("es")));
        assertEquals("4X  ·  ACTIVE", GdxSettingsContract.msaaStatusLabel(
                4, 4, new GdxGameText("en")));
        assertEquals("4X  ·  RESTART (CURRENT DISABLED)",
                GdxSettingsContract.msaaStatusLabel(4, 0,
                        new GdxGameText("en")));
    }

    @Test
    void menuUsesTheCanonicalSwingTabOrder() {
        assertEquals(java.util.List.of(
                GdxSettingsContract.Section.APPEARANCE,
                GdxSettingsContract.Section.AUDIO,
                GdxSettingsContract.Section.SHORTCUTS,
                GdxSettingsContract.Section.DEBUG),
                GdxSettingsContract.MENU_SECTIONS);
    }

    @Test
    void liveTableAddsOneGameSectionWithoutDuplicatingTheOtherTabs() {
        assertEquals(java.util.List.of(
                GdxSettingsContract.Section.APPEARANCE,
                GdxSettingsContract.Section.AUDIO,
                GdxSettingsContract.Section.SHORTCUTS,
                GdxSettingsContract.Section.GAME,
                GdxSettingsContract.Section.DEBUG),
                GdxSettingsContract.TABLE_SECTIONS);
    }

    @Test
    void bothSettingsEntryPointsUseTheSameSubpageCatalogueBuilder() {
        assertEquals(java.util.List.of("MESA", "CINEMÁTICAS",
                "MESA Y CARTAS", "MOVIMIENTO", "INFORMACIÓN",
                "CHAT", "CAPTURA Y VISTA", "RITMO Y ESTILO"),
                GdxSettingsContract.subpageLabels(
                        GdxSettingsContract.Section.APPEARANCE,
                        java.util.List.of(), 0, 5));
        assertEquals(java.util.List.of("CIEGAS", "COMPRA"),
                GdxSettingsContract.subpageLabels(
                        GdxSettingsContract.Section.GAME,
                        java.util.List.of("CIEGAS", "COMPRA"), 0, 5));
        assertEquals(java.util.List.of("PÁGINA 1", "PÁGINA 2", "PÁGINA 3"),
                GdxSettingsContract.subpageLabels(
                        GdxSettingsContract.Section.SHORTCUTS,
                        java.util.List.of(), 11, 5));
        assertEquals(java.util.List.of("REGISTRO TÉCNICO"),
                GdxSettingsContract.subpageLabels(
                        GdxSettingsContract.Section.DEBUG,
                        java.util.List.of(), 0, 5));
    }

    @Test
    void settingsContentAndSubpagesFollowTheLiveLanguage() {
        GdxGameText text = new GdxGameText("en");
        assertEquals(java.util.List.of("TABLE", "CINEMATICS",
                "TABLE AND CARDS", "MOVEMENT", "INFORMATION",
                "CHAT", "CAPTURE AND VIEW", "PACE AND STYLE"),
                GdxSettingsContract.subpageLabels(
                        GdxSettingsContract.Section.APPEARANCE,
                        java.util.List.of(), 0, 5, text));
        assertEquals(java.util.List.of("BLINDS", "BUY-IN", "REBUY"),
                GdxSettingsContract.subpageLabels(
                        GdxSettingsContract.Section.GAME,
                        java.util.List.of("CIEGAS", "COMPRA", "RECOMPRA"),
                        0, 5, text));
        assertEquals("SOUND EFFECTS",
                GdxSettingsContract.AUDIO_PAGES.get(0).options().get(2)
                        .label(text));
        assertEquals("FOREVER",
                GdxSettingsContract.voiceRetentionLabel(
                        properties("audio_voice_note_retention_days", "0"),
                        text));

        for (GdxSettingsContract.TogglePage page
                : GdxSettingsContract.AUDIO_PAGES) {
            assertFalse(page.title(text).startsWith("GDX.SETTINGS."));
            for (GdxSettingsContract.ToggleOption option : page.options()) {
                assertFalse(option.label(text).startsWith("GDX.SETTINGS."));
            }
        }
        for (GdxSettingsContract.TogglePage page
                : GdxSettingsContract.APPEARANCE_PAGES) {
            assertFalse(page.title(text).startsWith("GDX.SETTINGS."));
            for (GdxSettingsContract.ToggleOption option : page.options()) {
                assertFalse(option.label(text).startsWith("GDX.SETTINGS."));
            }
        }
    }

    @Test
    void cancelRestoresAllSharedValuesAndShortcutAdditions() {
        Properties properties = new Properties();
        properties.setProperty("sonidos", "true");
        properties.setProperty("sonido_apostar", "true");
        properties.setProperty("shortcut.pause", "ALT+P");
        properties.setProperty("blind_structures.count", "1");
        properties.setProperty("blind_structure.0.name", "Original");
        properties.setProperty("blind_structure.0.levels", "1/2");
        Map<String, String> snapshot = GdxSettingsContract.snapshot(properties);

        properties.setProperty("sonidos", "false");
        properties.remove("sonido_apostar");
        properties.setProperty("baraja", "goliat4");
        properties.setProperty("shortcut.pause", "P");
        properties.setProperty("shortcut.extra", "F12");
        properties.setProperty("blind_structures.count", "2");
        properties.setProperty("blind_structure.0.name", "Editada");
        properties.setProperty("blind_structure.1.name", "Nueva");
        properties.setProperty("blind_structure.1.levels", "2/4");

        GdxSettingsContract.restore(properties, snapshot);

        assertEquals("true", properties.getProperty("sonidos"));
        assertEquals("true", properties.getProperty("sonido_apostar"));
        assertEquals("ALT+P", properties.getProperty("shortcut.pause"));
        assertNull(properties.getProperty("baraja"));
        assertFalse(properties.containsKey("shortcut.extra"));
        assertEquals("1", properties.getProperty("blind_structures.count"));
        assertEquals("Original",
                properties.getProperty("blind_structure.0.name"));
        assertNull(properties.getProperty("blind_structure.1.name"));
    }

    @Test
    void audioCatalogHasNoDuplicateKeysAndContainsSwingGameplayCues() {
        java.util.List<String> keys = GdxSettingsContract.AUDIO_PAGES.stream()
                .flatMap(page -> page.options().stream())
                .map(GdxSettingsContract.ToggleOption::key)
                .toList();

        assertEquals(keys.size(), new java.util.HashSet<>(keys).size());
        assertTrue(keys.containsAll(java.util.List.of(
                "sonidos", "musica", "sonido_efectos",
                "sonido_apostar", "sonido_igualar", "sonido_pasar",
                "sonido_allin", "sonido_fold", "sonido_barajado",
                "sonido_reparto", "sonido_destape",
                "sonido_destape_mis_cartas", "sonido_ciegas",
                "sonido_ultima_mano", "sonido_pausa", "sonido_tu_turno",
                "sonido_aviso_tiempo", "sonido_fin_partida",
                "sonido_entra", "sonido_entrar_sala", "sonido_sale",
                "sonido_tapete", "sonido_visor", "sonido_arranque",
                "musica_about",
                "tts_server",
                "voice_messages", "audio_mic_enabled",
                "audio_block_voice_messages", "audio_play_own_voice",
                "audio_block_tts_local")));
        assertTrue(GdxSettingsContract.AUDIO_PAGES.stream()
                .allMatch(page -> page.options().size() <= 5));
        assertFalse(keys.contains("sonido_zoom"));
        assertFalse(keys.contains("sonido_vista_compacta"));
        // Do not advertise settings for GDX surfaces/behaviour that do not
        // exist yet. The persisted Swing values remain untouched in the
        // transactional snapshot and can be exposed when their real GDX
        // consumers land.
        assertFalse(keys.contains("musica_stats"));
        assertTrue(keys.contains("sonido_entrar_sala"));
    }

    @Test
    void roomJoinCueIsNotMislabelledAsThePendingAdmissionCue() {
        GdxSettingsContract.ToggleOption joined =
                GdxSettingsContract.AUDIO_PAGES.stream()
                .flatMap(page -> page.options().stream())
                .filter(option -> option.key().equals("sonido_entra"))
                .findFirst().orElseThrow();

        assertEquals("CREAR PARTIDA / NUEVO JUGADOR", joined.label());
        assertFalse(joined.label().contains("QUIERE ENTRAR"));
    }

    @Test
    void negativeBlockPreferencesArePresentedAsPositiveGreenSwitches() {
        Properties properties = new Properties();
        GdxSettingsContract.ToggleOption localNotes =
                GdxSettingsContract.AUDIO_PAGES.stream()
                .flatMap(page -> page.options().stream())
                .filter(option -> option.key().equals(
                        "audio_block_voice_messages")).findFirst()
                .orElseThrow();

        assertTrue(GdxSettingsContract.displayedValue(localNotes, properties,
                true));
        properties.setProperty("audio_block_voice_messages", "true");
        assertFalse(GdxSettingsContract.displayedValue(localNotes, properties,
                true));
    }

    @Test
    void voiceRetentionCyclesOnlyThroughSwingSupportedValues() {
        Properties properties = new Properties();
        assertEquals("90 DÍAS",
                GdxSettingsContract.voiceRetentionLabel(properties));
        GdxSettingsContract.cycleVoiceRetention(properties);
        assertEquals("PARA SIEMPRE",
                GdxSettingsContract.voiceRetentionLabel(properties));
        GdxSettingsContract.cycleVoiceRetention(properties);
        assertEquals("7 DÍAS",
                GdxSettingsContract.voiceRetentionLabel(properties));
        GdxSettingsContract.adjustVoiceRetention(properties, -1);
        assertEquals("PARA SIEMPRE",
                GdxSettingsContract.voiceRetentionLabel(properties));
        properties.setProperty("audio_voice_note_retention_days", "999");
        assertEquals("90 DÍAS",
                GdxSettingsContract.voiceRetentionLabel(properties));
    }

    @Test
    void childAudioControlsRespectTheirSwingMasterGates() {
        Properties properties = new Properties();
        GdxSettingsContract.ToggleOption music =
                GdxSettingsContract.AUDIO_PAGES.get(1).options().get(0);
        GdxSettingsContract.ToggleOption effect =
                GdxSettingsContract.AUDIO_PAGES.get(2).options().get(0);

        assertTrue(GdxSettingsContract.enabled(music, properties, true));
        assertTrue(GdxSettingsContract.enabled(effect, properties, true));
        properties.setProperty("musica", "false");
        properties.setProperty("sonido_efectos", "false");
        assertFalse(GdxSettingsContract.enabled(music, properties, true));
        assertFalse(GdxSettingsContract.enabled(effect, properties, true));
        assertFalse(GdxSettingsContract.enabled(music, properties, false));
    }

    @Test
    void appearanceCatalogHasNoDuplicateKeysAndKeepsRowsInsideTheDialog() {
        java.util.List<String> keys = GdxSettingsContract.APPEARANCE_PAGES
                .stream()
                .flatMap(page -> page.options().stream())
                .map(GdxSettingsContract.ToggleOption::key)
                .toList();

        assertEquals(keys.size(), new java.util.HashSet<>(keys).size());
        assertTrue(keys.containsAll(java.util.List.of(
                "cinematicas", "cinematicas_allin",
                "cinematicas_gameover",
                "animacion_barajado", "animacion_reparto",
                "animacion_destape", "animacion_ciegas_dealer",
                "animacion_apuestas", "animacion_contadores",
                "animacion_swap",
                "animacion_contador_final", "gdx_show_fps",
                "mostrar_coste_igualar",
                "chat_images_ingame", "chat_game_notifications",
                "resaltar_jugada_showdown",
                "resaltar_avatares", "auto_fullscreen")));
        assertTrue(GdxSettingsContract.APPEARANCE_PAGES.stream()
                .allMatch(page -> page.options().size() <= 6));
        assertFalse(keys.contains("auto_zoom"));
        assertFalse(keys.contains("animaciones"));
        assertFalse(keys.contains("cinematicas_accion"));
        assertFalse(keys.contains("animacion_cascada_overlay"));
        assertFalse(keys.contains("animacion_downgrade"));
    }

    @Test
    void chatNotificationsShareSwingsCanonicalPreferenceAndMigrateGdxAlias() {
        Properties properties = new Properties();
        properties.setProperty("chat_notifications_ingame", "false");

        assertFalse(GdxSettingsContract.chatNotificationsEnabled(
                properties, true));
        assertTrue(GdxSettingsContract
                .migrateLegacyChatNotificationPreference(properties));
        assertEquals("false", properties.getProperty(
                GdxSettingsContract.CHAT_GAME_NOTIFICATIONS_KEY));
        assertFalse(properties.containsKey("chat_notifications_ingame"));
        assertFalse(GdxSettingsContract.chatNotificationsEnabled(
                properties, true));

        properties.setProperty("chat_notifications_ingame", "false");
        properties.setProperty(
                GdxSettingsContract.CHAT_GAME_NOTIFICATIONS_KEY, "true");
        assertTrue(GdxSettingsContract
                .migrateLegacyChatNotificationPreference(properties));
        assertTrue(GdxSettingsContract.chatNotificationsEnabled(
                properties, false));
    }

    @Test
    void childAppearanceControlsIgnoreTheExcludedSwingMasterAndRespectCinematics() {
        Properties properties = new Properties();
        GdxSettingsContract.ToggleOption cinematic =
                GdxSettingsContract.APPEARANCE_PAGES.get(0).options().get(2);
        GdxSettingsContract.ToggleOption deal =
                GdxSettingsContract.APPEARANCE_PAGES.get(1).options().get(1);

        assertTrue(GdxSettingsContract.enabled(cinematic, properties, true));
        assertTrue(GdxSettingsContract.enabled(deal, properties, true));
        properties.setProperty("cinematicas", "false");
        assertFalse(GdxSettingsContract.enabled(cinematic, properties, true));
        assertTrue(GdxSettingsContract.enabled(deal, properties, true));
        properties.setProperty("animaciones", "false");
        assertFalse(GdxSettingsContract.enabled(cinematic, properties, true));
        assertTrue(GdxSettingsContract.enabled(deal, properties, true));
    }

    @Test
    void audioDefaultsMatchSwingAndPreserveServerRulesOnClients() {
        Properties properties = new Properties();
        properties.setProperty("master_volume", "0.15");
        properties.setProperty("sonidos", "false");
        properties.setProperty("sonidos_chorra", "true");
        properties.setProperty("audio_mic_enabled", "true");
        properties.setProperty("audio_block_voice_messages", "true");
        properties.setProperty("audio_voice_note_retention_days", "7");
        properties.setProperty(GdxAudioDevices.OUTPUT_KEY, "Altavoces");
        properties.setProperty(GdxAudioDevices.CAPTURE_KEY, "Micrófono");
        properties.setProperty("tts_server", "false");
        properties.setProperty("voice_messages", "false");

        GdxSettingsContract.restoreAudioDefaults(properties, false, false);

        assertEquals("0.8", properties.getProperty("master_volume"));
        assertEquals("true", properties.getProperty("sonidos"));
        assertEquals("false", properties.getProperty("sonidos_chorra"));
        assertEquals("false", properties.getProperty("audio_mic_enabled"));
        assertEquals("false", properties.getProperty(
                "audio_block_voice_messages"));
        assertEquals("90", properties.getProperty(
                "audio_voice_note_retention_days"));
        assertEquals("", properties.getProperty(GdxAudioDevices.OUTPUT_KEY));
        assertEquals("", properties.getProperty(GdxAudioDevices.CAPTURE_KEY));
        assertEquals("false", properties.getProperty("tts_server"));
        assertEquals("false", properties.getProperty("voice_messages"));

        GdxSettingsContract.restoreAudioDefaults(properties, true, true);
        assertEquals("true", properties.getProperty("audio_mic_enabled"));
        assertEquals("true", properties.getProperty("tts_server"));
        assertEquals("true", properties.getProperty("voice_messages"));
    }

    @Test
    void globalCommunicationRowsAreIdentifiedForHostOnlyEditing() {
        java.util.Map<String, GdxSettingsContract.ToggleOption> options =
                GdxSettingsContract.AUDIO_PAGES.stream()
                        .flatMap(page -> page.options().stream())
                        .collect(java.util.stream.Collectors.toMap(
                                GdxSettingsContract.ToggleOption::key,
                                option -> option));

        assertTrue(GdxSettingsContract.isGlobalCommunicationOption(
                options.get("tts_server")));
        assertTrue(GdxSettingsContract.isGlobalCommunicationOption(
                options.get("voice_messages")));
        assertFalse(GdxSettingsContract.isGlobalCommunicationOption(
                options.get("audio_block_tts_local")));
        assertFalse(GdxSettingsContract.isGlobalCommunicationOption(
                options.get("audio_block_voice_messages")));
        assertFalse(GdxSettingsContract.enabled(options.get("tts_server"),
                new Properties(), true, false));
        assertTrue(GdxSettingsContract.enabled(options.get("tts_server"),
                new Properties(), true, true));
        assertTrue(GdxSettingsContract.enabled(
                options.get("audio_block_tts_local"), new Properties(),
                true, false));
    }

    @Test
    void appearanceDefaultsKeepStartupGraphicsOutOfTheLiveTable() {
        Properties properties = new Properties();
        properties.setProperty("baraja", "goliat4");
        properties.setProperty("trasera", "goliat");
        properties.setProperty("color_tapete", "rojo");
        properties.setProperty("nivel_luz", "10");
        properties.setProperty("animaciones", "false");
        properties.setProperty("card_flip_duration", "1100");
        properties.setProperty("gdx_window_mode", "windowed");
        properties.setProperty("gdx_msaa_samples", "0");
        properties.setProperty("gdx_show_fps", "false");

        GdxSettingsContract.restoreAppearanceDefaults(properties, false);

        assertEquals("goliat", properties.getProperty("baraja"));
        assertEquals("default", properties.getProperty("trasera"));
        assertEquals("verde", properties.getProperty("color_tapete"));
        assertEquals("50", properties.getProperty("nivel_luz"));
        assertEquals("true", properties.getProperty("gdx_show_fps"));
        assertEquals("false", properties.getProperty("animaciones"));
        assertEquals("620", properties.getProperty("card_flip_duration"));
        assertEquals("windowed", properties.getProperty("gdx_window_mode"));
        assertEquals("0", properties.getProperty("gdx_msaa_samples"));

        GdxSettingsContract.restoreAppearanceDefaults(properties, true);
        assertEquals("borderless", properties.getProperty("gdx_window_mode"));
        assertEquals("4", properties.getProperty("gdx_msaa_samples"));
    }

    @Test
    void headingsNeverExposeInternalPageCounters() {
        assertEquals("MESA",
                GdxSettingsContract.contentHeading(
                        GdxSettingsContract.Section.APPEARANCE, "MESA"));
        assertEquals("DEBUG",
                GdxSettingsContract.contentHeading(
                        GdxSettingsContract.Section.DEBUG, "DEBUG"));
        assertFalse(GdxSettingsContract.contentHeading(
                GdxSettingsContract.Section.APPEARANCE, "MESA")
                .contains("/"));
    }

    @Test
    void restoreDefaultsMatchesTheThreeCanonicalSwingSections() {
        assertTrue(GdxSettingsContract.hasRestoreDefaults(
                GdxSettingsContract.Section.APPEARANCE));
        assertTrue(GdxSettingsContract.hasRestoreDefaults(
                GdxSettingsContract.Section.AUDIO));
        assertTrue(GdxSettingsContract.hasRestoreDefaults(
                GdxSettingsContract.Section.SHORTCUTS));
        assertFalse(GdxSettingsContract.hasRestoreDefaults(
                GdxSettingsContract.Section.GAME));
        assertFalse(GdxSettingsContract.hasRestoreDefaults(
                GdxSettingsContract.Section.DEBUG));
    }

    private static Properties properties(String key, String value) {
        Properties properties = new Properties();
        properties.setProperty(key, value);
        return properties;
    }
}
