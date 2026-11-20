/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.MathUtils;
import java.util.Properties;

/** Percent of one community card on the felt, stored separately for each view. */
enum GdxCardZoom {
    HAND_2D("zoom_mano_2d", 130, 200),
    HAND_3D("zoom_mano_3d", 100, 200),
    SHUFFLE_2D("zoom_barajado_2d", 100, 300),
    SHUFFLE_3D("zoom_barajado_3d", 100, 300);

    static final String LEGACY_SHUFFLE_KEY = "barajado_tamano";
    static final String HAND_LABEL = "gdx.settings.row.hand_zoom";
    static final String SHUFFLE_LABEL = "gdx.settings.row.shuffle_zoom";
    static final int MIN = 50;
    static final int STEP = 5;
    final String key;
    final int normal, max;

    GdxCardZoom(String key, int normal, int max) {
        this.key = key;
        this.normal = normal;
        this.max = max;
    }

    static GdxCardZoom hand(boolean perspective) { return perspective ? HAND_3D : HAND_2D; }
    static GdxCardZoom shuffle(boolean perspective) { return perspective ? SHUFFLE_3D : SHUFFLE_2D; }

    boolean isShuffle() { return this == SHUFFLE_2D || this == SHUFFLE_3D; }

    int percent(Properties settings) {
        String raw = settings.getProperty(key);
        if (raw == null) {
            if (!isShuffle()) return normal;
            return switch (settings.getProperty(LEGACY_SHUFFLE_KEY, "")) {
                case "2" -> 200;
                case "3" -> 300;
                default -> normal;
            };
        }
        try {
            return snap(Integer.parseInt(raw));
        } catch (NumberFormatException invalid) {
            return normal;
        }
    }

    int snap(float value) {
        return STEP * Math.round(MathUtils.clamp(value, MIN, max) / STEP);
    }

    String display(int percent) {
        return GdxSettingsContract.markDefault(percent + " %", percent == normal);
    }
}
