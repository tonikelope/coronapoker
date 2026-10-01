/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/**
 * One visual frame for every GDX settings entry point.
 *
 * <p>Content remains context-aware, but the panel, tab rows, content well and
 * footer are deliberately drawn here so menu/lobby and live-table settings
 * cannot drift into two different products again.</p>
 */
final class GdxSettingsChrome {

    private static final Color CYAN = new Color(GdxSettingsStyle.CYAN_RGBA);
    private static final Color CYAN_DARK = new Color(
            GdxSettingsStyle.CYAN_DARK_RGBA);
    private static final Color GOLD = new Color(GdxSettingsStyle.GOLD_RGBA);
    private static final Color LINE = new Color(GdxSettingsStyle.LINE_RGBA);
    // Full-screen settings is another frontend surface, so its glass opacity
    // must match menu/statistics/table setup rather than an opaque modal.
    private static final Color PANEL = new Color(GdxSettingsStyle.PANEL_RGBA);
    // The content well sits inside PANEL. Reapplying the same translucent fill
    // here compounded both alpha layers (0.80 + 0.80 visually became about
    // 0.96), making settings look opaque. Keep only its outline so the whole
    // screen has one uniform glass layer; rows retain their own backgrounds.
    private static final Color CONTENT = new Color(
            GdxSettingsStyle.CONTENT_RGBA);
    private static final Color TAB = new Color(0x0b1729b8);
    private static final Color ACTIVE_TAB = new Color(0x123047e8);
    private static final Color ACTIVE_SUBTAB = new Color(0x171b1de8);

    private GdxSettingsChrome() {
    }

    static void draw(ShapeRenderer shapes, GdxSettingsLayout.Frame frame,
            int sectionCount, int subpageCount, int activeSection,
            int activeSubpage, Vector2 pointer, boolean restoreVisible,
            float alpha) {
        Rectangle panel = frame.panel();
        Rectangle content = frame.content();

        shapes.setColor(0f, 0f, 0f, 0.40f * alpha);
        roundedRect(shapes, panel.x + 10f, panel.y - 10f,
                panel.width, panel.height, 20f);
        outerBox(shapes, panel, CYAN_DARK, PANEL, alpha);
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.72f * alpha);
        shapes.rect(panel.x + 22f, panel.y + panel.height - 11f,
                panel.width - 44f, 3f);
        outerBox(shapes, content, LINE, CONTENT, alpha);

        for (int index = 0; index < sectionCount; index++) {
            Rectangle tab = frame.mainTab(index);
            boolean active = index == activeSection;
            boolean hover = contains(tab, pointer);
            Color border = active ? CYAN : hover ? CYAN_DARK : LINE;
            Color fill = active ? ACTIVE_TAB : TAB;
            GdxUiButtonStyle.drawPalette(shapes, tab.x, tab.y,
                    tab.width, tab.height, border,
                    fill.r, fill.g, fill.b, fill.a,
                    true, hover ? 1f : 0f, false, alpha, false);
            if (active) {
                shapes.setColor(GOLD.r, GOLD.g, GOLD.b, alpha);
                roundedRect(shapes, tab.x + 10f, tab.y + 2f,
                        tab.width - 20f, 3f, 2f);
            }
        }

        Rectangle firstMainTab = frame.mainTab(0);
        Rectangle firstSubTab = frame.subTab(0);
        float separatorY = (firstMainTab.y
                + firstSubTab.y + firstSubTab.height) / 2f;
        shapes.setColor(CYAN.r, CYAN.g, CYAN.b, 0.46f * alpha);
        shapes.rect(panel.x + GdxSettingsLayout.HORIZONTAL_INSET + 10f,
                separatorY,
                panel.width - 2f * GdxSettingsLayout.HORIZONTAL_INSET - 20f,
                3f);

        for (int index = 0; index < subpageCount; index++) {
            Rectangle tab = frame.subTab(index);
            boolean active = index == activeSubpage;
            boolean hover = contains(tab, pointer);
            Color border = active ? GOLD : hover ? CYAN_DARK : LINE;
            Color fill = active ? ACTIVE_SUBTAB : TAB;
            GdxUiButtonStyle.drawPalette(shapes, tab.x, tab.y,
                    tab.width, tab.height, border,
                    fill.r, fill.g, fill.b, fill.a,
                    true, hover ? 1f : 0f, false, alpha, false);
        }

        drawFooterButton(shapes, frame.cancelButton(),
                GdxUiButtonStyle.Tone.NEUTRAL, pointer, alpha);
        if (restoreVisible) {
            drawFooterButton(shapes, frame.restoreButton(),
                    GdxUiButtonStyle.Tone.NEUTRAL, pointer, alpha);
        }
        drawFooterButton(shapes, frame.saveButton(),
                GdxUiButtonStyle.Tone.FEATURED, pointer, alpha);
    }

    private static void drawFooterButton(ShapeRenderer shapes,
            Rectangle bounds, GdxUiButtonStyle.Tone tone, Vector2 pointer,
            float alpha) {
        GdxUiButtonStyle.draw(shapes, bounds.x, bounds.y,
                bounds.width, bounds.height, tone, true,
                contains(bounds, pointer) ? 1f : 0f, false, alpha);
    }

    private static boolean contains(Rectangle bounds, Vector2 pointer) {
        return pointer != null && bounds.contains(pointer);
    }

    private static void outerBox(ShapeRenderer shapes, Rectangle bounds,
            Color border, Color fill, float alpha) {
        shapes.setColor(fill.r, fill.g, fill.b, fill.a * alpha);
        roundedRect(shapes, bounds.x, bounds.y, bounds.width, bounds.height,
                14f);
        shapes.setColor(border.r, border.g, border.b, border.a * alpha);
        roundedRectOutline(shapes, bounds.x + 1f, bounds.y + 1f,
                bounds.width - 2f, bounds.height - 2f, 13f, 2f);
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
        quarterArc(shapes, x + width - r, y + height - r, r, 0f, thickness);
        quarterArc(shapes, x + r, y + height - r, r, 90f, thickness);
    }

    private static void quarterArc(ShapeRenderer shapes, float cx, float cy,
            float radius, float startDegrees, float thickness) {
        float previous = (float) Math.toRadians(startDegrees);
        float previousX = cx + (float) Math.cos(previous) * radius;
        float previousY = cy + (float) Math.sin(previous) * radius;
        for (int step = 1; step <= 10; step++) {
            float radians = (float) Math.toRadians(
                    startDegrees + step * 9f);
            float x = cx + (float) Math.cos(radians) * radius;
            float y = cy + (float) Math.sin(radians) * radius;
            shapes.rectLine(previousX, previousY, x, y, thickness);
            previousX = x;
            previousY = y;
        }
    }
}
