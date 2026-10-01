/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Canonical visual tokens for every GDX settings surface.
 *
 * <p>The menu and waiting room share {@link GdxFrontendScreen}, while the live
 * table has its own renderer.  Keeping the complete palette, typography and
 * control geometry here prevents the table from becoming a merely similar
 * second theme.</p>
 */
final class GdxSettingsStyle {

    static final int TITLE_FONT_SIZE = 58;
    static final float TITLE_FONT_BORDER = 0.35f;
    static final int HEADING_FONT_SIZE = 30;
    static final float HEADING_FONT_BORDER = 0.2f;
    static final int ACTION_FONT_SIZE = 26;
    static final float ACTION_FONT_BORDER = 0.2f;
    static final int BODY_FONT_SIZE = 24;
    static final int SMALL_FONT_SIZE = 18;
    static final int TINY_FONT_SIZE = 15;

    /** One glass layer shared by menu, lobby and live-table settings. */
    static final int PANEL_RGBA = 0x101a2ecc;
    /** The content well is an outline only; it must not compound panel alpha. */
    static final int CONTENT_RGBA = 0x101a2e00;
    static final int PANEL_LIGHT_RGBA = 0x111a2add;
    static final int CYAN_RGBA = 0x36d9ffff;
    static final int CYAN_DARK_RGBA = 0x176b83ff;
    static final int GOLD_RGBA = 0xffe07aff;
    static final int LINE_RGBA = 0x31445fff;
    static final int MUTED_RGBA = 0xdbe5f3ff;
    static final int DISABLED_RGBA = 0x526078ff;
    static final int DISABLED_LINE_RGBA = 0x253044ff;
    static final int DISABLED_FILL_RGBA = 0x0b111ddd;
    static final int PRESSED_FILL_RGBA = 0x0b1424ff;
    static final int TOGGLE_OFF_RGBA = 0x253248ff;
    static final int TOGGLE_ON_RGBA = 0x20c765ff;
    static final int TOGGLE_KNOB_OFF_RGBA = 0x8290a4ff;
    static final int TOGGLE_KNOB_ON_RGBA = 0xb8ffc5ff;

    static final float TOGGLE_WIDTH = 66f;
    static final float TOGGLE_HEIGHT = 38f;
    static final float TOGGLE_RIGHT_INSET = 22f;
    static final float TOGGLE_BOTTOM_INSET = 15f;
    static final float TOGGLE_KNOB_RADIUS = 14f;
    static final float TOGGLE_KNOB_START = 19f;
    static final float TOGGLE_KNOB_TRAVEL = 28f;

    private GdxSettingsStyle() {
    }

    static Color rowBorder(boolean enabled, boolean hovered) {
        return new Color(!enabled ? DISABLED_LINE_RGBA
                : hovered ? CYAN_RGBA : LINE_RGBA);
    }

    static Color rowFill(boolean enabled, boolean pressed) {
        return new Color(!enabled ? DISABLED_FILL_RGBA
                : pressed ? PRESSED_FILL_RGBA : PANEL_LIGHT_RGBA);
    }

    static Color toggleTrack(float animation) {
        return new Color(TOGGLE_OFF_RGBA).lerp(
                new Color(TOGGLE_ON_RGBA), animation);
    }

    static Color toggleKnob(float animation) {
        return new Color(TOGGLE_KNOB_OFF_RGBA).lerp(
                new Color(TOGGLE_KNOB_ON_RGBA), animation);
    }

    static void drawRow(ShapeRenderer shapes, float x, float y, float width,
            float height, boolean enabled, boolean hovered, boolean pressed,
            float alpha) {
        Color fill = rowFill(enabled, pressed);
        Color border = rowBorder(enabled, hovered);
        shapes.setColor(fill.r, fill.g, fill.b, fill.a * alpha);
        roundedRect(shapes, x, y, width, height,
                Math.min(14f, height / 2f));
        shapes.setColor(border.r, border.g, border.b, border.a * alpha);
        roundedRectOutline(shapes, x + 1f, y + 1f,
                width - 2f, height - 2f,
                Math.min(13f, height / 2f - 1f), 2f);
        if (height <= 90f && width > 90f) {
            float inset = 14f;
            float sheenBottom = y + height * 0.54f;
            float sheenTop = y + height - 9f;
            shapes.rect(x + inset, sheenBottom, width - inset * 2f,
                    sheenTop - sheenBottom,
                    new Color(1f, 1f, 1f, 0.018f * alpha),
                    new Color(1f, 1f, 1f, 0.018f * alpha),
                    new Color(1f, 1f, 1f, 0.115f * alpha),
                    new Color(1f, 1f, 1f, 0.115f * alpha));
            shapes.rect(x + inset, y + 8f, width - inset * 2f,
                    height * 0.18f,
                    new Color(0f, 0f, 0f, 0.11f * alpha),
                    new Color(0f, 0f, 0f, 0.11f * alpha),
                    new Color(0f, 0f, 0f, 0.01f * alpha),
                    new Color(0f, 0f, 0f, 0.01f * alpha));
        }
    }

    static void drawToggle(ShapeRenderer shapes, float x, float y,
            float width, float animation, float alpha) {
        float trackX = x + width - TOGGLE_WIDTH - TOGGLE_RIGHT_INSET;
        Color track = toggleTrack(animation);
        shapes.setColor(track.r, track.g, track.b, alpha);
        roundedRect(shapes, trackX, y + TOGGLE_BOTTOM_INSET,
                TOGGLE_WIDTH, TOGGLE_HEIGHT, TOGGLE_HEIGHT / 2f);
        Color knob = toggleKnob(animation);
        shapes.setColor(knob.r, knob.g, knob.b, alpha);
        shapes.circle(trackX + TOGGLE_KNOB_START
                + TOGGLE_KNOB_TRAVEL * animation,
                y + TOGGLE_BOTTOM_INSET + TOGGLE_HEIGHT / 2f,
                TOGGLE_KNOB_RADIUS, 32);
    }

    private static void roundedRect(ShapeRenderer shapes, float x, float y,
            float width, float height, float radius) {
        float r = Math.min(radius, Math.min(width, height) / 2f);
        shapes.rect(x + r, y, width - 2f * r, height);
        shapes.rect(x, y + r, r, height - 2f * r);
        shapes.rect(x + width - r, y + r, r, height - 2f * r);
        shapes.arc(x + r, y + r, r, 180f, 90f, 18);
        shapes.arc(x + width - r, y + r, r, 270f, 90f, 18);
        shapes.arc(x + width - r, y + height - r, r, 0f, 90f, 18);
        shapes.arc(x + r, y + height - r, r, 90f, 90f, 18);
    }

    private static void roundedRectOutline(ShapeRenderer shapes, float x,
            float y, float width, float height, float radius,
            float thickness) {
        float r = Math.min(radius, Math.min(width, height) / 2f);
        shapes.rectLine(x + r, y, x + width - r, y, thickness);
        shapes.rectLine(x + r, y + height, x + width - r,
                y + height, thickness);
        shapes.rectLine(x, y + r, x, y + height - r, thickness);
        shapes.rectLine(x + width, y + r, x + width,
                y + height - r, thickness);
        quarterArc(shapes, x + r, y + r, r, 180f, thickness);
        quarterArc(shapes, x + width - r, y + r, r, 270f, thickness);
        quarterArc(shapes, x + width - r, y + height - r, r, 0f,
                thickness);
        quarterArc(shapes, x + r, y + height - r, r, 90f, thickness);
    }

    private static void quarterArc(ShapeRenderer shapes, float cx, float cy,
            float radius, float startDegrees, float thickness) {
        float previous = (float) Math.toRadians(startDegrees);
        float previousX = cx + (float) Math.cos(previous) * radius;
        float previousY = cy + (float) Math.sin(previous) * radius;
        for (int step = 1; step <= 10; step++) {
            float radians = (float) Math.toRadians(startDegrees + step * 9f);
            float nextX = cx + (float) Math.cos(radians) * radius;
            float nextY = cy + (float) Math.sin(radians) * radius;
            shapes.rectLine(previousX, previousY, nextX, nextY, thickness);
            previousX = nextX;
            previousY = nextY;
        }
    }
}
