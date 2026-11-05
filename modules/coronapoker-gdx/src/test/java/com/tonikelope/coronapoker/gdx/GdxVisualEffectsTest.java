/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.Properties;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class GdxVisualEffectsTest {

    @AfterEach
    void restoreDefaults() {
        GdxVisualEffects.synchronize(null);
    }

    @Test
    void effectsDefaultOnAndRemainIndependentlyConfigurable() {
        GdxVisualEffects.synchronize(null);
        assertTrue(GdxVisualEffects.blurActive());
        assertTrue(GdxVisualEffects.bevelActive());

        Properties properties = new Properties();
        properties.setProperty(GdxSettingsContract.MODAL_BLUR_KEY, "false");
        GdxVisualEffects.synchronize(properties);
        assertFalse(GdxVisualEffects.blurActive());
        assertTrue(GdxVisualEffects.bevelActive());

        properties.setProperty(GdxSettingsContract.MODAL_BLUR_KEY, "true");
        properties.setProperty(GdxSettingsContract.GLASS_EFFECT_KEY, "false");
        GdxVisualEffects.synchronize(properties);
        assertTrue(GdxVisualEffects.blurActive());
        assertFalse(GdxVisualEffects.bevelActive());
    }

    @Test
    void masterAnimationAndEffectsSwitchDisablesEveryOptionalEffect() {
        Properties properties = new Properties();
        properties.setProperty("animaciones", "false");
        properties.setProperty(GdxSettingsContract.MODAL_BLUR_KEY, "true");
        properties.setProperty(GdxSettingsContract.GLASS_EFFECT_KEY, "true");

        GdxVisualEffects.synchronize(properties);

        assertFalse(GdxVisualEffects.blurActive());
        assertFalse(GdxVisualEffects.bevelActive());
    }
}
