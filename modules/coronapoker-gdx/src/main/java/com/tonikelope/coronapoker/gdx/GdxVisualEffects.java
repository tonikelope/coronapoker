/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.Properties;

/** Runtime state for independently configurable optional visual effects. */
final class GdxVisualEffects {

    private static volatile boolean blurEnabled = true;
    private static volatile boolean bevelEnabled = true;

    private GdxVisualEffects() {
    }

    static void synchronize(Properties properties) {
        boolean effects = properties == null || Boolean.parseBoolean(
                properties.getProperty("animaciones", "true"));
        blurEnabled = effects && Boolean.parseBoolean(properties == null
                ? "true" : properties.getProperty(
                        GdxSettingsContract.MODAL_BLUR_KEY, "true"));
        bevelEnabled = effects && Boolean.parseBoolean(properties == null
                ? "true" : properties.getProperty(
                        GdxSettingsContract.GLASS_EFFECT_KEY, "true"));
    }

    static boolean blurActive() {
        return blurEnabled;
    }

    static boolean bevelActive() {
        return bevelEnabled;
    }
}
