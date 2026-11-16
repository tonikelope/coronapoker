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
        assertEquals("Default",
                GdxAppearanceOptions.cardBackLabel("default", text));
        assertEquals("Goliat", GdxAppearanceOptions.deckLabel("goliat", text));
        assertEquals("CustomDeck", GdxAppearanceOptions.deckLabel("CustomDeck", text));
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
    void communityRevealStyleUsesAndResetsIndependentViewDefaults() {
        Properties properties = new Properties();
        var style = GdxAppearanceOptions.COMMUNITY_REVEAL_STYLE;
        assertEquals("animacion_destape", GdxAppearanceOptions.parentKey(style));
        assertEquals("TIPO DE DESTAPE COMUNITARIAS", style.label(new GdxGameText("es")));
        assertEquals("FLEXIBLE", GdxAppearanceOptions.selectedLabel(style, properties));
        GdxAppearanceOptions.select(GdxAppearanceOptions.TABLE_VIEW, properties, 0);
        assertEquals("RÍGIDO", GdxAppearanceOptions.selectedLabel(style, properties));
        assertTrue(GdxAppearanceOptions.isDefault(style, properties));

        GdxAppearanceOptions.select(style, properties, 1);
        GdxAppearanceOptions.select(GdxAppearanceOptions.TABLE_VIEW, properties, 1);
        assertEquals("FLEXIBLE", GdxAppearanceOptions.selectedLabel(style, properties));
        assertTrue(GdxAppearanceOptions.isDefault(style, properties));
        GdxAppearanceOptions.select(style, properties, 0);
        assertFalse(GdxAppearanceOptions.flexibleCommunityReveal(properties, true));
        assertTrue(GdxAppearanceOptions.flexibleCommunityReveal(properties, false));
        properties.setProperty("animacion_destape", "false");
        assertFalse(GdxAppearanceOptions.enabled(style, properties));

        GdxSettingsContract.restoreAppearanceDefaults(properties, false);
        assertFalse(GdxAppearanceOptions.flexibleCommunityReveal(properties, false));
        assertTrue(GdxAppearanceOptions.flexibleCommunityReveal(properties, true));
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
        GdxAppearanceOptions.select(GdxAppearanceOptions.TABLE_VIEW, properties, 0);
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
    void choosing3dLocksOnly2dEffectsAndKeepsTheirValues() {
        Properties properties = new Properties();
        GdxAppearanceOptions.Choice zoom = choice("card_flip_zoom");
        GdxAppearanceOptions.Choice speed = choice("swap_velocidad");
        GdxAppearanceOptions.Choice style = choice("swap_arco");
        properties.setProperty(zoom.key(), "130");
        properties.setProperty(speed.key(), "520");
        properties.setProperty(style.key(), "true");

        GdxAppearanceOptions.select(GdxAppearanceOptions.TABLE_VIEW, properties, 1);
        assertFalse(GdxAppearanceOptions.enabled(zoom, properties));
        assertFalse(GdxAppearanceOptions.enabled(speed, properties));
        assertFalse(GdxAppearanceOptions.enabled(style, properties));
        assertTrue(GdxAppearanceOptions.enabled(choice("card_flip_duration"), properties));
        assertTrue(GdxAppearanceOptions.enabled(choice("card_flip_direction"), properties));

        GdxAppearanceOptions.select(GdxAppearanceOptions.TABLE_VIEW, properties, 0);
        assertTrue(GdxAppearanceOptions.enabled(zoom, properties));
        assertTrue(GdxAppearanceOptions.enabled(speed, properties));
        assertTrue(GdxAppearanceOptions.enabled(style, properties));
        assertEquals("130", properties.getProperty(zoom.key()));
        assertEquals("520", properties.getProperty(speed.key()));
        assertEquals("true", properties.getProperty(style.key()));
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
