/* Copyright (C) 2026 tonikelope. GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;

/** Oblique shuffle on a flat table. Only the deck uses this camera. */
final class GdxShuffleOverlayCamera {
    private final Matrix4 lens = new Matrix4();
    private final Matrix4 view;
    private final Rectangle rest = new Rectangle();
    private final Vector3 point = new Vector3();
    private final float distance;
    private float lastAspect = Float.NaN;
    private int lastAngle;

    GdxShuffleOverlayCamera() {
        float reach = (float) Math.hypot(GdxRiffleShuffle.PREVIEW_EYE_X, GdxRiffleShuffle.PREVIEW_EYE_Z);
        Vector3 eye = new Vector3(0f, GdxRiffleShuffle.PREVIEW_EYE_Y, reach);
        distance = eye.len();
        view = new Matrix4().setToLookAt(eye, Vector3.Zero, Vector3.Y);
    }

    void project(Matrix4 result, float width, float height, float x, float y,
            float length, float aspect, int angle) {
        if (lastAngle != angle || lastAspect != aspect) {
            lens.setToProjection(.5f, 8f, 90f, 1f).mul(view).rotate(Vector3.Y, -angle);
            GdxShuffleGeometry.projectRestBounds(lens, 2f, 2f, aspect, rest, point);
            lastAngle = angle;
            lastAspect = aspect;
        }
        // A 90 degree lens gives one world unit per distance at the origin.
        float sx = 2f * length * distance / width, sy = 2f * length * distance / height;
        float offsetX = rest.x + rest.width * .5f - 1f, offsetY = rest.y + rest.height * .5f - 1f;
        result.setToTranslation(2f * x / width - 1f - sx * offsetX,
                2f * y / height - 1f - sy * offsetY, 0f).scale(sx, sy, 1f).mul(lens);
    }
}
