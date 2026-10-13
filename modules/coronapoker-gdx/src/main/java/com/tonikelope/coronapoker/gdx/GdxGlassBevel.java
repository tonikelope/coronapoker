/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/** Smooth rounded glass lighting without detached edge rails. */
final class GdxGlassBevel {

    private GdxGlassBevel() {
    }

    /**
     * Adds a continuous vertical light field clipped to the rounded
     * silhouette. Earlier versions drew explicit upper/lower lines; those
     * became visible as a second border on bright HUD and dialog buttons.
     * Narrow clipped strips keep the material dimensional without introducing
     * a separate rail or a rectangular patch.
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
        int strips = Math.min(96, Math.max(24, (int) Math.ceil(height)));
        float stripHeight = height / strips;
        for (int strip = 0; strip < strips; strip++) {
            float localBottom = strip * stripHeight;
            float localTop = Math.min(height, localBottom + stripHeight);
            float midpoint = (localBottom + localTop) * 0.5f;
            float position = midpoint / height;
            float light = smoothstep(0.46f, 1f, position) * strength;
            float shade = (1f - smoothstep(0f, 0.48f, position))
                    * strength * 0.34f;
            if (light <= 0.001f && shade <= 0.001f) continue;

            float inset = Math.max(roundedInset(localBottom, height, r),
                    roundedInset(localTop, height, r));
            float stripWidth = width - 2f * inset;
            if (stripWidth <= 0f) continue;
            if (light > shade) {
                shapes.setColor(1f, 1f, 1f, light * opacity);
            } else {
                shapes.setColor(0f, 0f, 0f, shade * opacity);
            }
            shapes.rect(x + inset, y + localBottom,
                    stripWidth, localTop - localBottom + 0.02f);
        }
    }

    private static float roundedInset(float localY, float height,
            float radius) {
        if (radius <= 0f) return 0f;
        float edgeDistance = Math.min(localY, height - localY);
        if (edgeDistance >= radius) return 0f;
        float fromCenter = radius - Math.max(0f, edgeDistance);
        return radius - (float) Math.sqrt(Math.max(0f,
                radius * radius - fromCenter * fromCenter));
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Math.max(0f, Math.min(1f,
                (value - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }
}
