/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Coverage-antialiased rounded rectangles for renderer-owned UI chrome.
 *
 * <p>{@link ShapeRenderer} tessellates curves but does not calculate partial
 * pixel coverage when the framebuffer has no MSAA.  This renderer keeps the
 * interior exactly flat and adds a narrow alpha-gradient fringe around the
 * mathematical boundary.  The same geometry is used for fills and strokes,
 * so buttons, dialogs and input fields do not depend on the global MSAA
 * setting for clean corners.</p>
 */
final class GdxAntialiasedRoundedRect {

    static final int CORNER_SEGMENTS = 18;
    private static final int CIRCLE_SEGMENTS = CORNER_SEGMENTS * 4;
    static final float TARGET_PHYSICAL_FEATHER_PIXELS = 1.25f;
    private static final float REFERENCE_WORLD_WIDTH = 1920f;
    private static final float REFERENCE_WORLD_HEIGHT = 1080f;
    private static final Color FULL = new Color();
    private static final Color CLEAR = new Color();
    private static final float[][] CORNER_X =
            new float[4][CORNER_SEGMENTS + 1];
    private static final float[][] CORNER_Y =
            new float[4][CORNER_SEGMENTS + 1];
    private static final float[] CIRCLE_X =
            new float[CIRCLE_SEGMENTS + 1];
    private static final float[] CIRCLE_Y =
            new float[CIRCLE_SEGMENTS + 1];

    static {
        for (int corner = 0; corner < 4; corner++) {
            float startDegrees = 180f + corner * 90f;
            for (int step = 0; step <= CORNER_SEGMENTS; step++) {
                float radians = (float) Math.toRadians(startDegrees
                        + 90f * step / CORNER_SEGMENTS);
                CORNER_X[corner][step] = (float) Math.cos(radians);
                CORNER_Y[corner][step] = (float) Math.sin(radians);
            }
        }
        for (int step = 0; step <= CIRCLE_SEGMENTS; step++) {
            float radians = (float) (Math.PI * 2d * step
                    / CIRCLE_SEGMENTS);
            CIRCLE_X[step] = (float) Math.cos(radians);
            CIRCLE_Y[step] = (float) Math.sin(radians);
        }
    }

    private GdxAntialiasedRoundedRect() {
    }

    /**
     * Draws a coverage-antialiased filled circle using the same physical-pixel
     * fringe as the rounded UI chrome. A dedicated continuous contour avoids
     * the internal quadrant seams that a maximally rounded rectangle can show
     * at small indicator radii.
     */
    static void fillCircle(ShapeRenderer shapes, float centerX, float centerY,
            float radius) {
        if (!Float.isFinite(centerX) || !Float.isFinite(centerY)
                || !Float.isFinite(radius) || radius <= 0f) return;
        rememberColor(shapes);
        float feather = Math.min(coverageWidth(), radius * 1.5f);
        float half = feather * 0.5f;
        circleBand(shapes, centerX, centerY,
                radius + half, radius - half, CLEAR, FULL);
        hardCircle(shapes, centerX, centerY, radius - half, FULL);
        shapes.setColor(FULL);
    }

    static void fill(ShapeRenderer shapes, float x, float y, float width,
            float height, float radius) {
        if (!valid(width, height)) return;
        float safeRadius = safeRadius(width, height, radius);
        if (safeRadius <= 0f) {
            shapes.rect(x, y, width, height);
            return;
        }
        rememberColor(shapes);
        float feather = Math.min(coverageWidth(), safeRadius * 1.5f);
        float half = feather * 0.5f;
        drawContourBand(shapes, x, y, width, height, safeRadius,
                half, -half, CLEAR, FULL);
        hardFill(shapes, x + half, y + half,
                width - feather, height - feather,
                Math.max(0f, safeRadius - half), FULL);
        shapes.setColor(FULL);
    }

    static void outline(ShapeRenderer shapes, float x, float y, float width,
            float height, float radius, float thickness) {
        if (!valid(width, height) || !Float.isFinite(thickness)
                || thickness <= 0f) return;
        float safeRadius = safeRadius(width, height, radius);
        if (safeRadius <= 0f) return;
        rememberColor(shapes);
        float feather = Math.min(coverageWidth(),
                Math.max(0.45f, thickness * 0.80f));
        float strokeHalf = thickness * 0.5f;
        float fringeHalf = feather * 0.5f;
        float outerClear = strokeHalf + fringeHalf;
        float outerFull = strokeHalf - fringeHalf;
        float innerFull = -strokeHalf + fringeHalf;
        float innerClear = -strokeHalf - fringeHalf;
        if (outerFull < innerFull) {
            outerFull = 0f;
            innerFull = 0f;
        }
        drawContourBand(shapes, x, y, width, height, safeRadius,
                outerClear, outerFull, CLEAR, FULL);
        if (outerFull > innerFull) {
            drawContourBand(shapes, x, y, width, height, safeRadius,
                    outerFull, innerFull, FULL, FULL);
        }
        if (safeRadius + innerClear > 0f) {
            drawContourBand(shapes, x, y, width, height, safeRadius,
                    innerFull, innerClear, FULL, CLEAR);
        }
        shapes.setColor(FULL);
    }

    static float coverageWidthForBackBufferHeight(int backBufferHeight) {
        int referenceWidth = backBufferHeight <= 0 ? 0
                : Math.round(backBufferHeight * REFERENCE_WORLD_WIDTH
                        / REFERENCE_WORLD_HEIGHT);
        return coverageWidthForBackBuffer(referenceWidth, backBufferHeight);
    }

    static float coverageWidthForBackBuffer(int backBufferWidth,
            int backBufferHeight) {
        if (backBufferWidth <= 0 || backBufferHeight <= 0) {
            return TARGET_PHYSICAL_FEATHER_PIXELS;
        }
        float scale = Math.min(backBufferWidth / REFERENCE_WORLD_WIDTH,
                backBufferHeight / REFERENCE_WORLD_HEIGHT);
        if (!Float.isFinite(scale) || scale <= 0f) {
            return TARGET_PHYSICAL_FEATHER_PIXELS;
        }
        float worldWidth = TARGET_PHYSICAL_FEATHER_PIXELS / scale;
        return Math.max(0.5f, Math.min(2.5f, worldWidth));
    }

    private static float coverageWidth() {
        int width = Gdx.graphics == null
                ? Math.round(REFERENCE_WORLD_WIDTH)
                : Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics == null
                ? Math.round(REFERENCE_WORLD_HEIGHT)
                : Gdx.graphics.getBackBufferHeight();
        return coverageWidthForBackBuffer(width, height);
    }

    private static void rememberColor(ShapeRenderer shapes) {
        FULL.set(shapes.getColor());
        CLEAR.set(FULL.r, FULL.g, FULL.b, 0f);
    }

    private static void drawContourBand(ShapeRenderer shapes,
            float x, float y, float width, float height, float radius,
            float outerOffset, float innerOffset, Color outer, Color inner) {
        if (outerOffset < innerOffset) return;
        float outerRadius = radius + outerOffset;
        float innerRadius = radius + innerOffset;
        if (outerRadius <= 0f || innerRadius < 0f) return;
        float left = x + radius;
        float right = x + width - radius;
        float bottom = y + radius;
        float top = y + height - radius;

        quad(shapes,
                left, y - outerOffset, outer,
                right, y - outerOffset, outer,
                right, y - innerOffset, inner,
                left, y - innerOffset, inner);
        quad(shapes,
                right, y + height + outerOffset, outer,
                left, y + height + outerOffset, outer,
                left, y + height + innerOffset, inner,
                right, y + height + innerOffset, inner);
        quad(shapes,
                x - outerOffset, top, outer,
                x - outerOffset, bottom, outer,
                x - innerOffset, bottom, inner,
                x - innerOffset, top, inner);
        quad(shapes,
                x + width + outerOffset, bottom, outer,
                x + width + outerOffset, top, outer,
                x + width + innerOffset, top, inner,
                x + width + innerOffset, bottom, inner);

        cornerBand(shapes, left, bottom, radius,
                outerOffset, innerOffset, 0, outer, inner);
        cornerBand(shapes, right, bottom, radius,
                outerOffset, innerOffset, 1, outer, inner);
        cornerBand(shapes, right, top, radius,
                outerOffset, innerOffset, 2, outer, inner);
        cornerBand(shapes, left, top, radius,
                outerOffset, innerOffset, 3, outer, inner);
    }

    private static void cornerBand(ShapeRenderer shapes, float centerX,
            float centerY, float radius, float outerOffset,
            float innerOffset, int corner, Color outer, Color inner) {
        float outerRadius = radius + outerOffset;
        float innerRadius = radius + innerOffset;
        for (int step = 1; step <= CORNER_SEGMENTS; step++) {
            float outerPreviousX = centerX
                    + CORNER_X[corner][step - 1] * outerRadius;
            float outerPreviousY = centerY
                    + CORNER_Y[corner][step - 1] * outerRadius;
            float outerCurrentX = centerX
                    + CORNER_X[corner][step] * outerRadius;
            float outerCurrentY = centerY
                    + CORNER_Y[corner][step] * outerRadius;
            float innerPreviousX = centerX
                    + CORNER_X[corner][step - 1] * innerRadius;
            float innerPreviousY = centerY
                    + CORNER_Y[corner][step - 1] * innerRadius;
            float innerCurrentX = centerX
                    + CORNER_X[corner][step] * innerRadius;
            float innerCurrentY = centerY
                    + CORNER_Y[corner][step] * innerRadius;
            quad(shapes,
                    outerPreviousX, outerPreviousY, outer,
                    outerCurrentX, outerCurrentY, outer,
                    innerCurrentX, innerCurrentY, inner,
                    innerPreviousX, innerPreviousY, inner);
        }
    }

    private static void circleBand(ShapeRenderer shapes, float centerX,
            float centerY, float outerRadius, float innerRadius,
            Color outer, Color inner) {
        for (int step = 1; step <= CIRCLE_SEGMENTS; step++) {
            float outerPreviousX = centerX
                    + CIRCLE_X[step - 1] * outerRadius;
            float outerPreviousY = centerY
                    + CIRCLE_Y[step - 1] * outerRadius;
            float outerCurrentX = centerX + CIRCLE_X[step] * outerRadius;
            float outerCurrentY = centerY + CIRCLE_Y[step] * outerRadius;
            float innerPreviousX = centerX
                    + CIRCLE_X[step - 1] * innerRadius;
            float innerPreviousY = centerY
                    + CIRCLE_Y[step - 1] * innerRadius;
            float innerCurrentX = centerX + CIRCLE_X[step] * innerRadius;
            float innerCurrentY = centerY + CIRCLE_Y[step] * innerRadius;
            quad(shapes,
                    outerPreviousX, outerPreviousY, outer,
                    outerCurrentX, outerCurrentY, outer,
                    innerCurrentX, innerCurrentY, inner,
                    innerPreviousX, innerPreviousY, inner);
        }
    }

    private static void hardCircle(ShapeRenderer shapes, float centerX,
            float centerY, float radius, Color color) {
        for (int step = 1; step <= CIRCLE_SEGMENTS; step++) {
            shapes.triangle(centerX, centerY,
                    centerX + CIRCLE_X[step - 1] * radius,
                    centerY + CIRCLE_Y[step - 1] * radius,
                    centerX + CIRCLE_X[step] * radius,
                    centerY + CIRCLE_Y[step] * radius,
                    color, color, color);
        }
    }

    private static void quad(ShapeRenderer shapes,
            float x0, float y0, Color color0,
            float x1, float y1, Color color1,
            float x2, float y2, Color color2,
            float x3, float y3, Color color3) {
        shapes.triangle(x0, y0, x1, y1, x2, y2,
                color0, color1, color2);
        shapes.triangle(x0, y0, x2, y2, x3, y3,
                color0, color2, color3);
    }

    private static void hardFill(ShapeRenderer shapes, float x, float y,
            float width, float height, float radius, Color color) {
        if (!valid(width, height)) return;
        shapes.setColor(color);
        float safeRadius = safeRadius(width, height, radius);
        if (safeRadius <= 0f) {
            shapes.rect(x, y, width, height);
            return;
        }
        shapes.rect(x + safeRadius, y, width - 2f * safeRadius, height);
        shapes.rect(x, y + safeRadius, safeRadius,
                height - 2f * safeRadius);
        shapes.rect(x + width - safeRadius, y + safeRadius, safeRadius,
                height - 2f * safeRadius);
        hardCorner(shapes, x + safeRadius, y + safeRadius,
                safeRadius, 0, color);
        hardCorner(shapes, x + width - safeRadius, y + safeRadius,
                safeRadius, 1, color);
        hardCorner(shapes, x + width - safeRadius,
                y + height - safeRadius, safeRadius, 2, color);
        hardCorner(shapes, x + safeRadius, y + height - safeRadius,
                safeRadius, 3, color);
    }

    private static void hardCorner(ShapeRenderer shapes, float centerX,
            float centerY, float radius, int corner, Color color) {
        float previousX = centerX + CORNER_X[corner][0] * radius;
        float previousY = centerY + CORNER_Y[corner][0] * radius;
        for (int step = 1; step <= CORNER_SEGMENTS; step++) {
            float currentX = centerX + CORNER_X[corner][step] * radius;
            float currentY = centerY + CORNER_Y[corner][step] * radius;
            shapes.triangle(centerX, centerY, previousX, previousY,
                    currentX, currentY, color, color, color);
            previousX = currentX;
            previousY = currentY;
        }
    }

    private static float safeRadius(float width, float height, float radius) {
        if (!Float.isFinite(radius)) return 0f;
        return Math.max(0f, Math.min(radius, Math.min(width, height) * 0.5f));
    }

    private static boolean valid(float width, float height) {
        return Float.isFinite(width) && Float.isFinite(height)
                && width > 0f && height > 0f;
    }
}
