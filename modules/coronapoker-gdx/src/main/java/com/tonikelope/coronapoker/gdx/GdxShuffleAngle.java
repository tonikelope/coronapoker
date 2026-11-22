/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import java.util.Properties;

/** Azimuth about the felt normal, measured from the local player's view. */
final class GdxShuffleAngle {
    static final String KEY = "barajado_giro_3d";
    static final String LABEL = "gdx.settings.row.shuffle_angle";
    static final int DEFAULT = 40;
    private final Rectangle track = new Rectangle();
    private final Rectangle hit = new Rectangle();
    private int pointer = -1;

    static int value(Properties settings) {
        try {
            return MathUtils.clamp(Integer.parseInt(settings.getProperty(KEY)), -180, 180);
        } catch (NumberFormatException invalid) {
            return DEFAULT;
        }
    }

    static boolean enabled(Properties settings) {
        return Boolean.parseBoolean(settings.getProperty("animaciones", "true"))
                && Boolean.parseBoolean(settings.getProperty("animacion_barajado", "true"));
    }

    void hide() {
        hit.set(0, 0, 0, 0);
    }

    void draw(ShapeRenderer shapes, Rectangle row, GdxSettingsLayout.PixelRows rows,
            int angle, boolean enabled, float alpha) {
        GdxSettingsLayout.setSliderTrack(track, row);
        float bottom = Math.max(row.y, rows.viewportBottom());
        float top = Math.min(row.y + row.height, rows.viewportTop());
        if (enabled && top > bottom) hit.set(track.x - 8f, bottom, track.width + 16f, top - bottom);
        else hide();
        float progress = (angle + 180f) / 360f;
        GdxSettingsStyle.drawAngleSlider(shapes, row, track, progress, enabled ? alpha : alpha * .42f);
    }

    static String labelValue(int angle) {
        return GdxSettingsContract.markDefault(angle + "°", angle == DEFAULT);
    }

    boolean begin(float x, float y, int id, Properties settings) {
        if (pointer >= 0 || hit.width <= 0f || !hit.contains(x, y) || !enabled(settings)) return false;
        pointer = id;
        drag(x, id, settings);
        return true;
    }

    boolean drag(float x, int id, Properties settings) {
        if (pointer != id) return false;
        if (hit.width > 0f && enabled(settings)) {
            int degrees = Math.round(360f * MathUtils.clamp((x - track.x) / track.width, 0f, 1f)) - 180;
            settings.setProperty(KEY, Integer.toString(degrees));
        }
        return true;
    }

    void cancel() {
        hide();
        pointer = -1;
    }

    boolean release(int id) {
        if (pointer != id) return false;
        pointer = -1;
        return true;
    }
}
