/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.MathUtils;
import java.util.Properties;

/** Percent of one community card on the felt, stored separately for each view. */
enum GdxCardZoom implements GdxSettingsSlider.Setting {
    SHUFFLE_2D("zoom_barajado_2d", 150, 300),
    SHUFFLE_3D("zoom_barajado_3d", 150, 300);

    static final String LEGACY_SHUFFLE_KEY = "barajado_tamano";
    static final String SHUFFLE_LABEL = "gdx.settings.row.shuffle_zoom";
    static final int MIN = 50;
    final String key;
    final int normal, max;

    GdxCardZoom(String key, int normal, int max) {
        this.key = key;
        this.normal = normal;
        this.max = max;
    }

    static GdxCardZoom shuffle(boolean perspective) { return perspective ? SHUFFLE_3D : SHUFFLE_2D; }


    int percent(Properties settings) {
        String raw = settings.getProperty(key);
        if (raw == null) {
            return switch (settings.getProperty(LEGACY_SHUFFLE_KEY, "")) {
                case "1" -> 100;
                case "2" -> 200;
                case "3" -> 300;
                default -> normal;
            };
        }
        try {
            return MathUtils.clamp(Integer.parseInt(raw), MIN, max);
        } catch (NumberFormatException invalid) {
            return normal;
        }
    }

    @Override public int min() { return MIN; }
    @Override public int max() { return max; }
    @Override public int step() { return 1; }
    @Override public int value(Properties settings) { return percent(settings); }

    @Override public boolean enabled(Properties settings) {
        return this == shuffle(GdxAppearanceOptions.perspectiveTable(settings))
                && GdxShuffleAngle.enabled(settings);
    }

    @Override public void select(Properties settings, int percent) {
        settings.setProperty(key, Integer.toString(percent));
    }

    String display(int percent) {
        return GdxSettingsContract.markDefault(percent + " %", percent == normal);
    }
}
