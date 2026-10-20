/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/** Smooth rounded glass lighting without detached edge rails. */
final class GdxGlassBevel {

    private static final Color SHADE_BOTTOM =
            new Color(0f, 0f, 0f, 0f);
    private static final Color SHADE_TOP = new Color(0f, 0f, 0f, 0f);
    private static final Color LIGHT_BOTTOM =
            new Color(1f, 1f, 1f, 0f);
    private static final Color LIGHT_TOP = new Color(1f, 1f, 1f, 0f);

    private GdxGlassBevel() {
    }

    /**
     * Adds a continuous vertical light field clipped to the rounded
     * silhouette. Earlier versions drew explicit upper/lower lines; those
     * became visible as a second border on bright HUD and dialog buttons.
     * A connected gradient mesh keeps the material dimensional without
     * introducing a separate rail or a rectangular patch.  Every band shares
     * its exact boundary vertices with the next one, avoiding the dark
     * staircase gaps that independent clipped rectangles left at rounded
     * corners.
     */
    static void draw(ShapeRenderer shapes, float x, float y, float width,
            float height, float radius, float topAlpha, float alpha) {
        if (shapes == null || !Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(width) || width <= 0f
                || !Float.isFinite(height) || height <= 0f
                || !Float.isFinite(radius) || !Float.isFinite(alpha)
                || alpha <= 0f) return;
        float r = Math.max(0f, Math.min(radius,
                Math.min(width, height) / 2f));
        float opacity = Math.max(0f, Math.min(1f, alpha));
        float strength = Math.max(0f, Math.min(1f, topAlpha));
        int bands = bandCount(height);
        drawLayer(shapes, x, y, width, height, r, bands,
                strength, opacity, false);
        drawLayer(shapes, x, y, width, height, r, bands,
                strength, opacity, true);
    }

    private static void drawLayer(ShapeRenderer shapes, float x, float y,
            float width, float height, float radius, int bands,
            float strength, float opacity, boolean lightLayer) {
        Color bottomColor = lightLayer ? LIGHT_BOTTOM : SHADE_BOTTOM;
        Color topColor = lightLayer ? LIGHT_TOP : SHADE_TOP;
        for (int band = 0; band < bands; band++) {
            float localBottom = bandBoundary(height, band, bands);
            float localTop = bandBoundary(height, band + 1, bands);
            float bottomPosition = localBottom / height;
            float topPosition = localTop / height;
            float lowerCoverage = layerAlpha(bottomPosition, strength,
                    opacity, lightLayer);
            float upperCoverage = layerAlpha(topPosition, strength,
                    opacity, lightLayer);
            if (lowerCoverage <= 0.001f && upperCoverage <= 0.001f) continue;

            float bottomInset = roundedInset(localBottom, height, radius);
            float topInset = roundedInset(localTop, height, radius);
            float leftBottom = x + bottomInset;
            float rightBottom = x + width - bottomInset;
            float leftTop = x + topInset;
            float rightTop = x + width - topInset;
            if (rightBottom < leftBottom || rightTop < leftTop) continue;

            bottomColor.a = lowerCoverage;
            topColor.a = upperCoverage;
            shapes.triangle(leftBottom, y + localBottom,
                    rightBottom, y + localBottom,
                    rightTop, y + localTop,
                    bottomColor, bottomColor, topColor);
            shapes.triangle(leftBottom, y + localBottom,
                    rightTop, y + localTop,
                    leftTop, y + localTop,
                    bottomColor, topColor, topColor);
        }
    }

    static int bandCount(float height) {
        return Math.min(96, Math.max(24, (int) Math.ceil(height)));
    }

    static float bandBoundary(float height, int boundary, int bands) {
        if (bands <= 0) return 0f;
        if (boundary <= 0) return 0f;
        if (boundary >= bands) return height;
        return height * boundary / bands;
    }

    static float roundedInset(float localY, float height,
            float radius) {
        if (radius <= 0f) return 0f;
        float edgeDistance = Math.min(localY, height - localY);
        if (edgeDistance >= radius) return 0f;
        float fromCenter = radius - Math.max(0f, edgeDistance);
        return radius - (float) Math.sqrt(Math.max(0f,
                radius * radius - fromCenter * fromCenter));
    }

    static float layerAlpha(float position, float strength, float opacity,
            boolean lightLayer) {
        if (lightLayer) {
            return smoothstep(0.46f, 1f, position) * strength * opacity;
        }
        return (1f - smoothstep(0f, 0.48f, position))
                * strength * 0.34f * opacity;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Math.max(0f, Math.min(1f,
                (value - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }
}
