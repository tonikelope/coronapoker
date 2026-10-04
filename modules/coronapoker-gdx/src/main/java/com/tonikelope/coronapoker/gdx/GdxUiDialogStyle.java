package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/** Shared glass material for every renderer-owned GDX dialog. */
final class GdxUiDialogStyle {

    // Match the 80% glass used by the main menu and statistics surfaces.
    // Dialogs get their hierarchy from the dimmed backdrop and outline, not
    // from turning the panel into an almost opaque second visual language.
    static final int PANEL_RGBA = 0x071321cc;
    static final int INSET_RGBA = 0x08182870;
    private static final Color PANEL = new Color(PANEL_RGBA);
    private static final Color INSET = new Color(INSET_RGBA);
    private static final Color LINE = new Color(0x31445fff);

    private GdxUiDialogStyle() {
    }

    static void drawBackdrop(ShapeRenderer shapes, float width, float height,
            float alpha) {
        shapes.setColor(0.008f, 0.018f, 0.038f, 0.68f * alpha);
        shapes.rect(0f, 0f, width, height);
    }

    static void drawPanel(ShapeRenderer shapes, float x, float y,
            float width, float height, Color accent, float alpha) {
        drawPanel(shapes, x, y, width, height, PANEL, accent, alpha);
    }

    static void drawPanel(ShapeRenderer shapes, float x, float y,
            float width, float height, Color fill, Color accent,
            float alpha) {
        shapes.setColor(fill.r, fill.g, fill.b, fill.a * alpha);
        roundedRect(shapes, x, y, width, height, 18f);
        shapes.setColor(accent.r, accent.g, accent.b, 0.92f * alpha);
        roundedRectOutline(shapes, x + 1f, y + 1f,
                width - 2f, height - 2f, 17f, 2f);
        shapes.setColor(accent.r, accent.g, accent.b, 0.72f * alpha);
        shapes.rect(x + 28f, y + height - 10f, width - 56f, 3f);
    }

    static void drawInset(ShapeRenderer shapes, float x, float y,
            float width, float height, float alpha) {
        drawInset(shapes, x, y, width, height, INSET, LINE, alpha);
    }

    static void drawInset(ShapeRenderer shapes, float x, float y,
            float width, float height, Color fill, Color border,
            float alpha) {
        shapes.setColor(fill.r, fill.g, fill.b, fill.a * alpha);
        roundedRect(shapes, x, y, width, height, 14f);
        shapes.setColor(border.r, border.g, border.b,
                border.a * 0.82f * alpha);
        roundedRectOutline(shapes, x + 1f, y + 1f,
                width - 2f, height - 2f, 13f, 1.5f);
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

    private static void quarterArc(ShapeRenderer shapes, float centerX,
            float centerY, float radius, float startDegrees,
            float thickness) {
        float previous = (float) Math.toRadians(startDegrees);
        float previousX = centerX + (float) Math.cos(previous) * radius;
        float previousY = centerY + (float) Math.sin(previous) * radius;
        for (int step = 1; step <= 10; step++) {
            float radians = (float) Math.toRadians(
                    startDegrees + step * 9f);
            float nextX = centerX + (float) Math.cos(radians) * radius;
            float nextY = centerY + (float) Math.sin(radians) * radius;
            shapes.rectLine(previousX, previousY, nextX, nextY, thickness);
            previousX = nextX;
            previousY = nextY;
        }
    }
}
