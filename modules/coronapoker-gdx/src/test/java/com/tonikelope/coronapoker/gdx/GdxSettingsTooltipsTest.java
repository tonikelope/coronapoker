package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class GdxSettingsTooltipsTest {

    @Test
    void graphicsTooltipsExplainTheSettingAndThenItsImpact() {
        Properties properties = new Properties();
        properties.setProperty("gdx_msaa_samples", "4");
        GdxSettingsTooltips.Content spanish = GdxSettingsTooltips.content(
                "gdx_msaa_samples", properties, new GdxGameText("es"));
        assertTrue(spanish.text().contains("Suaviza"));
        assertTrue(spanish.text().contains("Impacto estimado en el rendimiento: MEDIO"));
        assertEquals(GdxSettingsContract.PerformanceImpact.MEDIUM,
                spanish.performanceImpact());

        GdxSettingsTooltips.Content english = GdxSettingsTooltips.content(
                "gdx_vsync", properties, new GdxGameText("en"));
        assertTrue(english.text().contains("Synchronizes frames"));
        assertFalse(english.text().contains("Estimated performance impact"));

        properties.setProperty("gdx_fps_limit", "0");
        GdxSettingsTooltips.Content monitorRefresh = GdxSettingsTooltips.content(
                "gdx_fps_limit", properties, new GdxGameText("en"));
        assertTrue(monitorRefresh.text().contains("follows your display"));
        assertFalse(monitorRefresh.text().contains("Estimated performance impact"));
    }

    @Test
    void pokerVideoTooltipExplainsReopeningDuringPlayInBothLanguages() {
        String key = GdxSettingsContract.POKER_VIDEOS_KEY;
        Properties properties = new Properties();
        assertTrue(GdxSettingsTooltips.content(key, properties,
                new GdxGameText("es")).text().contains(
                        "puedes volver a abrirlo desde su icono"));
        assertTrue(GdxSettingsTooltips.content(key, properties,
                new GdxGameText("en")).text().contains(
                        "can be reopened from its icon"));
    }

    @Test
    void everyVisibleAudioAndAppearanceOptionHasARealDescription() {
        Stream<String> audio = GdxSettingsContract.AUDIO_PAGES.stream()
                .flatMap(page -> page.options().stream())
                .map(GdxSettingsContract.ToggleOption::key);
        Stream<String> appearance = GdxSettingsContract.APPEARANCE_PAGES
                .stream().flatMap(page -> page.options().stream())
                .map(GdxSettingsContract.ToggleOption::key);
        Stream<String> table = GdxSettingsContract.appearanceTablePage()
                .options().stream().map(GdxSettingsContract.ToggleOption::key);
        Stream<String> choices = GdxAppearanceOptions.ANIMATION_CHOICES
                .stream().map(GdxAppearanceOptions.Choice::key);
        Stream.concat(Stream.concat(Stream.concat(audio, appearance), table),
                choices)
                .forEach(key -> assertFalse(
                        "gdx.settings.tooltip.generic".equals(
                                GdxSettingsTooltips.descriptionKey(key)), key));
    }

    @Test
    void allControlAndGraphicsRowsHaveDedicatedDescriptions() {
        List<String> keys = List.of(
                "gdx_window_mode", "auto_fullscreen", "gdx_vsync",
                "gdx_fps_limit", "gdx_msaa_samples",
                GdxSettingsContract.CARD_RENDERING_KEY, "gdx_show_fps",
                "gdx_show_cpu_memory", "gpu_renderer", "auto_action_buttons",
                "auto_call_enabled", "auto_action_persist",
                "modo_auto_confirm", "confirmar_todo", "auto_rebuy",
                "shortcut");
        keys.forEach(key -> assertFalse(
                "gdx.settings.tooltip.generic".equals(
                        GdxSettingsTooltips.descriptionKey(key)), key));
    }

    @Test
    void shufflePreloadHasItsOwnConciseMemoryTooltip() {
        GdxSettingsTooltips.Content spanish = GdxSettingsTooltips.content(
                "animacion_barajado_precarga", new Properties(),
                new GdxGameText("es"));
        assertFalse(spanish.text().contains("MiB"));
        assertTrue(spanish.text().contains("uso de memoria"));
        assertTrue(spanish.text().length() < 140);

        GdxSettingsTooltips.Content english = GdxSettingsTooltips.content(
                "animacion_barajado_precarga", new Properties(),
                new GdxGameText("en"));
        assertFalse(english.text().contains("MiB"));
        assertTrue(english.text().contains("memory usage"));
        assertTrue(english.text().length() < 140);

        assertEquals("gdx.settings.tooltip.animation_shuffle",
                GdxSettingsTooltips.descriptionKey("animacion_barajado"));
        assertEquals("gdx.settings.tooltip.shuffle_preload",
                GdxSettingsTooltips.descriptionKey(
                        "animacion_barajado_precarga"));
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
            assertFalse(genericKeys.contains(descriptionKey), key);
            String spanish = new GdxGameText("es").translate(descriptionKey);
            String english = new GdxGameText("en").translate(descriptionKey);
            assertTrue(spanish.length() >= 35, key + " es");
            assertTrue(english.length() >= 35, key + " en");
        });
    }

    @Test
    void cpuMemoryTooltipStaysConcise() {
        Properties properties = new Properties();
        GdxSettingsTooltips.Content spanish = GdxSettingsTooltips.content(
                "gdx_show_cpu_memory", properties, new GdxGameText("es"));
        GdxSettingsTooltips.Content english = GdxSettingsTooltips.content(
                "gdx_show_cpu_memory", properties, new GdxGameText("en"));

        assertTrue(spanish.text().length() < 90);
        assertTrue(english.text().length() < 90);
    }

    @Test
    void modAudioTooltipExplainsWhichVersionIsUsed() {
        GdxSettingsTooltips.Content spanish = GdxSettingsTooltips.content(
                "sonidos_chorra", new Properties(), new GdxGameText("es"),
                true);
        GdxSettingsTooltips.Content english = GdxSettingsTooltips.content(
                "sonidos_chorra", new Properties(), new GdxGameText("en"),
                true);

        assertTrue(spanish.text().contains("MOD cargado"));
        assertTrue(english.text().contains("loaded MOD"));
    }

    @Test
    void jokeStartAndRecoveryAudioTooltipsExplainTheirRealEffect() {
        GdxGameText spanish = new GdxGameText("es");

        assertTrue(GdxSettingsTooltips.content("sonidos_chorra",
                new Properties(), spanish).text().contains("broma"));
        assertTrue(GdxSettingsTooltips.content("sonido_inicio",
                new Properties(), spanish).text().contains("comienza"));
        assertTrue(GdxSettingsTooltips.content("musica_recuperacion",
                new Properties(), spanish).text().contains("recupera"));
    }

    @Test
    void jokeBankTooltipsExplainFreeFilenamesAndRealProvenance() {
        String resource = "joke/es/allin/sonrie.wav";
        String key = com.tonikelope.coronapoker.core.game
                .GamePresentationSettings.jokeSoundPreferenceKey(resource);

        String officialSpanish = GdxSettingsTooltips.content(key,
                new Properties(),
                new GdxGameText("es")).text();
        String modSpanish = GdxSettingsTooltips.content(key,
                new Properties(), new GdxGameText("es"), true).text();
        String officialParent = GdxSettingsTooltips.content(
                "sonido_chorra_allin", new Properties(),
                new GdxGameText("es"), false,
                GdxGameAudioResources.JokeBankMode.OFFICIAL).text();
        String exclusiveParent = GdxSettingsTooltips.content(
                "sonido_chorra_allin", new Properties(),
                new GdxGameText("es"), true,
                GdxGameAudioResources.JokeBankMode.MOD_ONLY).text();
        String fusedParent = GdxSettingsTooltips.content(
                "sonido_chorra_allin", new Properties(),
                new GdxGameText("es"), true,
                GdxGameAudioResources.JokeBankMode.FUSED).text();

        assertTrue(officialSpanish.contains("sounds/joke/es/allin/"));
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
        String startup = GdxSettingsTooltips.content("sonido_arranque",
                new Properties(), new GdxGameText("es")).text();
        String switchSounds = GdxSettingsTooltips.content(
                "sonido_interruptor", new Properties(),
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
        keys.forEach(key -> assertFalse(
                "gdx.settings.tooltip.generic".equals(
                        GdxSettingsTooltips.descriptionKey(key)), key));
    }
}
