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

    private static final Color CYAN = new Color(0x36d9ffff);
    private static final Color CYAN_DARK = new Color(0x176b83ff);
    private static final Color GOLD = new Color(0xffe07aff);
    private static final Color LINE = new Color(0x31445fff);
    private static final Color PANEL = new Color(0x071321f2);
    private static final Color CONTENT = new Color(0x081828d9);
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
            outerBox(shapes, tab,
                    active ? CYAN : hover ? CYAN_DARK : LINE,
                    active ? ACTIVE_TAB : TAB, alpha);
            if (active) {
                shapes.setColor(GOLD.r, GOLD.g, GOLD.b, alpha);
                roundedRect(shapes, tab.x + 10f, tab.y + 2f,
                        tab.width - 20f, 3f, 2f);
            }
        }

        for (int index = 0; index < subpageCount; index++) {
            Rectangle tab = frame.subTab(index);
            boolean active = index == activeSubpage;
            boolean hover = contains(tab, pointer);
            outerBox(shapes, tab,
                    active ? GOLD : hover ? CYAN_DARK : LINE,
                    active ? ACTIVE_SUBTAB : TAB, alpha);
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
        if (bounds.height <= 90f && bounds.width > 90f) {
            float inset = 14f;
            float sheenBottom = bounds.y + bounds.height * 0.54f;
            float sheenTop = bounds.y + bounds.height - 9f;
            shapes.rect(bounds.x + inset, sheenBottom,
                    bounds.width - inset * 2f, sheenTop - sheenBottom,
                    new Color(1f, 1f, 1f, 0.018f * alpha),
                    new Color(1f, 1f, 1f, 0.018f * alpha),
                    new Color(1f, 1f, 1f, 0.115f * alpha),
                    new Color(1f, 1f, 1f, 0.115f * alpha));
        }
        shapes.setColor(1f, 1f, 1f, 0.055f * alpha);
        shapes.rect(bounds.x + 16f,
                bounds.y + bounds.height - 7f,
                bounds.width - 32f, 1.5f);
    }

    private static void roundedRect(ShapeRenderer shapes, float x, float y,
            float width, float height, float radius) {
        float r = Math.min(radius, Math.min(width, height) / 2f);
        shapes.rect(x + r, y, width - 2f * r, height);
        shapes.rect(x, y + r, width, height - 2f * r);
        shapes.circle(x + r, y + r, r, 24);
        shapes.circle(x + width - r, y + r, r, 24);
        shapes.circle(x + width - r, y + height - r, r, 24);
        shapes.circle(x + r, y + height - r, r, 24);
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
