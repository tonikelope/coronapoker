/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

/**
 * Resolution-independent glyphs for small GDX controls.
 *
 * <p>These icons deliberately use the active ShapeRenderer rather than
 * enlarging 24 px PNGs.  At 1080p, 1440p, 4K and fractional Windows DPI the
 * geometry is therefore rasterized at the real framebuffer resolution and
 * cannot expose source-image pixels.</p>
 */
final class GdxUiIconRenderer {

    enum Icon {
        EXIT,
        LOG,
        STATISTICS,
        CONTINUE,
        CAMERA,
        SETTINGS
    }

    private static final int CIRCLE_SEGMENTS = 40;

    private GdxUiIconRenderer() {
    }

    static void draw(ShapeRenderer shapes, Icon icon, float centerX,
            float centerY, float size, Color color, float alpha) {
        if (shapes == null || icon == null || color == null
                || !Float.isFinite(centerX) || !Float.isFinite(centerY)
                || !Float.isFinite(size) || size <= 0f
                || !Float.isFinite(alpha) || alpha <= 0f) return;
        float a = MathUtils.clamp(alpha, 0f, 1f) * color.a;
        shapes.setColor(color.r, color.g, color.b, a);
        switch (icon) {
            case EXIT -> drawExit(shapes, centerX, centerY, size);
            case LOG -> drawLog(shapes, centerX, centerY, size);
            case STATISTICS -> drawStatistics(shapes, centerX, centerY, size);
            case CONTINUE -> drawContinue(shapes, centerX, centerY, size);
            case CAMERA -> drawCamera(shapes, centerX, centerY, size);
            case SETTINGS -> drawSettings(shapes, centerX, centerY, size);
            default -> { }
        }
    }

    private static void drawExit(ShapeRenderer shapes, float cx, float cy,
            float size) {
        float stroke = size * 0.115f;
        shapes.rectLine(cx - size * 0.34f, cy - size * 0.34f,
                cx - size * 0.34f, cy + size * 0.34f, stroke);
        shapes.rectLine(cx - size * 0.34f, cy + size * 0.34f,
                cx - size * 0.05f, cy + size * 0.34f, stroke);
        shapes.rectLine(cx - size * 0.34f, cy - size * 0.34f,
                cx - size * 0.05f, cy - size * 0.34f, stroke);
        shapes.rect(cx - size * 0.18f, cy - stroke / 2f,
                size * 0.42f, stroke);
        shapes.triangle(cx + size * 0.40f, cy,
                cx + size * 0.18f, cy + size * 0.22f,
                cx + size * 0.18f, cy - size * 0.22f);
    }

    private static void drawLog(ShapeRenderer shapes, float cx, float cy,
            float size) {
        float left = cx - size * 0.32f;
        float right = cx + size * 0.32f;
        float bottom = cy - size * 0.39f;
        float top = cy + size * 0.39f;
        float fold = size * 0.20f;
        float stroke = size * 0.075f;
        shapes.rectLine(left, bottom, left, top, stroke);
        shapes.rectLine(left, bottom, right, bottom, stroke);
        shapes.rectLine(right, bottom, right, top - fold, stroke);
        shapes.rectLine(left, top, right - fold, top, stroke);
        shapes.rectLine(right - fold, top, right, top - fold, stroke);
        shapes.rectLine(right - fold, top, right - fold, top - fold, stroke);
        for (int row = 0; row < 3; row++) {
            float y = cy + size * (0.12f - row * 0.18f);
            shapes.rectLine(left + size * 0.12f, y,
                    right - size * 0.10f, y, stroke * 0.68f);
        }
    }

    private static void drawStatistics(ShapeRenderer shapes, float cx,
            float cy, float size) {
        float width = size * 0.20f;
        float bottom = cy - size * 0.35f;
        roundedRect(shapes, cx - size * 0.36f, bottom, width,
                size * 0.43f, width * 0.28f);
        roundedRect(shapes, cx - width / 2f, bottom, width,
                size * 0.70f, width * 0.28f);
        roundedRect(shapes, cx + size * 0.16f, bottom, width,
                size * 0.55f, width * 0.28f);
    }

    private static void drawContinue(ShapeRenderer shapes, float cx,
            float cy, float size) {
        ring(shapes, cx, cy, size * 0.34f, size * 0.075f,
                CIRCLE_SEGMENTS);
        shapes.triangle(cx - size * 0.08f, cy - size * 0.18f,
                cx - size * 0.08f, cy + size * 0.18f,
                cx + size * 0.19f, cy);
    }

    private static void drawCamera(ShapeRenderer shapes, float cx, float cy,
            float size) {
        float left = cx - size * 0.40f;
        float right = cx + size * 0.40f;
        float bottom = cy - size * 0.27f;
        float top = cy + size * 0.27f;
        float stroke = size * 0.075f;
        roundedRectOutline(shapes, left, bottom, right - left, top - bottom,
                size * 0.10f, stroke);
        shapes.rectLine(cx - size * 0.16f, top,
                cx - size * 0.10f, top + size * 0.15f, stroke);
        shapes.rectLine(cx - size * 0.10f, top + size * 0.15f,
                cx + size * 0.12f, top + size * 0.15f, stroke);
        shapes.rectLine(cx + size * 0.12f, top + size * 0.15f,
                cx + size * 0.18f, top, stroke);
        ring(shapes, cx, cy, size * 0.17f, stroke, 32);
        shapes.circle(cx, cy, size * 0.045f, 20);
    }

    private static void drawSettings(ShapeRenderer shapes, float cx,
            float cy, float size) {
        // Broad square teeth merged into a heavy annulus read as a mechanical
        // cog even at toolbar size; vector geometry stays crisp at high DPI.
        float gearRadius = size * 0.285f;
        float gearStroke = size * 0.145f;
        float toothRoot = size * 0.285f;
        float toothTip = size * 0.46f;
        float rootHalfWidth = size * 0.090f;
        float tipHalfWidth = size * 0.090f;
        for (int tooth = 0; tooth < 8; tooth++) {
            drawGearTooth(shapes, cx, cy, tooth * MathUtils.PI / 4f,
                    toothRoot, toothTip, rootHalfWidth, tipHalfWidth);
        }
        ring(shapes, cx, cy, gearRadius, gearStroke, CIRCLE_SEGMENTS);
        ring(shapes, cx, cy, size * 0.105f, size * 0.058f, 32);
    }

    private static void drawGearTooth(ShapeRenderer shapes, float cx,
            float cy, float angle, float rootRadius, float tipRadius,
            float rootHalfWidth, float tipHalfWidth) {
        float radialX = MathUtils.cos(angle);
        float radialY = MathUtils.sin(angle);
        float tangentX = -radialY;
        float tangentY = radialX;
        float rootLeftX = cx + radialX * rootRadius
                + tangentX * rootHalfWidth;
        float rootLeftY = cy + radialY * rootRadius
                + tangentY * rootHalfWidth;
        float rootRightX = cx + radialX * rootRadius
                - tangentX * rootHalfWidth;
        float rootRightY = cy + radialY * rootRadius
                - tangentY * rootHalfWidth;
        float tipLeftX = cx + radialX * tipRadius
                + tangentX * tipHalfWidth;
        float tipLeftY = cy + radialY * tipRadius
                + tangentY * tipHalfWidth;
        float tipRightX = cx + radialX * tipRadius
                - tangentX * tipHalfWidth;
        float tipRightY = cy + radialY * tipRadius
                - tangentY * tipHalfWidth;
        shapes.triangle(rootLeftX, rootLeftY, tipLeftX, tipLeftY,
                tipRightX, tipRightY);
        shapes.triangle(rootLeftX, rootLeftY, tipRightX, tipRightY,
                rootRightX, rootRightY);
    }

    private static void ring(ShapeRenderer shapes, float cx, float cy,
            float radius, float thickness, int segments) {
        float previousX = cx + radius;
        float previousY = cy;
        for (int step = 1; step <= segments; step++) {
            float angle = MathUtils.PI2 * step / segments;
            float x = cx + MathUtils.cos(angle) * radius;
            float y = cy + MathUtils.sin(angle) * radius;
            shapes.rectLine(previousX, previousY, x, y, thickness);
            previousX = x;
            previousY = y;
        }
    }

    private static void roundedRect(ShapeRenderer shapes, float x, float y,
            float width, float height, float radius) {
        float r = Math.min(radius, Math.min(width, height) / 2f);
        shapes.rect(x + r, y, width - 2f * r, height);
        shapes.rect(x, y + r, width, height - 2f * r);
        shapes.circle(x + r, y + r, r, 16);
        shapes.circle(x + width - r, y + r, r, 16);
        shapes.circle(x + r, y + height - r, r, 16);
        shapes.circle(x + width - r, y + height - r, r, 16);
    }

    private static void roundedRectOutline(ShapeRenderer shapes, float x,
            float y, float width, float height, float radius,
            float thickness) {
        float r = Math.min(radius, Math.min(width, height) / 2f);
        shapes.rectLine(x + r, y, x + width - r, y, thickness);
        shapes.rectLine(x + r, y + height, x + width - r, y + height,
                thickness);
        shapes.rectLine(x, y + r, x, y + height - r, thickness);
        shapes.rectLine(x + width, y + r, x + width, y + height - r,
                thickness);
        arc(shapes, x + r, y + r, r, MathUtils.PI, thickness);
        arc(shapes, x + width - r, y + r, r, MathUtils.PI * 1.5f,
                thickness);
        arc(shapes, x + width - r, y + height - r, r, 0f, thickness);
        arc(shapes, x + r, y + height - r, r, MathUtils.PI / 2f,
                thickness);
    }

    private static void arc(ShapeRenderer shapes, float cx, float cy,
            float radius, float start, float thickness) {
        float previousX = cx + MathUtils.cos(start) * radius;
        float previousY = cy + MathUtils.sin(start) * radius;
        for (int step = 1; step <= 8; step++) {
            float angle = start + MathUtils.PI / 2f * step / 8f;
            float x = cx + MathUtils.cos(angle) * radius;
            float y = cy + MathUtils.sin(angle) * radius;
            shapes.rectLine(previousX, previousY, x, y, thickness);
            previousX = x;
            previousY = y;
        }
    }
}
