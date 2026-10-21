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
        ADD,
        PLAYERS,
        EXIT,
        LOG,
        STATISTICS,
        CONTINUE,
        CAMERA,
        CLOSE,
        SETTINGS,
        INFO,
        BLOCKED
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
            case ADD -> drawAdd(shapes, centerX, centerY, size);
            case PLAYERS -> drawPlayers(shapes, centerX, centerY, size);
            case EXIT -> drawExit(shapes, centerX, centerY, size);
            case LOG -> drawLog(shapes, centerX, centerY, size);
            case STATISTICS -> drawStatistics(shapes, centerX, centerY, size);
            case CONTINUE -> drawContinue(shapes, centerX, centerY, size);
            case CAMERA -> drawCamera(shapes, centerX, centerY, size);
            case CLOSE -> drawClose(shapes, centerX, centerY, size);
            case SETTINGS -> drawSettings(shapes, centerX, centerY, size);
            case INFO -> drawInfo(shapes, centerX, centerY, size);
            case BLOCKED -> drawBlocked(shapes, centerX, centerY, size);
            default -> { }
        }
    }

    private static void drawAdd(ShapeRenderer shapes, float cx, float cy,
            float size) {
        float stroke = size * 0.18f;
        roundedRect(shapes, cx - stroke / 2f, cy - size * 0.45f,
                stroke, size * 0.90f, stroke * 0.44f);
        roundedRect(shapes, cx - size * 0.45f, cy - stroke / 2f,
                size * 0.90f, stroke, stroke * 0.44f);
    }

    private static void drawPlayers(ShapeRenderer shapes, float cx, float cy,
            float size) {
        float head = size * 0.14f;
        shapes.circle(cx, cy + size * 0.19f, head, 32);
        shapes.circle(cx - size * 0.29f, cy + size * 0.12f,
                head * 0.82f, 28);
        shapes.circle(cx + size * 0.29f, cy + size * 0.12f,
                head * 0.82f, 28);
        roundedRect(shapes, cx - size * 0.22f, cy - size * 0.36f,
                size * 0.44f, size * 0.39f, size * 0.15f);
        roundedRect(shapes, cx - size * 0.47f, cy - size * 0.31f,
                size * 0.30f, size * 0.30f, size * 0.12f);
        roundedRect(shapes, cx + size * 0.17f, cy - size * 0.31f,
                size * 0.30f, size * 0.30f, size * 0.12f);
    }

    private static void drawExit(ShapeRenderer shapes, float cx, float cy,
            float size) {
        float stroke = size * 0.105f;
        float left = cx - size * 0.36f;
        float bottom = cy - size * 0.35f;
        float top = cy + size * 0.35f;
        shapes.rect(left - stroke / 2f, bottom,
                stroke, top - bottom);
        shapes.rect(left, top - stroke, size * 0.31f, stroke);
        shapes.rect(left, bottom, size * 0.31f, stroke);
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
        shapes.rect(left - stroke / 2f, bottom,
                stroke, top - bottom);
        shapes.rect(left, bottom - stroke / 2f,
                right - left, stroke);
        shapes.rect(right - stroke / 2f, bottom,
                stroke, top - fold - bottom);
        shapes.rect(left, top - stroke / 2f,
                right - fold - left, stroke);
        shapes.rect(right - fold - stroke / 2f, top - fold,
                stroke, fold);
        shapes.triangle(right - fold, top,
                right, top - fold,
                right - fold, top - fold);
        for (int row = 0; row < 3; row++) {
            float y = cy + size * (0.12f - row * 0.18f);
            float lineStroke = stroke * 0.68f;
            shapes.rect(left + size * 0.12f, y - lineStroke / 2f,
                    size * 0.42f, lineStroke);
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
        float shoulderTop = top + size * 0.15f;
        shapes.triangle(cx - size * 0.18f, top,
                cx - size * 0.10f, shoulderTop,
                cx + size * 0.12f, shoulderTop);
        shapes.triangle(cx - size * 0.18f, top,
                cx + size * 0.12f, shoulderTop,
                cx + size * 0.20f, top);
        ring(shapes, cx, cy, size * 0.17f, stroke, 32);
    }

    /**
     * Geometrically centred close mark shared by every dialog.  A font glyph
     * is centred by its advance and baseline rather than by its visible ink,
     * which made the multiplication sign look low and slightly off-axis in
     * compact close buttons.  Two equal, rounded strokes have no such font
     * metrics and remain centred at every DPI.
     */
    private static void drawClose(ShapeRenderer shapes, float cx, float cy,
            float size) {
        float arm = size * 0.32f;
        float stroke = Math.max(2f, size * 0.13f);
        shapes.rectLine(cx - arm, cy - arm, cx + arm, cy + arm, stroke);
        shapes.rectLine(cx - arm, cy + arm, cx + arm, cy - arm, stroke);
        float cap = stroke / 2f;
        shapes.circle(cx - arm, cy - arm, cap, 20);
        shapes.circle(cx + arm, cy + arm, cap, 20);
        shapes.circle(cx - arm, cy + arm, cap, 20);
        shapes.circle(cx + arm, cy - arm, cap, 20);
    }

    private static void drawSettings(ShapeRenderer shapes, float cx,
            float cy, float size) {
        // One continuous annulus and eight tapered teeth avoid the doubled
        // rings and overlapping round caps that made the old cog look noisy.
        float gearRadius = size * 0.255f;
        float gearStroke = size * 0.135f;
        float toothRoot = size * 0.275f;
        float toothTip = size * 0.445f;
        float rootHalfWidth = size * 0.085f;
        float tipHalfWidth = size * 0.065f;
        for (int tooth = 0; tooth < 8; tooth++) {
            drawGearTooth(shapes, cx, cy, tooth * MathUtils.PI / 4f,
                    toothRoot, toothTip, rootHalfWidth, tipHalfWidth);
        }
        ring(shapes, cx, cy, gearRadius, gearStroke, CIRCLE_SEGMENTS);
    }

    private static void drawInfo(ShapeRenderer shapes, float cx, float cy,
            float size) {
        ring(shapes, cx, cy, size * 0.34f, size * 0.09f,
                CIRCLE_SEGMENTS);
        shapes.circle(cx, cy + size * 0.15f, size * 0.055f, 20);
        roundedRect(shapes, cx - size * 0.055f, cy - size * 0.22f,
                size * 0.11f, size * 0.27f, size * 0.045f);
    }

    private static void drawBlocked(ShapeRenderer shapes, float cx, float cy,
            float size) {
        float stroke = size * 0.12f;
        ring(shapes, cx, cy, size * 0.34f, stroke, CIRCLE_SEGMENTS);
        shapes.rectLine(cx - size * 0.25f, cy + size * 0.25f,
                cx + size * 0.25f, cy - size * 0.25f, stroke);
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
        float outer = radius + thickness / 2f;
        float inner = Math.max(0f, radius - thickness / 2f);
        float previousOuterX = cx + outer;
        float previousOuterY = cy;
        float previousInnerX = cx + inner;
        float previousInnerY = cy;
        for (int step = 1; step <= segments; step++) {
            float angle = MathUtils.PI2 * step / segments;
            float outerX = cx + MathUtils.cos(angle) * outer;
            float outerY = cy + MathUtils.sin(angle) * outer;
            float innerX = cx + MathUtils.cos(angle) * inner;
            float innerY = cy + MathUtils.sin(angle) * inner;
            shapes.triangle(previousOuterX, previousOuterY,
                    outerX, outerY, innerX, innerY);
            shapes.triangle(previousOuterX, previousOuterY,
                    innerX, innerY, previousInnerX, previousInnerY);
            previousOuterX = outerX;
            previousOuterY = outerY;
            previousInnerX = innerX;
            previousInnerY = innerY;
        }
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
