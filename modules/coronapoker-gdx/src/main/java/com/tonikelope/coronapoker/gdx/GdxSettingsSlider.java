/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import java.util.Properties;

/** A stepped settings slider with optional minus and plus buttons. */
final class GdxSettingsSlider {
    interface Setting {
        int min();
        int max();
        int step();
        int value(Properties settings);
        boolean enabled(Properties settings);
        void select(Properties settings, int value);
    }
    private final boolean stepButtons;
    private final Rectangle track = new Rectangle(), hit = new Rectangle();
    private final Rectangle minus = new Rectangle(), plus = new Rectangle();
    private Setting visible, pressed;
    private int pointer = -1;
    private boolean dragging;

    GdxSettingsSlider(boolean stepButtons) {
        this.stepButtons = stepButtons;
    }

    void hide() { visible = null; }

    void cancel() {
        hide();
        pointer = -1;
        pressed = null;
    }

    void draw(ShapeRenderer shapes, Rectangle row, GdxSettingsLayout.PixelRows rows,
            Setting setting, int value, boolean enabled, float alpha) {
        float mid = row.y + row.height * .5f;
        float start = row.x + row.width * .42f, end = row.x + row.width - 106f;
        float buttonInset = stepButtons ? 60f : 0f;
        track.set(start + buttonInset, mid - 4f, end - start - 2f * buttonInset, 8f);
        float bottom = Math.max(row.y, rows.viewportBottom()), top = Math.min(row.y + row.height, rows.viewportTop());
        float hitPadding = stepButtons ? 0f : 8f;
        hit.set(start - hitPadding, bottom, end - start + 2f * hitPadding,
                Math.max(0f, top - bottom));
        visible = enabled && top > bottom ? setting : null;
        float opacity = enabled ? alpha : alpha * .42f;
        float progress = (float) (value - setting.min()) / (setting.max() - setting.min());
        GdxSettingsStyle.drawAngleSlider(shapes, row, track, progress, opacity);
        if (stepButtons) {
            minus.set(start, mid - 20f, 42f, 40f);
            plus.set(end - 42f, mid - 20f, 42f, 40f);
            drawButton(shapes, minus, false, value > setting.min(), opacity);
            drawButton(shapes, plus, true, value < setting.max(), opacity);
        }
    }

    private static void drawButton(ShapeRenderer shapes, Rectangle r, boolean plus, boolean enabled, float alpha) {
        GdxSettingsStyle.drawRow(shapes, r.x, r.y, r.width, r.height, enabled, false, false, alpha);
        shapes.setColor(1f, 1f, 1f, enabled ? alpha : .4f * alpha);
        float cx = r.x + r.width * .5f, cy = r.y + r.height * .5f;
        shapes.rect(cx - 8f, cy - 1.5f, 16f, 3f);
        if (plus) shapes.rect(cx - 1.5f, cy - 8f, 3f, 16f);
    }

    boolean begin(float x, float y, int id, Properties settings) {
        if (pointer >= 0 || !current(settings) || !hit.contains(x, y)) return false;
        int direction = 0;
        if (stepButtons) {
            direction = minus.contains(x, y) ? -1 : plus.contains(x, y) ? 1 : 0;
        }
        if (direction == 0 && (x < track.x - 8f || x > track.x + track.width + 8f)) return false;
        pressed = visible;
        pointer = id;
        dragging = direction == 0;
        if (dragging) drag(x, id, settings);
        else pressed.select(settings, snap(pressed, pressed.value(settings) + direction * pressed.step()));
        return true;
    }

    private boolean current(Properties settings) {
        return visible != null && visible.enabled(settings);
    }

    boolean drag(float x, int id, Properties settings) {
        if (pointer != id) return false;
        if (dragging && visible == pressed && current(settings)) {
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
