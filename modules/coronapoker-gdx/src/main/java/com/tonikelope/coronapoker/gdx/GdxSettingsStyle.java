/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;

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
    /** Dedicated glyph size for the compact minus/plus settings buttons. */
    static final int STEPPER_SIGN_FONT_SIZE = 38;
    static final float STEPPER_SIGN_FONT_BORDER = 0.2f;
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

    private static final Color ROW_DISABLED_BORDER = new Color(
            DISABLED_LINE_RGBA);
    private static final Color ROW_HOVER_BORDER = new Color(CYAN_RGBA);
    private static final Color ROW_BORDER = new Color(LINE_RGBA);
    private static final Color ROW_DISABLED_FILL = new Color(
            DISABLED_FILL_RGBA);
    private static final Color ROW_PRESSED_FILL = new Color(
            PRESSED_FILL_RGBA);
    private static final Color ROW_FILL = new Color(PANEL_LIGHT_RGBA);
    private static final Color GOLD = new Color(GOLD_RGBA);
    private static final Color CYAN = new Color(CYAN_RGBA);
    private static final Color CYAN_DARK = new Color(CYAN_DARK_RGBA);
    private static final Color SHORTCUT_DESCRIPTION = new Color(0xffffffff);
    private static final Color SHORTCUT_KEY_FILL = new Color(0x07111fff);
    private static final Color ROW_SCROLL_TRACK = new Color(0x1f334feb);
    private static final Color ROW_SCROLL_THUMB = new Color(CYAN_RGBA);
    private static final Color TOGGLE_OFF = new Color(TOGGLE_OFF_RGBA);
    private static final Color TOGGLE_ON = new Color(TOGGLE_ON_RGBA);
    private static final Color TOGGLE_KNOB_OFF = new Color(
            TOGGLE_KNOB_OFF_RGBA);
    private static final Color TOGGLE_KNOB_ON = new Color(
            TOGGLE_KNOB_ON_RGBA);
    private static final Color TOGGLE_TRACK_SCRATCH = new Color();
    private static final Color TOGGLE_KNOB_SCRATCH = new Color();

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
        return !enabled ? ROW_DISABLED_BORDER
                : hovered ? ROW_HOVER_BORDER : ROW_BORDER;
    }

    static Color rowFill(boolean enabled, boolean pressed) {
        return !enabled ? ROW_DISABLED_FILL
                : pressed ? ROW_PRESSED_FILL : ROW_FILL;
    }

    /**
     * Shortcut text is a semantic settings token, not a renderer-local
     * choice.  Menu, waiting room and live table must therefore consume the
     * exact same Color instance for both normal and capture states.
     */
    static Color shortcutKeyText(boolean capturing) {
        return capturing ? GOLD : CYAN;
    }

    static Color shortcutDescriptionText() {
        return SHORTCUT_DESCRIPTION;
    }

    static Color toggleTrack(float animation) {
        return TOGGLE_TRACK_SCRATCH.set(TOGGLE_OFF).lerp(
                TOGGLE_ON, animation);
    }

    static Color toggleKnob(float animation) {
        return TOGGLE_KNOB_SCRATCH.set(TOGGLE_KNOB_OFF).lerp(
                TOGGLE_KNOB_ON, animation);
    }

    static void drawRow(ShapeRenderer shapes, float x, float y, float width,
            float height, boolean enabled, boolean hovered, boolean pressed,
            float alpha) {
        Color fill = rowFill(enabled, pressed);
        Color border = rowBorder(enabled, hovered);
        drawRow(shapes, x, y, width, height, border, fill, alpha);
    }

    static void drawShortcutRow(ShapeRenderer shapes,
            GdxSettingsLayout.ShortcutRow row, boolean capturing,
            float alpha) {
        drawRow(shapes, row.bounds().x, row.bounds().y,
                row.bounds().width, row.bounds().height,
                capturing ? GOLD : ROW_BORDER, ROW_FILL, alpha);
        drawRow(shapes, row.key().x, row.key().y,
                row.key().width, row.key().height,
                capturing ? GOLD : CYAN_DARK, SHORTCUT_KEY_FILL, alpha);
    }

    static void drawDebugConsole(ShapeRenderer shapes, Rectangle bounds,
            float alpha) {
        GdxUiDialogStyle.drawInset(shapes, bounds.x, bounds.y,
                bounds.width, bounds.height, alpha);
    }

    static void drawDebugScrollbar(ShapeRenderer shapes, float x, float y,
            float height, float thumbY, float thumbHeight, float alpha) {
        shapes.setColor(ROW_BORDER.r, ROW_BORDER.g, ROW_BORDER.b,
                0.85f * alpha);
        roundedRect(shapes, x, y, GdxSettingsLayout.SCROLLBAR_WIDTH,
                height, GdxSettingsLayout.SCROLLBAR_WIDTH / 2f);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.88f * alpha);
        roundedRect(shapes, x, thumbY,
                GdxSettingsLayout.SCROLLBAR_WIDTH, thumbHeight,
                GdxSettingsLayout.SCROLLBAR_WIDTH / 2f);
    }

    static void drawRowScrollbar(ShapeRenderer shapes, float x, float y,
            float height, float thumbY, float thumbHeight, float alpha) {
        shapes.setColor(ROW_SCROLL_TRACK.r, ROW_SCROLL_TRACK.g,
                ROW_SCROLL_TRACK.b, ROW_SCROLL_TRACK.a * alpha);
        roundedRect(shapes, x, y, GdxSettingsLayout.SCROLLBAR_WIDTH,
                height, GdxSettingsLayout.SCROLLBAR_WIDTH / 2f);
        shapes.setColor(ROW_SCROLL_THUMB.r, ROW_SCROLL_THUMB.g,
                ROW_SCROLL_THUMB.b, 0.96f * alpha);
        roundedRect(shapes, x, thumbY,
                GdxSettingsLayout.SCROLLBAR_WIDTH, thumbHeight,
                GdxSettingsLayout.SCROLLBAR_WIDTH / 2f);
    }

    static void drawVolumeRow(ShapeRenderer shapes,
            GdxSettingsLayout.VolumeRow row, float progress, float alpha) {
        Rectangle bounds = row.bounds();
        Rectangle slider = row.slider();
        drawRow(shapes, bounds.x, bounds.y, bounds.width, bounds.height,
                ROW_BORDER, ROW_FILL, alpha);
        shapes.setColor(TOGGLE_OFF.r, TOGGLE_OFF.g, TOGGLE_OFF.b,
                TOGGLE_OFF.a * alpha);
        roundedRect(shapes, slider.x, slider.y, slider.width, slider.height,
                6f);
        shapes.setColor(ROW_HOVER_BORDER.r, ROW_HOVER_BORDER.g,
                ROW_HOVER_BORDER.b, ROW_HOVER_BORDER.a * alpha);
        roundedRect(shapes, slider.x, slider.y,
                slider.width * Math.max(0f, Math.min(1f, progress)),
                slider.height, 6f);
    }

    private static void drawRow(ShapeRenderer shapes, float x, float y,
            float width, float height, Color border, Color fill,
            float alpha) {
        shapes.setColor(fill.r, fill.g, fill.b, fill.a * alpha);
        roundedRect(shapes, x, y, width, height,
                Math.min(14f, height / 2f));
        GdxGlassBevel.draw(shapes, x, y, width, height,
                Math.min(14f, height / 2f),
                0.13f, alpha);
        shapes.setColor(border.r, border.g, border.b, border.a * alpha);
        roundedRectOutline(shapes, x + 1f, y + 1f,
                width - 2f, height - 2f,
                Math.min(13f, height / 2f - 1f), 2f);
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
        GdxAntialiasedRoundedRect.fillCircle(shapes,
                trackX + TOGGLE_KNOB_START
                        + TOGGLE_KNOB_TRAVEL * animation,
                y + TOGGLE_BOTTOM_INSET + TOGGLE_HEIGHT / 2f,
                TOGGLE_KNOB_RADIUS);
    }

    private static void roundedRect(ShapeRenderer shapes, float x, float y,
            float width, float height, float radius) {
        GdxAntialiasedRoundedRect.fill(shapes, x, y, width, height, radius);
    }

    private static void roundedRectOutline(ShapeRenderer shapes, float x,
            float y, float width, float height, float radius,
            float thickness) {
        GdxAntialiasedRoundedRect.outline(shapes, x, y, width, height,
                radius, thickness);
    }
}
