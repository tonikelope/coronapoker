/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.ConvexHull;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;

/** Keeps the local position marker near its seat, outside the shuffle and the two chip banks. */
final class GdxShuffleMarkerPlacement {
    private final ConvexHull hull = new ConvexHull();
    private final float[] corners = new float[24];
    private final Vector3 point = new Vector3();
    private FloatArray outline;
    private final Rectangle leftBank = new Rectangle(), rightBank = new Rectangle();
    private final Vector2 result = new Vector2();
    private float baseX = Float.NaN, baseY, radiusX, radiusY;
    private float lowX, highX, bestDistance;

    void project(Matrix4 model, float aspect, float width, float height) {
        // Outer packets lie flat at full spread. Only the inner tips rise as the cards bend.
        float stackHeight = GdxShuffleGeometry.stackHeight(aspect);
        for (int i = 0; i < 12; i++) {
            int level = i / 4;
            float reach = level < 2 ? GdxShuffleGeometry.MAX_HALF_SPAN : .5f;
            float elevation = level == 0 ? 0f : level == 1 ? stackHeight : 1f;
            point.set((i & 1) == 0 ? -reach : reach, elevation,
                    ((i & 2) == 0 ? -.5f : .5f) * aspect).prj(model);
            corners[2*i] = (1f + point.x) * width * .5f;
            corners[2*i+1] = (1f + point.y) * height * .5f;
        }
        outline = hull.computePolygon(corners, false);
        baseX = Float.NaN;
    }

    Vector2 place(float x, float y, float rx, float ry, Rectangle left, Rectangle right) {
        if (baseX == x && baseY == y && radiusX == rx && radiusY == ry
                && leftBank.equals(left) && rightBank.equals(right)) return result;
        baseX = x; baseY = y; radiusX = rx; radiusY = ry;
        leftBank.set(left); rightBank.set(right);
        bestDistance = Float.POSITIVE_INFINITY;
        considerRow(y);
        if (bestDistance > 0f) {
            // Search across the open felt, keeping the near edge clear of the player's HUD.
            for (int step = 1; step <= 16; step++) {
                float offset = step * ry * .25f;
                considerRow(y + offset);
                if (step <= 5) considerRow(y - offset);
            }
            if (right.width > 0f) considerRow(right.y + right.height + ry);
            if (left.width > 0f) considerRow(left.y + left.height + ry);
        }
        return result;
    }

    private void considerRow(float y) {
        float dy = y - baseY;
        if (dy * dy >= bestDistance) return;
        // Intersect the convex silhouette with the full vertical extent of the marker.
        lowX = Float.POSITIVE_INFINITY;
        highX = Float.NEGATIVE_INFINITY;
        for (int i = 2; i < outline.size; i += 2) {
            float x0 = outline.items[i-2], y0 = outline.items[i-1];
            float x1 = outline.items[i], y1 = outline.items[i+1];
            if (Math.max(y0, y1) < y - radiusY || Math.min(y0, y1) > y + radiusY) continue;
            if (y0 != y1) {
                float a = MathUtils.clamp((y - radiusY - y0) / (y1 - y0), 0f, 1f);
                float b = MathUtils.clamp((y + radiusY - y0) / (y1 - y0), 0f, 1f);
                float span = x1 - x0;
                x1 = x0 + b * span;
                x0 += a * span;
            }
            lowX = Math.min(lowX, Math.min(x0, x1) - radiusX);
            highX = Math.max(highX, Math.max(x0, x1) + radiusX);
        }
        // On this row the nearest legal x is the original x or an obstacle boundary.
        consider(baseX, y);
        if (lowX <= highX) { consider(lowX, y); consider(highX, y); }
        if (leftBank.width > 0f) {
            consider(leftBank.x - radiusX, y);
            consider(leftBank.x + leftBank.width + radiusX, y);
        }
        if (rightBank.width > 0f) {
            consider(rightBank.x - radiusX, y);
            consider(rightBank.x + rightBank.width + radiusX, y);
        }
    }

    private void consider(float x, float y) {
        if ((x > lowX && x < highX) || blocks(leftBank, x, y) || blocks(rightBank, x, y)) return;
        float distance = Vector2.dst2(x, y, baseX, baseY);
        if (distance < bestDistance) {
            bestDistance = distance;
            result.set(x, y);
        }
    }

    private boolean blocks(Rectangle bank, float x, float y) {
        return bank.width > 0f && x > bank.x - radiusX && x < bank.x + bank.width + radiusX
                && y > bank.y - radiusY && y < bank.y + bank.height + radiusY;
    }
}
