package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;

final class GdxAppearanceOptionsTest {

    @Test
    void choiceAndSurfaceLabelsFollowTheLiveLanguage() {
        GdxGameText text = new GdxGameText("en");
        Properties properties = new Properties();
        GdxAppearanceOptions.Choice deal = choice("reparto_velocidad");

        assertEquals("DEAL SPEED", deal.label(text));
        assertEquals("NORMAL",
                GdxAppearanceOptions.selectedLabel(deal, properties, text));
        assertEquals("FLIGHT TYPE",
                GdxAppearanceOptions.CHIP_FLIGHT_MODE.label(text));
        assertEquals("DYNAMIC", GdxAppearanceOptions.selectedLabel(
                GdxAppearanceOptions.CHIP_FLIGHT_MODE, properties, text));
        assertTrue(text.translate(
                GdxAppearanceOptions.CHIP_FLIGHT_MODE_TOOLTIP_KEY)
                .startsWith("Dynamic (default)"));
        assertEquals("Default",
                GdxAppearanceOptions.cardBackLabel("default", text));
        assertEquals("No felt",
                GdxAppearanceOptions.feltLabel("madera", text));
    }

    @Test
    void usesSwingDefaultsAndCyclesInCanonicalOrder() {
        Properties properties = new Properties();
        GdxAppearanceOptions.Choice deal = choice("reparto_velocidad");
        assertEquals("NORMAL",
                GdxAppearanceOptions.selectedLabel(deal, properties));
        GdxAppearanceOptions.cycle(deal, properties);
        assertEquals("RÁPIDA",
                GdxAppearanceOptions.selectedLabel(deal, properties));
        GdxAppearanceOptions.cycle(deal, properties);
        assertEquals("LENTA",
                GdxAppearanceOptions.selectedLabel(deal, properties));
        GdxAppearanceOptions.adjust(deal, properties, -1);
        assertEquals("RÁPIDA",
                GdxAppearanceOptions.selectedLabel(deal, properties));
    }

    @Test
    void nonCanonicalNumericValuesSelectTheNearestSwingPreset() {
        Properties properties = new Properties();
        GdxAppearanceOptions.Choice flip = choice("card_flip_duration");
        properties.setProperty("card_flip_duration", "500");
        assertEquals("RÁPIDA",
                GdxAppearanceOptions.selectedLabel(flip, properties));
        properties.setProperty("card_flip_duration", "no");
        assertEquals("NORMAL",
                GdxAppearanceOptions.selectedLabel(flip, properties));
    }

    @Test
    void lightLevelUsesSwingRangeStepAndWraps() {
        Properties properties = new Properties();
        assertEquals("35%", GdxAppearanceOptions.lightLevelLabel(properties));
        properties.setProperty("nivel_luz", "88");
        assertEquals("90%", GdxAppearanceOptions.lightLevelLabel(properties));
        GdxAppearanceOptions.cycleLightLevel(properties);
        assertEquals("10%", GdxAppearanceOptions.lightLevelLabel(properties));
    }

    @Test
    void lightLevelCanBeAdjustedInBothDirectionsWithoutWrapping() {
        Properties properties = new Properties();
        GdxAppearanceOptions.adjustLightLevel(properties, 1);
        assertEquals("40%", GdxAppearanceOptions.lightLevelLabel(properties));
        GdxAppearanceOptions.adjustLightLevel(properties, -1);
        assertEquals("35%", GdxAppearanceOptions.lightLevelLabel(properties));
        properties.setProperty("nivel_luz", "10");
        GdxAppearanceOptions.adjustLightLevel(properties, -1);
        assertEquals("10%", GdxAppearanceOptions.lightLevelLabel(properties));
        properties.setProperty("nivel_luz", "90");
        GdxAppearanceOptions.adjustLightLevel(properties, 1);
        assertEquals("90%", GdxAppearanceOptions.lightLevelLabel(properties));
    }

    @Test
    void animationChoicesFollowTheMasterAndTheirParentSwitches() {
        Properties properties = new Properties();
        GdxAppearanceOptions.Choice deal = choice("reparto_velocidad");
        GdxAppearanceOptions.Choice flip = choice("card_flip_duration");
        GdxAppearanceOptions.Choice swap = choice("swap_velocidad");

        assertTrue(GdxAppearanceOptions.enabled(deal, properties));
        assertTrue(GdxAppearanceOptions.enabled(flip, properties));
        assertTrue(GdxAppearanceOptions.enabled(swap, properties));

        properties.setProperty("animacion_reparto", "false");
        assertFalse(GdxAppearanceOptions.enabled(deal, properties));
        assertTrue(GdxAppearanceOptions.enabled(flip, properties));

        properties.setProperty("animacion_reparto", "true");
        properties.setProperty("animacion_destape", "false");
        assertFalse(GdxAppearanceOptions.enabled(flip, properties));
        assertTrue(GdxAppearanceOptions.enabled(swap, properties));

        properties.setProperty("animaciones", "false");
        assertFalse(GdxAppearanceOptions.enabled(deal, properties));
        assertFalse(GdxAppearanceOptions.enabled(flip, properties));
        assertFalse(GdxAppearanceOptions.enabled(swap, properties));
    }

    @Test
    void chipFlightModeDefaultsToDynamicAndBelongsToFlyingChips() {
        Properties properties = new Properties();
        GdxAppearanceOptions.Choice mode =
                GdxAppearanceOptions.CHIP_FLIGHT_MODE;

        assertEquals("DINÁMICO",
                GdxAppearanceOptions.selectedLabel(mode, properties));
        assertTrue(GdxAppearanceOptions.dynamicChipFlights(properties));
        assertTrue(GdxAppearanceOptions.enabled(mode, properties));

        GdxAppearanceOptions.cycle(mode, properties);
        assertEquals("ESTÁTICO",
                GdxAppearanceOptions.selectedLabel(mode, properties));
        assertFalse(GdxAppearanceOptions.dynamicChipFlights(properties));

        properties.setProperty("animacion_apuestas", "false");
        assertFalse(GdxAppearanceOptions.enabled(mode, properties));
    }

    @Test
    void everyAnimationChoiceUsesChildRowGeometry() {
        assertTrue(GdxAppearanceOptions.ANIMATION_CHOICES.stream()
                .allMatch(GdxAppearanceOptions::isChildChoice));
    }

    private static GdxAppearanceOptions.Choice choice(String key) {
        return GdxAppearanceOptions.ANIMATION_CHOICES.stream()
                .filter(choice -> key.equals(choice.key()))
                .findFirst().orElseThrow();
    }
}
