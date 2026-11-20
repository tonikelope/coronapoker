package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class GdxSettingsTooltipsTest {

    @Test
    void explainedDropdownOptionsAreTranslatedInBothLanguages() {
        var spanish = new GdxGameText("es");
        var english = new GdxGameText("en");
        Map<String, Integer> selectors = Map.ofEntries(
                Map.entry("BOT_DIFFICULTY", 3), Map.entry("CUSTOM_BOT_DIFFICULTY", 3),
                Map.entry("CUSTOM_BOT_PERSONALITY", 5), Map.entry("ANTE_MODE", 4),
                Map.entry("REBUY_CAP", 2),
                Map.entry("RABBIT", 4), Map.entry("WINDOW_MODE", 2));
        selectors.forEach((selector, count) -> {
            for (int option = 0; option < count; option++) {
                String key = GdxSettingsTooltips.optionKey(selector, option);
                assertNotNull(key, selector);
                assertNotEquals(key, spanish.translate(key));
                assertNotEquals(key, english.translate(key));
                assertNotEquals(spanish.translate(key), english.translate(key));
            }
        });
        for (String selector : List.of("BLIND_UNIT", "FPS_LIMIT", "MSAA",
                "CARD_RENDERING", "APPEARANCE_CHOICE", "SERVER_HISTORY")) {
            assertNull(GdxSettingsTooltips.optionKey(selector, 0), selector);
        }
        assertNull(GdxSettingsTooltips.optionKey("WINDOW_MODE", GdxWindowMode.WINDOWED.ordinal()));
    }

    @Test
    void everyGameSettingsRowHasSpecificTextInBothLanguages() {
        List<Integer> rowCounts = List.of(
                GdxSettingsContract.BLIND_ROW_COUNT, 8, 6, 9);
        for (int page = 0; page < rowCounts.size(); page++) {
            List<String> rows = GdxSettingsTooltips.gameRowKeys(page);
            assertEquals(rowCounts.get(page).intValue(), rows.size());
            for (String row : rows) {
                String key = GdxSettingsTooltips.descriptionKey(row);
                assertNotNull(key, row);
                for (String language : List.of("en", "es")) {
                    assertNotEquals(key, new GdxGameText(language).translate(key),
                            row + " " + language);
                }
            }
        }
        assertEquals("ante", GdxSettingsTooltips.gameRowKeys(0).get(
                GdxSettingsContract.BLIND_ANTE_ROW));
        assertEquals("ante_mode", GdxSettingsTooltips.gameRowKeys(0).get(
                GdxSettingsContract.BLIND_ANTE_MODE_ROW));
    }

    @Test
    void anteModeExplainsEveryPayerEvenWhenTheSettingIsLocked() {
        for (String language : List.of("en", "es")) {
            GdxGameText text = new GdxGameText(language);
            String description = GdxSettingsTooltips.content(
                    "ante_mode", text).text();
            for (String mode : List.of("BBA", "BA", "SBA")) {
                assertTrue(description.matches(".*\\b" + mode + "\\b.*"),
                        language + " " + mode);
            }
            assertTrue(description.contains("10"));
            String lockKey = "gdx.settings.game.server_only";
            assertEquals(description + "\n" + text.translate(lockKey),
                    GdxSettingsTooltips.withLock(description, lockKey, text));
        }
    }

    @Test
    void graphicsTooltipsExplainTheSettingAndThenItsImpact() {
        GdxSettingsTooltips.Content spanish = GdxSettingsTooltips.content(
                "gdx_msaa_samples", new GdxGameText("es"));
        assertTrue(spanish.text().contains("Suaviza"));
        assertTrue(spanish.text().contains("Impacto estimado en el rendimiento: ALTO"));
        assertEquals(GdxSettingsContract.PerformanceImpact.HIGH,
                spanish.performanceImpact());

        GdxSettingsTooltips.Content english = GdxSettingsTooltips.content(
                "gdx_vsync", new GdxGameText("en"));
        assertTrue(english.text().contains("Synchronizes frames"));
        assertFalse(english.text().contains("Estimated performance impact"));

        GdxSettingsTooltips.Content monitorRefresh = GdxSettingsTooltips.content(
                "gdx_fps_limit", new GdxGameText("en"));
        assertTrue(monitorRefresh.text().contains("follows your display"));
        assertTrue(monitorRefresh.text().contains("Estimated performance impact"));
        assertEquals(GdxSettingsContract.PerformanceImpact.HIGH,
                monitorRefresh.performanceImpact());

        var shadows = GdxSettingsTooltips.content(
                GdxSettingsContract.FLYING_CHIP_SHADOW_QUALITY_KEY, new GdxGameText("es"));
        assertTrue(shadows.text().contains("fichas en vuelo"));
        assertEquals(GdxSettingsContract.PerformanceImpact.HIGH, shadows.performanceImpact());
    }

    @Test
    void pokerVideoTooltipExplainsReopeningDuringPlayInBothLanguages() {
        String key = GdxSettingsContract.POKER_VIDEOS_KEY;
        assertTrue(GdxSettingsTooltips.content(key,
                new GdxGameText("es")).text().contains(
                        "puedes volver a abrirlo desde su icono"));
        assertTrue(GdxSettingsTooltips.content(key,
                new GdxGameText("en")).text().contains(
                        "can be reopened from its icon"));
    }

    @Test
    void everyVisibleAppearanceOptionHasARealDescription() {
        Stream<String> appearance = GdxSettingsContract.APPEARANCE_PAGES
                .stream().flatMap(page -> page.options().stream())
                .map(GdxSettingsContract.ToggleOption::key);
        Stream<String> table = GdxSettingsContract.appearanceTablePage()
                .options().stream().map(GdxSettingsContract.ToggleOption::key);
        Stream<String> choices = GdxAppearanceOptions.ANIMATION_CHOICES
                .stream().map(GdxAppearanceOptions.Choice::key);
        Stream.concat(Stream.concat(appearance, table), choices)
                .forEach(key -> assertNotNull(GdxSettingsTooltips.descriptionKey(key), key));
    }

    @Test
    void everyAudioSettingHasSpecificHelpInSpanishAndEnglish() {
        var spanish = new GdxGameText("es");
        var english = new GdxGameText("en");
        GdxSettingsContract.AUDIO_PAGES.stream()
                .flatMap(page -> page.options().stream())
                .map(GdxSettingsContract.ToggleOption::key)
                .forEach(setting -> {
                    String key = GdxSettingsTooltips.descriptionKey(setting);
                    assertNotNull(key, setting);
                    assertNotEquals("gdx.settings.tooltip.audio_event", key, setting);
                    assertNotEquals("tooltip.cfg.music_surface", key, setting);
                    assertNotEquals(key, spanish.translate(key), setting);
                    assertNotEquals(key, english.translate(key), setting);
                });
    }

    @Test
    void tableViewIsTheFirstSettingAndEveryTableRowHasLocalizedHelp() {
        assertEquals(GdxAppearanceOptions.TABLE_VIEW,
                GdxSettingsContract.appearanceTableChoice(0));
        var spanish = new GdxGameText("es");
        var english = new GdxGameText("en");
        for (int row = 0; row < GdxSettingsContract.appearanceTableRowCount(); row++) {
            String setting = GdxSettingsContract.appearanceTableSettingKey(row);
            String key = GdxSettingsTooltips.descriptionKey(setting);
            assertNotNull(key, setting);
            assertFalse(Set.of("generic", "animation", "cinematic", "information", "audio_event")
                    .contains(key.substring(key.lastIndexOf('.') + 1)), setting);
            assertNotEquals(key, spanish.translate(key), setting);
            assertNotEquals(key, english.translate(key), setting);
        }
        String viewHelp = GdxSettingsTooltips.content(
                GdxSettingsContract.appearanceTableSettingKey(0), spanish).text();
        assertTrue(viewHelp.contains("2D") && viewHelp.contains("3D")
                && viewHelp.contains("pulsada V") && viewHelp.contains("arrastra"));
        assertTrue(GdxSettingsTooltips.content("unmapped_setting", spanish).text().isEmpty());
    }

    @Test
    void allControlAndGraphicsRowsHaveDedicatedDescriptions() {
        List<String> keys = List.of(
                "gdx_window_mode", "auto_fullscreen", "gdx_vsync",
                "gdx_fps_limit", "gdx_msaa_samples",
                "gdx_show_fps",
                "gdx_show_cpu_memory", "gpu_renderer", "auto_action_buttons",
                "auto_call_enabled", "auto_action_persist",
                "modo_auto_confirm", "confirmar_todo", "auto_rebuy",
                "shortcut");
        keys.forEach(key -> assertNotNull(GdxSettingsTooltips.descriptionKey(key), key));
    }

    @Test
    void everyVisibleAnimationSettingExplainsItsOwnEffect() {
        Set<String> genericKeys = Set.of(
                "gdx.settings.tooltip.generic",
                "gdx.settings.tooltip.animation",
                "gdx.settings.tooltip.cinematic",
                "gdx.settings.tooltip.information");
        Stream<String> toggles = GdxSettingsContract.APPEARANCE_PAGES
                .stream().flatMap(page -> page.options().stream())
                .map(GdxSettingsContract.ToggleOption::key);
        Stream<String> choices = GdxAppearanceOptions.ANIMATION_CHOICES
                .stream().map(GdxAppearanceOptions.Choice::key);
        Stream.concat(toggles, choices).forEach(key -> {
            String descriptionKey = GdxSettingsTooltips.descriptionKey(key);
            assertNotNull(descriptionKey, key);
            assertFalse(genericKeys.contains(descriptionKey), key);
            String spanish = new GdxGameText("es").translate(descriptionKey);
            String english = new GdxGameText("en").translate(descriptionKey);
            assertTrue(spanish.length() >= 35, key + " es");
            assertTrue(english.length() >= 35, key + " en");
        });
    }

    @Test
    void cpuMemoryTooltipStaysConcise() {
        GdxSettingsTooltips.Content spanish = GdxSettingsTooltips.content(
                "gdx_show_cpu_memory", new GdxGameText("es"));
        GdxSettingsTooltips.Content english = GdxSettingsTooltips.content(
                "gdx_show_cpu_memory", new GdxGameText("en"));

        assertTrue(spanish.text().length() < 90);
        assertTrue(english.text().length() < 90);
    }

    @Test
    void modAudioTooltipExplainsWhichVersionIsUsed() {
        GdxSettingsTooltips.Content spanish = GdxSettingsTooltips.content(
                "sonidos_chorra", new GdxGameText("es"),
                true);
        GdxSettingsTooltips.Content english = GdxSettingsTooltips.content(
                "sonidos_chorra", new GdxGameText("en"),
                true);

        assertTrue(spanish.text().contains("MOD cargado"));
        assertTrue(english.text().contains("loaded MOD"));
    }

    @Test
    void jokeStartAndRecoveryAudioTooltipsExplainTheirRealEffect() {
        GdxGameText spanish = new GdxGameText("es");

        assertTrue(GdxSettingsTooltips.content("sonidos_chorra", spanish).text().contains("broma"));
        assertTrue(GdxSettingsTooltips.content("sonido_inicio", spanish).text().contains("comienza"));
        assertTrue(GdxSettingsTooltips.content("musica_recuperacion", spanish).text().contains("recupera"));
    }

    @Test
    void jokeBankTooltipsExplainFreeFilenamesAndRealProvenance() {
        String resource = "joke/es/allin/sonrie.wav";
        String key = com.tonikelope.coronapoker.core.game
                .GamePresentationSettings.jokeSoundPreferenceKey(resource);

        String officialSpanish = GdxSettingsTooltips.content(key,
                new GdxGameText("es")).text();
        String modSpanish = GdxSettingsTooltips.content(key, new GdxGameText("es"), true).text();
        String officialParent = GdxSettingsTooltips.content(
                "sonido_chorra_allin",
                new GdxGameText("es"), false,
                GdxGameAudioResources.JokeBankMode.OFFICIAL).text();
        String exclusiveParent = GdxSettingsTooltips.content(
                "sonido_chorra_allin",
                new GdxGameText("es"), true,
                GdxGameAudioResources.JokeBankMode.MOD_ONLY).text();
        String fusedParent = GdxSettingsTooltips.content(
                "sonido_chorra_allin",
                new GdxGameText("es"), true,
                GdxGameAudioResources.JokeBankMode.FUSED).text();

        assertTrue(officialSpanish.contains("sounds/" + resource));
        assertTrue(officialSpanish.contains("cualquier nombre"));
        assertFalse(officialSpanish.contains("sustituirlo"));
        assertTrue(modSpanish.contains("sounds/" + resource));
        assertTrue(modSpanish.contains("nombre es libre"));
        assertTrue(officialParent.contains("sounds/joke/es/allin/"));
        assertTrue(officialParent.contains("banco oficial"));
        assertTrue(exclusiveParent.contains("solo sus WAV"));
        assertTrue(exclusiveParent.contains("fusion_sounds=false"));
        assertTrue(fusedParent.contains("combina los WAV oficiales"));
        assertTrue(fusedParent.contains("fusion_sounds=true"));
        assertFalse(exclusiveParent.contains("MOD cargado"));
    }

    @Test
    void audioTooltipsNameTheRealModFilesWithoutGenericFiller() {
        String startup = GdxSettingsTooltips.content("sonido_arranque", new GdxGameText("es")).text();
        String switchSounds = GdxSettingsTooltips.content(
                "sonido_interruptor",
                new GdxGameText("es")).text();

        assertTrue(startup.contains("sounds/misc/init.wav"));
        assertTrue(startup.contains("Ruta exacta"));
        assertTrue(switchSounds.contains("sounds/misc/button_on.wav"));
        assertTrue(switchSounds.contains("sounds/misc/button_off.wav"));
    }

    @Test
    void allGameRowsHaveDedicatedDescriptions() {
        List<String> keys = List.of(
                "structure", "initial_blinds", "ante", "straddle",
                GdxSettingsContract.STRADDLE_ON_TIMEOUT_KEY,
                "blind_structures", "increase_blinds", "blind_unit",
                "blind_interval", "blind_cap", "blind_cap_level",
                "fixed_buyin", "initial_buyin", "minimum_buyin",
                "maximum_buyin", "rebuy_cap", "rebuy",
                "player_rebuy_limit", "maximum_rebuys", "bot_difficulty",
                "bot_rebuy", "custom_bots", "custom_bots_only",
                "bot_balance", "custom_bots_manage", "hand_limit", "hand_count",
                "think_time", "think_seconds", "showdown_time",
                "allow_hot_join", "iwtsth", "run_it_twice",
                "rabbit_hunting");
        keys.forEach(key -> assertNotNull(GdxSettingsTooltips.descriptionKey(key), key));
    }
}
