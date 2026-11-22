/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import java.util.Properties;

/** A settings track that snaps to the preference's configured step. */
final class GdxSettingsSlider {
    interface Setting {
        int min();
        int max();
        int step();
        int value(Properties settings);
        boolean enabled(Properties settings);
        void select(Properties settings, int value);
    }
    private final Rectangle track = new Rectangle(), hit = new Rectangle();
    private Setting visible, pressed;
    private int pointer = -1;

    void hide() { visible = null; }

    void cancel() {
        hide();
        pointer = -1;
        pressed = null;
    }

    void draw(ShapeRenderer shapes, Rectangle row, GdxSettingsLayout.PixelRows rows,
            Setting setting, int value, boolean enabled, float alpha) {
        GdxSettingsLayout.setSliderTrack(track, row);
        float bottom = Math.max(row.y, rows.viewportBottom()), top = Math.min(row.y + row.height, rows.viewportTop());
        hit.set(track.x - 8f, bottom, track.width + 16f,
                Math.max(0f, top - bottom));
        visible = enabled && track.width > 0f && top > bottom ? setting : null;
        float opacity = enabled ? alpha : alpha * .42f;
        float progress = (float) (value - setting.min()) / (setting.max() - setting.min());
        GdxSettingsStyle.drawAngleSlider(shapes, row, track, progress, opacity);
    }

    boolean begin(float x, float y, int id, Properties settings) {
        if (pointer >= 0 || !current(settings) || !hit.contains(x, y)) return false;
        pressed = visible;
        pointer = id;
        drag(x, id, settings);
        return true;
    }

    private boolean current(Properties settings) {
        return visible != null && visible.enabled(settings);
    }

    boolean drag(float x, int id, Properties settings) {
        if (pointer != id) return false;
        if (visible == pressed && current(settings)) {
            float value = pressed.min()
                    + (x - track.x) / track.width * (pressed.max() - pressed.min());
            pressed.select(settings, snap(pressed, value));
        }
        return true;
    }

    private static int snap(Setting setting, float value) {
        float bounded = MathUtils.clamp(value, setting.min(), setting.max());
        return setting.min() + setting.step()
                * Math.round((bounded - setting.min()) / setting.step());
    }

    boolean release(int id) {
        if (pointer != id) return false;
        pointer = -1;
        pressed = null;
        return true;
    }
}
