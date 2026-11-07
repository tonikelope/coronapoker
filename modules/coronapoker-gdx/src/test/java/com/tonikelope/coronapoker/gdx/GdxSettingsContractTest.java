package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class GdxSettingsContractTest {

    @Test
    void msaaRestartNoticeRequiresARealPendingBackBufferChange() {
        assertTrue(GdxSettingsContract.requiresMsaaRestart(4, 8, 4));
        assertFalse(GdxSettingsContract.requiresMsaaRestart(4, 4, 4));
        assertFalse(GdxSettingsContract.requiresMsaaRestart(4, 4, 0));
        assertFalse(GdxSettingsContract.requiresMsaaRestart(4, 8, 8));
        assertFalse(GdxSettingsContract.requiresMsaaRestart(8, 4, 4));
    }

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
        assertEquals("MSAA 4X", GdxSettingsContract.msaaStatusLabel(
                4, 4, new GdxGameText("es")));
        assertEquals("MSAA 4X", GdxSettingsContract.msaaStatusLabel(
                4, 4, new GdxGameText("en")));
        assertEquals("MSAA 4X",
                GdxSettingsContract.msaaStatusLabel(4, 0,
                        new GdxGameText("en")));
        assertEquals("SUAVE",
                GdxSettingsContract.cardRenderingStatusLabel(1,
                        new GdxGameText("es")));
        assertEquals("NATIVE",
                GdxSettingsContract.cardRenderingStatusLabel(0,
                        new GdxGameText("en")));
        assertEquals("", GdxSettingsContract.msaaRestartStatus(
                4, 4, new GdxGameText("es")));
        assertEquals("[!] RESTART PENDING (CURRENT MSAA DISABLED)",
                GdxSettingsContract.msaaRestartStatus(4, 0,
                        new GdxGameText("en")));
    }

    @Test
    void performanceImpactHighlightsOnlyMeaningfulRenderSettings() {
        Properties properties = new Properties();

        assertEquals(GdxSettingsContract.PerformanceImpact.HIGH,
                GdxSettingsContract.performanceImpact(
                        "gdx_vsync", properties));

        assertEquals(GdxSettingsContract.PerformanceImpact.HIGH,
                GdxSettingsContract.performanceImpact(
                        "animacion_barajado", properties));
        assertEquals(GdxSettingsContract.PerformanceImpact.HIGH,
                GdxSettingsContract.performanceImpact(
                        "animacion_fuego_allin", properties));
        assertEquals(GdxSettingsContract.PerformanceImpact.MEDIUM,
                GdxSettingsContract.performanceImpact(
                        "animacion_efecto_ganador", properties));
        assertEquals(GdxSettingsContract.PerformanceImpact.MEDIUM,
                GdxSettingsContract.performanceImpact(
                        "cinematicas_allin", properties));
        assertEquals(GdxSettingsContract.PerformanceImpact.NONE,
                GdxSettingsContract.performanceImpact(
                        "show_time", properties));
    }

    @Test
    void booleanValuesUseCanonicalStatusAndMarkTheFactoryDefault() {
        GdxGameText spanish = new GdxGameText("es");
        GdxGameText english = new GdxGameText("en");

        assertEquals("DESACTIVADO",
                GdxSettingsContract.vsyncStatusLabel(false, spanish));
        assertEquals("ACTIVADO *",
                GdxSettingsContract.vsyncStatusLabel(true, spanish));
        assertEquals("DISABLED",
                GdxSettingsContract.vsyncStatusLabel(false, english));
        assertEquals("ENABLED *",
                GdxSettingsContract.vsyncStatusLabel(true, english));
        assertEquals("ACTIVADO",
                GdxSettingsContract.booleanStatusLabel(true, false,
                        spanish));
        assertEquals("DESACTIVADO *",
                GdxSettingsContract.booleanStatusLabel(false, false,
                        spanish));
    }

    @Test
    void autoCallSummaryAlwaysDescribesTheCompleteState() {
        GdxGameText spanish = new GdxGameText("es");
        GdxGameText english = new GdxGameText("en");

        assertEquals("DESACTIVADO *",
                GdxSettingsContract.autoCallStatusLabel(false, 0d,
                        "0.05", spanish));
        assertEquals("ACTIVADO (SIN LÍMITE)",
                GdxSettingsContract.autoCallStatusLabel(true, 0d,
                        "0.05", spanish));
        assertEquals("ACTIVADO (HASTA 1.25)",
                GdxSettingsContract.autoCallStatusLabel(true, 1.25d,
                        "1.25", spanish));
        assertEquals("ENABLED (UP TO 1.25)",
                GdxSettingsContract.autoCallStatusLabel(true, 1.25d,
                        "1.25", english));
    }

    @Test
    void antialiasingImpactBelongsToTheSettingNotItsSelectedValue() {
        Properties properties = new Properties();
        properties.setProperty("gdx_msaa_samples", "0");
        assertEquals(GdxSettingsContract.PerformanceImpact.HIGH,
                GdxSettingsContract.performanceImpact(
                        "gdx_msaa_samples", properties));
        properties.setProperty("gdx_msaa_samples", "2");
        assertEquals(GdxSettingsContract.PerformanceImpact.HIGH,
                GdxSettingsContract.performanceImpact(
                        "gdx_msaa_samples", properties));
        properties.setProperty("gdx_msaa_samples", "8");
        assertEquals(GdxSettingsContract.PerformanceImpact.HIGH,
                GdxSettingsContract.performanceImpact(
                        "gdx_msaa_samples", properties));
        for (String quality : java.util.List.of("0", "1")) {
            properties.setProperty("gdx_card_smaa_quality", quality);
            assertEquals(GdxSettingsContract.PerformanceImpact.HIGH,
                    GdxSettingsContract.performanceImpact(
                            "gdx_card_smaa_quality", properties));
        }

    }

    @Test
    void otherGraphicsImpactStillReflectsItsConfiguredCost() {
        Properties properties = new Properties();

        properties.setProperty("gdx_fps_limit", "60");
        assertEquals(GdxSettingsContract.PerformanceImpact.LOW,
                GdxSettingsContract.performanceImpact(
                        "gdx_fps_limit", properties));
        properties.setProperty("gdx_fps_limit", "120");
        assertEquals(GdxSettingsContract.PerformanceImpact.MEDIUM,
                GdxSettingsContract.performanceImpact(
                        "gdx_fps_limit", properties));
        properties.setProperty("gdx_fps_limit", "0");
        assertEquals(GdxSettingsContract.PerformanceImpact.HIGH,
                GdxSettingsContract.performanceImpact(
                        "gdx_fps_limit", properties));

        properties.setProperty("card_flip_zoom", "100");
        assertEquals(GdxSettingsContract.PerformanceImpact.LOW,
                GdxSettingsContract.performanceImpact(
                        "card_flip_zoom", properties));
        properties.setProperty("card_flip_zoom", "145");
        assertEquals(GdxSettingsContract.PerformanceImpact.MEDIUM,
                GdxSettingsContract.performanceImpact(
                        "card_flip_zoom", properties));
    }

    @Test
    void menuAddsFutureGameDefaultsToTheCanonicalTabOrder() {
        assertEquals(java.util.List.of(
                GdxSettingsContract.Section.APPEARANCE,
                GdxSettingsContract.Section.AUDIO,
                GdxSettingsContract.Section.SHORTCUTS,
                GdxSettingsContract.Section.GAME,
                GdxSettingsContract.Section.DEBUG),
                GdxSettingsContract.MENU_SECTIONS);
    }

    @Test
    void menuSettingsDoNotDereferenceAnAbsentLobby() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/tonikelope/coronapoker/gdx/"
                + "GdxFrontendScreen.java"));

        assertTrue(source.contains("settingsTable != null && lobby != null "
                + "&& lobby.recovering()"));
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
    void everySwitchDependencyKeepsItsMasterGate() {
        GdxSettingsContract.TogglePage animations =
                GdxSettingsContract.APPEARANCE_PAGES.get(0);
        assertFalse(GdxSettingsContract.isChildOption(animations,
                animations.options().get(0)));
        assertTrue(animations.options().stream().skip(1).allMatch(option ->
                GdxSettingsContract.isChildOption(animations, option)));

        GdxSettingsContract.TogglePage music =
                GdxSettingsContract.AUDIO_PAGES.get(1);
        assertFalse(GdxSettingsContract.isChildOption(music,
                music.options().get(0)));
        assertEquals("musica", music.options().get(0).key());
        assertTrue(music.options().stream().skip(1).allMatch(option ->
                GdxSettingsContract.isChildOption(music, option)));

        GdxSettingsContract.TogglePage funny =
                GdxSettingsContract.AUDIO_PAGES.get(3);
        assertEquals("sonidos_chorra", funny.options().get(0).key());
        assertFalse(GdxSettingsContract.isChildOption(funny,
                funny.options().get(0)));
        assertTrue(funny.options().stream().skip(1).allMatch(option ->
                GdxSettingsContract.isChildOption(funny, option)));

    }

    @Test
    void nestedGroupsPreserveExplicitParentageAndSpacing() {
        GdxSettingsContract.TogglePage page =
                new GdxSettingsContract.TogglePage("JOKES", java.util.List.of(
                        new GdxSettingsContract.ToggleOption("root", "ROOT",
                                true, GdxSettingsContract.Gate.NONE, false,
                                null),
                        new GdxSettingsContract.ToggleOption("category",
                                "LOSER", true,
                                GdxSettingsContract.Gate.NONE, false,
                                "root"),
                        new GdxSettingsContract.ToggleOption("entry",
                                "LOSER-1", true,
                                GdxSettingsContract.Gate.NONE, false,
                                "category"),
                        new GdxSettingsContract.ToggleOption("standalone",
                                "OTHER", true,
                                GdxSettingsContract.Gate.NONE, false,
                                null)));

        assertEquals(java.util.List.of(
                new GdxSettingsContract.RowGroup(0, 2, 0),
                new GdxSettingsContract.RowGroup(1, 2, 1)),
                GdxSettingsContract.toggleGroups(page, index -> index));
        assertEquals(java.util.Set.of(1, 3),
                GdxSettingsContract.groupGapsBefore(
                        GdxSettingsContract.toggleGroups(page,
                                index -> index), 4));
    }

    @Test
    void visualGroupsDropOnlyTheRedundantWholeSubpageFrame() {
        assertEquals(java.util.List.of(), GdxSettingsContract.visualGroups(
                java.util.List.of(
                        new GdxSettingsContract.RowGroup(0, 4, 0)), 5));
        assertEquals(java.util.List.of(
                new GdxSettingsContract.RowGroup(1, 3, 0)),
                GdxSettingsContract.visualGroups(java.util.List.of(
                        new GdxSettingsContract.RowGroup(0, 4, 0),
                        new GdxSettingsContract.RowGroup(1, 3, 1)), 5));
    }

    @Test
    void controlsExposeTheirParentGroupWithoutIndentingChildren() {
        assertEquals(java.util.List.of(
                new GdxSettingsContract.RowGroup(0, 3, 0)),
                GdxSettingsContract.controlGroups());
    }

    @Test
    void graphicsWindowModeOwnsAutomaticFullscreenInEverySettingsSurface() {
        assertEquals(java.util.List.of(
                new GdxSettingsContract.RowGroup(0, 1, 0)),
                GdxSettingsContract.appearanceGraphicsGroups());
    }

    @Test
    void gameParentBlocksAreSharedAcrossEverySettingsEntryPoint() {
        assertEquals(java.util.List.of(
                new GdxSettingsContract.RowGroup(0, 2, 0),
                new GdxSettingsContract.RowGroup(3, 7, 0)),
                GdxSettingsContract.gameGroups(0));
        assertEquals(java.util.List.of(
                new GdxSettingsContract.RowGroup(2, 3, 0),
                new GdxSettingsContract.RowGroup(4, 7, 0)),
                GdxSettingsContract.gameGroups(1));
        assertEquals(java.util.List.of(
                new GdxSettingsContract.RowGroup(2, 4, 0)),
                GdxSettingsContract.gameGroups(2));
        assertEquals(java.util.List.of(
                new GdxSettingsContract.RowGroup(0, 1, 0),
                new GdxSettingsContract.RowGroup(2, 3, 0)),
                GdxSettingsContract.gameGroups(3));
    }

    @Test
    void bothSettingsEntryPointsUseTheSameSubpageCatalogueBuilder() {
        assertEquals(java.util.List.of("MESA", "ANIMACIONES Y EFECTOS",
                "GRÁFICOS"),
                GdxSettingsContract.subpageLabels(
                        GdxSettingsContract.Section.APPEARANCE,
                        java.util.List.of(), 0, 5));
        assertEquals(java.util.List.of("CIEGAS", "COMPRA", "BOTS", "REGLAS"),
                GdxSettingsContract.subpageLabels(
                        GdxSettingsContract.Section.GAME,
                        java.util.List.of("CIEGAS", "COMPRA", "BOTS", "REGLAS"),
                        0, 5));
        assertEquals(java.util.List.of(),
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
        assertEquals(java.util.List.of("TABLE", "ANIMATIONS AND EFFECTS",
                "GRAPHICS"),
                GdxSettingsContract.subpageLabels(
                        GdxSettingsContract.Section.APPEARANCE,
                        java.util.List.of(), 0, 5, text));
        assertEquals(java.util.List.of("BLINDS", "BUY-IN", "BOTS", "RULES"),
                GdxSettingsContract.subpageLabels(
                        GdxSettingsContract.Section.GAME,
                        java.util.List.of("CIEGAS", "COMPRA", "BOTS", "REGLAS"),
                        0, 5, text));
        assertEquals("USE SOUND EFFECTS",
                GdxSettingsContract.AUDIO_PAGES.get(2).options().get(0)
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
                "sonido_tapete", "sonido_visor", "sonido_zoom",
                "sonido_arranque", "sonidos_chorra", "sonido_inicio",
                "musica_about", "musica_stats", "musica_gestor_bots",
                "musica_recuperacion",
                "tts_server",
                "voice_messages", "audio_mic_enabled",
                 GdxSettingsContract.AUDIO_MIC_AUTO_GAIN_KEY,
                "audio_block_voice_messages", "audio_play_own_voice",
                "audio_block_tts_local")));
        assertEquals(java.util.List.of("GENERAL", "MÚSICA", "EFECTOS",
                "SONIDOS DE COÑA", "NOTAS Y CHAT DE VOZ"),
                GdxSettingsContract.AUDIO_PAGES.stream()
                        .map(GdxSettingsContract.TogglePage::title).toList());
        assertFalse(keys.contains("sonido_vista_compacta"));
        assertEquals("sonido_efectos",
                GdxSettingsContract.AUDIO_PAGES.get(2).options().get(0).key());
        assertFalse(GdxSettingsContract.AUDIO_PAGES.get(2).options().stream()
                .anyMatch(option -> "sonidos_chorra".equals(option.key())));
        assertEquals("sonidos_chorra",
                GdxSettingsContract.AUDIO_PAGES.get(3).options().get(0).key());
        assertEquals(13,
                GdxSettingsContract.AUDIO_PAGES.get(3).options().size());
        assertTrue(keys.contains("sonido_entrar_sala"));
    }

    @Test
    void funnySoundChildrenFollowBothAudioParents() {
        GdxSettingsContract.TogglePage page =
                GdxSettingsContract.AUDIO_PAGES.get(3);
        GdxSettingsContract.ToggleOption parent = page.options().get(0);
        GdxSettingsContract.ToggleOption child = page.options().get(1);
        Properties properties = new Properties();

        assertTrue(GdxSettingsContract.enabled(parent, properties, true));
        assertFalse(GdxSettingsContract.enabled(child, properties, true));
        properties.setProperty("sonidos_chorra", "true");
        assertTrue(GdxSettingsContract.enabled(child, properties, true));
        properties.setProperty("sonido_efectos", "false");
        assertFalse(GdxSettingsContract.enabled(parent, properties, true));
        assertFalse(GdxSettingsContract.enabled(child, properties, true));
    }

    @Test
    void audioScrollRowsIncludeVoiceActionsAndDeviceSelectors() {
        GdxSettingsContract.TogglePage voice =
                GdxSettingsContract.AUDIO_PAGES.stream()
                        .filter(page -> GdxSettingsContract.hasVoiceRetention(
                                page))
                        .findFirst().orElseThrow();
        GdxSettingsContract.TogglePage devices =
                GdxSettingsContract.AUDIO_PAGES.stream()
                        .filter(page -> GdxSettingsContract.hasAudioDevices(
                                page))
                        .findFirst().orElseThrow();

        assertEquals(7, GdxSettingsContract.audioRowCount(voice));
        assertEquals(3, GdxSettingsContract.voiceRetentionRow(voice));
        assertEquals(4, GdxSettingsContract.voiceActionsRow(voice));
        assertEquals(5, GdxSettingsContract.audioOptionRow(voice, 3));
        assertEquals(6, GdxSettingsContract.audioOptionRow(voice, 4));
        assertEquals(java.util.List.of(
                new GdxSettingsContract.RowGroup(0, 4, 0),
                new GdxSettingsContract.RowGroup(5, 6, 0)),
                GdxSettingsContract.audioGroups(voice));

        assertEquals(6, GdxSettingsContract.audioRowCount(devices));
        assertEquals(0, GdxSettingsContract.audioLeadingRows(devices));
        assertEquals(2, GdxSettingsContract.audioOutputDeviceRow(devices));
        assertEquals(4, GdxSettingsContract.audioCaptureDeviceRow(devices));
        assertEquals(0, GdxSettingsContract.audioOptionRow(devices, 0));
        assertEquals(3, GdxSettingsContract.audioOptionRow(devices, 1));
        assertEquals(5, GdxSettingsContract.audioOptionRow(devices, 2));
        assertEquals(java.util.List.of(
                new GdxSettingsContract.RowGroup(3, 5, 0)),
                GdxSettingsContract.audioGroups(devices));
        assertEquals(GdxSettingsContract.AUDIO_MIC_AUTO_GAIN_KEY,
                devices.options().get(2).key());

        GdxSettingsContract.TogglePage general =
                GdxSettingsContract.AUDIO_PAGES.get(0);
        assertTrue(GdxSettingsContract.hasMasterVolume(general));
        assertEquals(6, GdxSettingsContract.audioRowCount(general));
        assertEquals(0, GdxSettingsContract.audioOptionRow(general, 0));
        assertEquals(1, GdxSettingsContract.masterVolumeRow(general));
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
    void audioPreviewCatalogKeepsSwingResourcesAndTimeLimits() {
        Properties mutedProperties = new Properties();
        mutedProperties.setProperty("sonido_efectos", "true");
        GdxSettingsContract.ToggleOption bet =
                GdxSettingsContract.AUDIO_PAGES.stream()
                .flatMap(page -> page.options().stream())
                .filter(option -> option.key().equals("sonido_apostar"))
                .findFirst().orElseThrow();
        GdxSettingsContract.ToggleOption stacks =
                GdxSettingsContract.AUDIO_PAGES.stream()
                .flatMap(page -> page.options().stream())
                .filter(option -> option.key().equals(
                        "sonido_carga_stacks"))
                .findFirst().orElseThrow();

        assertEquals(new GdxSettingsContract.AudioPreview(
                "misc/bet.wav", 1f, 10f),
                GdxSettingsContract.audioPreview(bet, "es"));
        assertTrue(GdxSettingsContract.enabled(bet, mutedProperties, false),
                "La prueba de sonido debe seguir disponible con SONIDO apagado");
        GdxSettingsContract.ToggleOption botManager =
                GdxSettingsContract.AUDIO_PAGES.stream()
                .flatMap(page -> page.options().stream())
                .filter(option -> option.key().equals("musica_gestor_bots"))
                .findFirst().orElseThrow();
        assertEquals(new GdxSettingsContract.AudioPreview(
                "misc/bots_manager.mp3", 0.65f, 10f),
                GdxSettingsContract.audioPreview(botManager, "es"));
        assertTrue(GdxSettingsContract.enabled(botManager, mutedProperties,
                false),
                "La prueba del gestor debe funcionar con SONIDO apagado");
        GdxSettingsContract.ToggleOption recovery =
                GdxSettingsContract.AUDIO_PAGES.stream()
                .flatMap(page -> page.options().stream())
                .filter(option -> option.key().equals(
                        "musica_recuperacion"))
                .findFirst().orElseThrow();
        assertEquals(new GdxSettingsContract.AudioPreview(
                "misc/recovering.mp3", 0.4f, 10f),
                GdxSettingsContract.audioPreview(recovery, "en"));
        assertEquals(1f,
                GdxSettingsContract.audioPreview(stacks, "es")
                        .limitSeconds());
        for (GdxSettingsContract.ToggleOption option
                : GdxSettingsContract.AUDIO_PAGES.stream()
                        .flatMap(page -> page.options().stream()).toList()) {
            GdxSettingsContract.AudioPreview preview =
                    GdxSettingsContract.audioPreview(option, "es");
            if (preview == null) continue;
            boolean bundled = getClass().getResource(
                    "/sounds/" + preview.resource()) != null
                    || getClass().getResource(
                            "/cinematics/" + preview.resource()) != null;
            assertTrue(bundled,
                    option.key() + " -> " + preview.resource());
        }
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
        assertEquals(List.of(7, 15, 30, 90, 0),
                GdxSettingsContract.voiceRetentionOptions());
        assertEquals(3, GdxSettingsContract.voiceRetentionIndex(properties));
        assertEquals("90 DÍAS",
                GdxSettingsContract.voiceRetentionLabel(properties));
        GdxSettingsContract.selectVoiceRetention(properties, 1);
        assertEquals("15 DÍAS",
                GdxSettingsContract.voiceRetentionLabel(properties));
        GdxSettingsContract.selectVoiceRetention(properties, -1);
        assertEquals("15 DÍAS",
                GdxSettingsContract.voiceRetentionLabel(properties));
        GdxSettingsContract.selectVoiceRetention(properties, 3);
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
    void audioFamiliesRemainEditableWhileMasterSoundIsMuted() {
        Properties properties = new Properties();
        GdxSettingsContract.ToggleOption musicMaster =
                GdxSettingsContract.AUDIO_PAGES.get(1).options().get(0);
        GdxSettingsContract.ToggleOption effectsMaster =
                GdxSettingsContract.AUDIO_PAGES.get(2).options().get(0);
        GdxSettingsContract.ToggleOption music =
                GdxSettingsContract.AUDIO_PAGES.get(1).options().get(1);
        GdxSettingsContract.ToggleOption effect =
                GdxSettingsContract.AUDIO_PAGES.get(2).options().get(2);

        assertTrue(GdxSettingsContract.enabled(musicMaster, properties, false));
        assertTrue(GdxSettingsContract.enabled(effectsMaster, properties,
                false));
        assertTrue(GdxSettingsContract.enabled(music, properties, false));
        assertTrue(GdxSettingsContract.enabled(effect, properties, false));
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
        java.util.List<String> keys = java.util.stream.Stream.concat(
                java.util.stream.Stream.of(
                        GdxSettingsContract.appearanceTablePage()),
                GdxSettingsContract.APPEARANCE_PAGES.stream())
                .flatMap(page -> page.options().stream())
                .map(GdxSettingsContract.ToggleOption::key)
                .toList();

        assertEquals(keys.size(), new java.util.HashSet<>(keys).size());
        assertTrue(keys.containsAll(java.util.List.of(
                "cinematicas_allin", "cinematicas_gameover",
                "animacion_barajado", "animacion_barajado_precarga",
                "animacion_reparto",
                "animacion_destape", "animacion_ciegas_dealer",
                "animacion_apuestas", "animacion_contadores",
                "animacion_entrada_asientos", "animacion_swap",
                "animacion_contador_final", "animacion_fuego_allin",
                "animacion_efecto_ganador",
                "mostrar_coste_igualar",
                "chat_images_ingame", "chat_game_notifications",
                "resaltar_jugada_showdown",
                "resaltar_avatares")));
        assertFalse(keys.contains("auto_fullscreen"));
        assertTrue(GdxSettingsContract.PREFERENCE_KEYS.contains(
                "auto_fullscreen"));
        assertFalse(keys.contains("cinematicas"));
        assertEquals(8,
                GdxSettingsContract.APPEARANCE_GRAPHICS_ROW_COUNT);
        assertEquals(java.util.List.of("ANIMACIONES Y EFECTOS"),
                GdxSettingsContract.APPEARANCE_PAGES.stream()
                        .map(GdxSettingsContract.TogglePage::title).toList());
        assertFalse(keys.contains("auto_zoom"));
        assertTrue(keys.contains("animaciones"));
        assertFalse(keys.contains("cinematicas_accion"));
        assertFalse(keys.contains("animacion_cascada_overlay"));
        assertFalse(keys.contains("animacion_downgrade"));
        assertTrue(GdxSettingsContract.PREFERENCE_KEYS.containsAll(
                java.util.List.of("gdx_vsync", "gdx_fps_limit",
                        "gdx_show_fps", "gdx_show_cpu_memory",
                        "gdx_msaa_samples",
                        "gdx_card_smaa_quality")));
    }

    @Test
    void animationsCombinesSwitchesAndChoicesInOneScrollablePage() {
        GdxSettingsContract.TogglePage page =
                GdxSettingsContract.APPEARANCE_PAGES.get(0);

        assertTrue(GdxSettingsContract.hasAppearanceAnimationOptions(page));
        assertEquals(20, page.options().size());
        assertEquals(7, GdxAppearanceOptions.ANIMATION_CHOICES.size());
        assertEquals(27, GdxSettingsContract.appearanceRowCount(page));
        assertEquals(java.util.List.of(
                "animaciones",
                "mostrar_videos_poker",
                "abrir_video_poker_al_arrancar",
                "tipo_videos_poker",
                "efecto_halos",
                "efecto_desenfoque",
                "efecto_relieve",
                "cinematicas_allin",
                "cinematicas_gameover",
                "animacion_barajado",
                "animacion_barajado_precarga",
                "animacion_reparto",
                "reparto_velocidad",
                "animacion_destape",
                "card_flip_duration",
                "card_flip_direction",
                "card_flip_zoom",
                "animacion_ciegas_dealer",
                "animacion_contadores",
                "animacion_entrada_asientos",
                "animacion_swap",
                "swap_velocidad",
                "swap_arco",
                "animacion_contador_final",
                "animacion_fuego_allin",
                "animacion_efecto_ganador",
                "animacion_apuestas"),
                GdxSettingsContract.appearanceRows(page).stream()
                        .map(GdxSettingsContract.AppearanceAnimationRow::key)
                        .toList());
        assertTrue(GdxSettingsContract.visualGroups(
                GdxSettingsContract.appearanceGroups(page),
                GdxSettingsContract.appearanceRowCount(page)).stream()
                .anyMatch(group -> group.parentRow() == 1
                        && group.lastRow() == 3),
                "video startup and type must share the poker video group");
    }

    @Test
    void videoStartupPreferenceDefaultsOnAndRequiresVideosAndAnimations() {
        var option = GdxSettingsContract.APPEARANCE_PAGES.get(0).options().stream()
                .filter(row -> GdxSettingsContract.POKER_VIDEO_AUTO_OPEN_KEY.equals(row.key()))
                .findFirst().orElseThrow();
        Properties values = new Properties();
        assertTrue(option.fallback());
        assertTrue(GdxSettingsContract.PREFERENCE_KEYS.contains(option.key()));
        assertTrue(GdxSettingsContract.enabled(option, values, true));
        values.setProperty(GdxSettingsContract.POKER_VIDEOS_KEY, "false");
        assertFalse(GdxSettingsContract.enabled(option, values, true));
        values.setProperty(GdxSettingsContract.POKER_VIDEOS_KEY, "true");
        values.setProperty("animaciones", "false");
        assertFalse(GdxSettingsContract.enabled(option, values, true));
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
    void cinematicsAndAnimationsShareTheAnimationMaster() {
        Properties properties = new Properties();
        GdxSettingsContract.ToggleOption cinematic =
                GdxSettingsContract.APPEARANCE_PAGES.get(0).options().stream()
                        .filter(option -> "cinematicas_allin".equals(
                                option.key()))
                        .findFirst().orElseThrow();
        GdxSettingsContract.ToggleOption deal =
                GdxSettingsContract.APPEARANCE_PAGES.get(0).options().stream()
                        .filter(option -> "animacion_reparto".equals(
                                option.key()))
                        .findFirst().orElseThrow();

        assertTrue(GdxSettingsContract.enabled(cinematic, properties, true));
        assertTrue(GdxSettingsContract.enabled(deal, properties, true));
        properties.setProperty("cinematicas", "false");
        assertTrue(GdxSettingsContract.enabled(cinematic, properties, true),
                "the retired cinematics master must not gate visible rows");
        assertTrue(GdxSettingsContract.enabled(deal, properties, true));
        properties.setProperty("animaciones", "false");
        assertFalse(GdxSettingsContract.enabled(cinematic, properties, true));
        assertFalse(GdxSettingsContract.enabled(deal, properties, true));
    }

    @Test
    void shufflePreloadRequiresOnlyItsShuffleAnimationParent() {
        GdxSettingsContract.ToggleOption preload =
                GdxSettingsContract.APPEARANCE_PAGES.get(0).options().stream()
                        .filter(option -> "animacion_barajado_precarga".equals(
                                option.key()))
                        .findFirst().orElseThrow();
        Properties properties = new Properties();

        assertTrue(preload.fallback());
        assertTrue(GdxSettingsContract.enabled(preload, properties, true));
        properties.setProperty("animacion_barajado", "false");
        assertFalse(GdxSettingsContract.enabled(preload, properties, true));
        properties.setProperty("animacion_barajado", "true");
        properties.setProperty("animaciones", "false");
        assertFalse(GdxSettingsContract.enabled(preload, properties, true));
    }

    @Test
    void audioDefaultsMatchSwingAndPreserveServerRulesOnClients() {
        Properties properties = new Properties();
        properties.setProperty("master_volume", "0.15");
        properties.setProperty("sonidos", "false");
        properties.setProperty("sonidos_chorra", "true");
        properties.setProperty("audio_mic_enabled", "true");
        properties.setProperty(GdxSettingsContract.AUDIO_MIC_AUTO_GAIN_KEY,
                "false");
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
        assertEquals("true", properties.getProperty(
                GdxSettingsContract.AUDIO_MIC_AUTO_GAIN_KEY));
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
    void microphoneDisablesItsChildrenWithoutLosingTheirStoredValues() {
        GdxSettingsContract.TogglePage page =
                GdxSettingsContract.AUDIO_PAGES.get(0);
        GdxSettingsContract.ToggleOption microphone = page.options().stream()
                .filter(option -> "audio_mic_enabled".equals(option.key()))
                .findFirst().orElseThrow();
        GdxSettingsContract.ToggleOption autoGain = page.options().stream()
                .filter(option -> GdxSettingsContract.AUDIO_MIC_AUTO_GAIN_KEY
                        .equals(option.key()))
                .findFirst().orElseThrow();
        Properties properties = new Properties();
        properties.setProperty("audio_mic_enabled", "false");
        properties.setProperty(GdxSettingsContract.AUDIO_MIC_AUTO_GAIN_KEY,
                "true");

        assertTrue(GdxSettingsContract.enabled(microphone, properties, true));
        assertFalse(GdxSettingsContract.microphoneEnabled(properties));
        assertEquals("audio_mic_enabled", autoGain.parentKey());
        assertFalse(GdxSettingsContract.enabled(autoGain, properties, true));
        assertTrue(GdxSettingsContract.displayedValue(autoGain, properties,
                true), "disabling the parent must preserve the child value");

        properties.setProperty("audio_mic_enabled", "true");
        assertTrue(GdxSettingsContract.microphoneEnabled(properties));
        assertTrue(GdxSettingsContract.enabled(autoGain, properties, true));
        assertTrue(GdxSettingsContract.displayedValue(autoGain, properties,
                true));
        assertEquals(java.util.List.of(
                new GdxSettingsContract.RowGroup(3, 5, 0)),
                GdxSettingsContract.audioGroups(page));
    }

    @Test
    void voiceAndTtsParentsDisableTheirLocalChildrenWithoutLosingValues() {
        GdxSettingsContract.TogglePage page =
                GdxSettingsContract.AUDIO_PAGES.stream()
                        .filter(GdxSettingsContract::hasVoiceRetention)
                        .findFirst().orElseThrow();
        java.util.Map<String, GdxSettingsContract.ToggleOption> options =
                page.options().stream().collect(
                        java.util.stream.Collectors.toMap(
                                GdxSettingsContract.ToggleOption::key,
                                option -> option));
        Properties properties = new Properties();
        properties.setProperty("voice_messages", "false");
        properties.setProperty("audio_block_voice_messages", "false");
        properties.setProperty("audio_play_own_voice", "true");
        properties.setProperty("tts_server", "false");
        properties.setProperty("audio_block_tts_local", "false");

        assertEquals("voice_messages",
                options.get("audio_block_voice_messages").parentKey());
        assertEquals("voice_messages",
                options.get("audio_play_own_voice").parentKey());
        assertEquals("tts_server",
                options.get("audio_block_tts_local").parentKey());
        assertFalse(GdxSettingsContract.enabled(
                options.get("audio_block_voice_messages"), properties, true));
        assertFalse(GdxSettingsContract.enabled(
                options.get("audio_play_own_voice"), properties, true));
        assertFalse(GdxSettingsContract.enabled(
                options.get("audio_block_tts_local"), properties, true));
        assertTrue(GdxSettingsContract.displayedValue(
                options.get("audio_block_voice_messages"), properties, true));
        assertTrue(GdxSettingsContract.displayedValue(
                options.get("audio_play_own_voice"), properties, true));
        assertTrue(GdxSettingsContract.displayedValue(
                options.get("audio_block_tts_local"), properties, true));
    }

    @Test
    void blindLabelsAreIdenticalAcrossSettingsSurfaces() {
        assertEquals("0.10", GdxSettingsContract.blindAmountLabel(0.1d));
        assertEquals("0.20", GdxSettingsContract.blindAmountLabel(0.2d));
        assertEquals("1", GdxSettingsContract.blindAmountLabel(1d));
        assertEquals("1.5", GdxSettingsContract.blindAmountLabel(1.5d));
        assertEquals("0.10 / 0.20",
                GdxSettingsContract.blindLevelLabel(0.1d, 0.2d));
    }

    @Test
    void audioRestoreAndDirtyStateAreScopedToTheVisibleSubsection() {
        Properties properties = new Properties();
        properties.setProperty("master_volume", "0.25");
        properties.setProperty("musica_about", "false");
        properties.setProperty("musica_gestor_bots", "false");
        properties.setProperty("musica_recuperacion", "false");
        properties.setProperty("sonido_apostar", "false");

        GdxSettingsContract.restoreAudioPageDefaults(properties, 1,
                true, true);

        assertEquals("0.25", properties.getProperty("master_volume"));
        assertEquals("true", properties.getProperty("musica_about"));
        assertEquals("true", properties.getProperty("musica_gestor_bots"));
        assertEquals("true", properties.getProperty("musica_recuperacion"));
        assertEquals("false", properties.getProperty("sonido_apostar"));
        assertTrue(GdxSettingsContract.audioPageAtDefaults(properties, 1,
                true, true, true));
        assertFalse(GdxSettingsContract.audioPageAtDefaults(properties, 0,
                true, true, true));
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
        properties.setProperty("card_flip_direction", "mixed");
        properties.setProperty("gdx_window_mode", "windowed");
        properties.setProperty("gdx_msaa_samples", "0");
        properties.setProperty("gdx_card_smaa_quality", "0");
        properties.setProperty("gdx_fps_limit", "240");
        properties.setProperty("gdx_vsync", "false");
        properties.setProperty("gdx_show_fps", "false");

        GdxSettingsContract.restoreAppearanceDefaults(properties, false);

        assertEquals("goliat", properties.getProperty("baraja"));
        assertEquals("default", properties.getProperty("trasera"));
        assertEquals("verde", properties.getProperty("color_tapete"));
        assertEquals("35", properties.getProperty("nivel_luz"));
        assertEquals("false", properties.getProperty("gdx_show_fps"));
        assertEquals("true", properties.getProperty("animaciones"));
        assertEquals("true", properties.getProperty(
                "animacion_fuego_allin"));
        assertEquals("true", properties.getProperty(
                "animacion_barajado_precarga"));
        assertEquals("true", properties.getProperty(
                "animacion_efecto_ganador"));
        assertEquals("620", properties.getProperty("card_flip_duration"));
        assertEquals("right", properties.getProperty("card_flip_direction"));
        assertEquals("windowed", properties.getProperty("gdx_window_mode"));
        assertEquals("0", properties.getProperty("gdx_msaa_samples"));
        assertEquals("1",
                properties.getProperty("gdx_card_smaa_quality"));
        assertEquals("120", properties.getProperty("gdx_fps_limit"));
        assertEquals("true", properties.getProperty("gdx_vsync"));

        GdxSettingsContract.restoreAppearanceDefaults(properties, true);
        assertEquals("borderless", properties.getProperty("gdx_window_mode"));
        assertEquals("4", properties.getProperty("gdx_msaa_samples"));
        assertEquals("120", properties.getProperty("gdx_fps_limit"));
        assertEquals("true", properties.getProperty("gdx_vsync"));
    }

    @Test
    void appearanceRestoreAndDirtyStateAreScopedToTheVisibleSubsection() {
        Properties properties = new Properties();
        properties.setProperty("baraja", "goliat4");
        properties.setProperty("gdx_fps_limit", "240");
        properties.setProperty("animaciones", "false");

        GdxSettingsContract.restoreAppearancePageDefaults(properties, 2,
                true);

        assertEquals("goliat4", properties.getProperty("baraja"));
        assertEquals("120", properties.getProperty("gdx_fps_limit"));
        assertEquals("false", properties.getProperty("animaciones"));
        assertTrue(GdxSettingsContract.appearancePageAtDefaults(properties,
                2, true));
        assertFalse(GdxSettingsContract.appearancePageAtDefaults(properties,
                0, true));
        assertFalse(GdxSettingsContract.appearancePageAtDefaults(properties,
                1, true));
    }

    @Test
    void invertedAudioOptionsUseTheirDisplayedFactoryValue() {
        GdxSettingsContract.ToggleOption inverted =
                GdxSettingsContract.AUDIO_PAGES.stream()
                        .flatMap(page -> page.options().stream())
                        .filter(option -> "audio_block_tts_local".equals(
                                option.key()))
                        .findFirst().orElseThrow();

        assertTrue(GdxSettingsContract.displayedFallback(inverted));
        assertTrue(GdxSettingsContract.displayedValue(inverted,
                new Properties(), true));
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

    @Test
    void defaultMarkerOnlyDecoratesFactoryValues() {
        assertEquals("goliat *",
                GdxSettingsContract.markDefault("goliat", true));
        assertEquals("rojo",
                GdxSettingsContract.markDefault("rojo", false));
    }

    private static Properties properties(String key, String value) {
        Properties properties = new Properties();
        properties.setProperty(key, value);
        return properties;
    }
}
