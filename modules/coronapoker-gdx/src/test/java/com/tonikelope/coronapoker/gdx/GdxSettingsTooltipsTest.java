package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Properties;
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
        assertTrue(spanish.text().contains("Impacto en el rendimiento: ALTO"));
        assertEquals(GdxSettingsContract.PerformanceImpact.HIGH,
                spanish.performanceImpact());

        GdxSettingsTooltips.Content english = GdxSettingsTooltips.content(
                "gdx_vsync", properties, new GdxGameText("en"));
        assertTrue(english.text().contains("Synchronizes frames"));
        assertTrue(english.text().contains("Performance impact: HIGH"));
    }

    @Test
    void everyVisibleAudioAndAppearanceOptionHasARealDescription() {
        Stream<String> audio = GdxSettingsContract.AUDIO_PAGES.stream()
                .flatMap(page -> page.options().stream())
                .map(GdxSettingsContract.ToggleOption::key);
        Stream<String> appearance = GdxSettingsContract.APPEARANCE_PAGES
                .stream().flatMap(page -> page.options().stream())
                .map(GdxSettingsContract.ToggleOption::key);
        Stream<String> choices = GdxAppearanceOptions.ANIMATION_CHOICES
                .stream().map(GdxAppearanceOptions.Choice::key);
        Stream.concat(Stream.concat(audio, appearance), choices)
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

        assertEquals("gdx.settings.tooltip.animation",
                GdxSettingsTooltips.descriptionKey("animacion_barajado"));
        assertEquals("gdx.settings.tooltip.shuffle_preload",
                GdxSettingsTooltips.descriptionKey(
                        "animacion_barajado_precarga"));
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
    void allGameRowsHaveDedicatedDescriptions() {
        List<String> keys = List.of(
                "structure", "initial_blinds", "ante", "straddle",
                "blind_structures", "increase_blinds", "blind_unit",
                "blind_interval", "blind_cap", "blind_cap_level",
                "fixed_buyin", "initial_buyin", "minimum_buyin",
                "maximum_buyin", "rebuy_cap", "rebuy",
                "player_rebuy_limit", "maximum_rebuys", "bot_difficulty",
                "bot_rebuy", "bot_balance", "hand_limit", "hand_count",
                "think_time", "think_seconds", "showdown_time",
                "allow_hot_join", "iwtsth", "run_it_twice",
                "rabbit_hunting");
        keys.forEach(key -> assertFalse(
                "gdx.settings.tooltip.generic".equals(
                        GdxSettingsTooltips.descriptionKey(key)), key));
    }
}
