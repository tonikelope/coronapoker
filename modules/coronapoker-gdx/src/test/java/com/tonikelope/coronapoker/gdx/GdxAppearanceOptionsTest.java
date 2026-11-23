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
        assertEquals("DEFAULT",
                GdxAppearanceOptions.cardBackLabel("default", text));
        assertEquals("Goliat", GdxAppearanceOptions.deckLabel("goliat", text));
        assertEquals("CustomDeck", GdxAppearanceOptions.deckLabel("CustomDeck", text));
        assertEquals("NO FELT",
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
    void obsoleteRevealStyleDoesNotAffectAnimationDefaults() {
        Properties properties = new Properties();
        properties.setProperty("community_reveal_style_2d", "rigid");
        properties.setProperty("community_reveal_style_3d", "rigid");
        int animationsPage = GdxSettingsContract.APPEARANCE_PAGES.size() + 1;
        for (int i = 1; i <= GdxSettingsContract.APPEARANCE_PAGES.size() + 1; i++) {
            if (GdxSettingsContract.isAppearanceTogglePage(i)
                    && GdxSettingsContract.hasAppearanceAnimationOptions(
                            GdxSettingsContract.appearanceTogglePage(i))) {
                animationsPage = i;
                break;
            }
        }
        assertTrue(GdxSettingsContract.appearancePageAtDefaults(properties, animationsPage, false));
        assertFalse(GdxAppearanceOptions.ANIMATION_CHOICES.stream()
                .anyMatch(choice -> choice.key().equals("community_reveal_style")));
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
    void lightOffSliderPreservesRangeStepAndSavedPreferences() {
        Properties properties = new Properties();
        var light = GdxAppearanceOptions.LIGHT_OFF;
        assertEquals(10, light.min());
        assertEquals(90, light.max());
        assertEquals(5, light.step());
        assertEquals(35, light.value(properties));
        properties.setProperty("nivel_luz", "88");
        assertEquals(90, light.value(properties));
        light.select(properties, 43);
        assertEquals("45", properties.getProperty("nivel_luz"));
        light.select(properties, 0);
        assertEquals(10, light.value(properties));
        light.select(properties, 100);
        assertEquals(90, light.value(properties));
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
        GdxAppearanceOptions.Choice speed = choice("swap_velocidad");
        GdxAppearanceOptions.Choice style = choice("swap_arco");
        properties.setProperty("card_flip_zoom", "130");
        properties.setProperty(speed.key(), "520");
        properties.setProperty(style.key(), "true");

        GdxAppearanceOptions.select(GdxAppearanceOptions.TABLE_VIEW, properties, 1);
        assertFalse(GdxAppearanceOptions.enabled(speed, properties));
        assertFalse(GdxAppearanceOptions.enabled(style, properties));
        assertTrue(GdxAppearanceOptions.enabled(choice("card_flip_duration"), properties));
        assertTrue(GdxAppearanceOptions.enabled(choice("card_flip_direction"), properties));

        GdxAppearanceOptions.select(GdxAppearanceOptions.TABLE_VIEW, properties, 0);
        assertTrue(GdxAppearanceOptions.enabled(speed, properties));
        assertTrue(GdxAppearanceOptions.enabled(style, properties));
        assertEquals("130", properties.getProperty("card_flip_zoom"));
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
