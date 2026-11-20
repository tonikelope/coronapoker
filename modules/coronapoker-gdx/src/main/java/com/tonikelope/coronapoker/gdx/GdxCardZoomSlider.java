/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import java.util.Properties;

/** One visible zoom row per settings page. Buttons and dragging share the same five-percent grid. */
final class GdxCardZoomSlider {
    private final Rectangle track = new Rectangle(), hit = new Rectangle();
    private final Rectangle minus = new Rectangle(), plus = new Rectangle();
    private GdxCardZoom visible, pressed;
    private int pointer = -1;
    private boolean dragging;

    void hide() { visible = null; }

    void cancel() {
        hide();
        pointer = -1;
        pressed = null;
    }

    void cancelShuffle() {
        if ((pressed != null && pressed.isShuffle()) || (visible != null && visible.isShuffle())) cancel();
    }

    void draw(ShapeRenderer shapes, Rectangle row, GdxSettingsLayout.PixelRows rows,
            GdxCardZoom zoom, int value, boolean enabled, float alpha) {
        float mid = row.y + row.height * .5f;
        float start = row.x + row.width * .42f, end = row.x + row.width - 106f;
        minus.set(start, mid - 20f, 42f, 40f);
        plus.set(end - 42f, mid - 20f, 42f, 40f);
        track.set(start + 60f, mid - 4f, end - start - 120f, 8f);
        float bottom = Math.max(row.y, rows.viewportBottom()), top = Math.min(row.y + row.height, rows.viewportTop());
        hit.set(start, bottom, end - start, Math.max(0f, top - bottom));
        visible = enabled && top > bottom ? zoom : null;
        float opacity = enabled ? alpha : alpha * .42f;
        GdxSettingsStyle.drawAngleSlider(shapes, row, track, (float) (value - GdxCardZoom.MIN) / (zoom.max - GdxCardZoom.MIN), opacity);
        drawButton(shapes, minus, false, value > GdxCardZoom.MIN, opacity);
        drawButton(shapes, plus, true, value < zoom.max, opacity);
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
        int direction = minus.contains(x, y) ? -1 : plus.contains(x, y) ? 1 : 0;
        if (direction == 0 && (x < track.x - 8f || x > track.x + track.width + 8f)) return false;
        pressed = visible;
        pointer = id;
        dragging = direction == 0;
        if (dragging) drag(x, id, settings);
        else settings.setProperty(pressed.key, Integer.toString(pressed.snap(pressed.percent(settings) + direction * GdxCardZoom.STEP)));
        return true;
    }

    private boolean current(Properties settings) {
        if (visible == null) return false;
        boolean perspective = GdxAppearanceOptions.perspectiveTable(settings);
        return visible.isShuffle() ? visible == GdxCardZoom.shuffle(perspective) && GdxShuffleAngle.enabled(settings)
                : visible == GdxCardZoom.hand(perspective);
    }

    boolean drag(float x, int id, Properties settings) {
        if (pointer != id) return false;
        if (dragging && visible == pressed && current(settings)) {
            int value = pressed.snap(GdxCardZoom.MIN + (x - track.x) / track.width * (pressed.max - GdxCardZoom.MIN));
            settings.setProperty(pressed.key, Integer.toString(value));
        }
        return true;
    }

    boolean release(int id) {
        if (pointer != id) return false;
        pointer = -1;
        pressed = null;
        return true;
    }
}
