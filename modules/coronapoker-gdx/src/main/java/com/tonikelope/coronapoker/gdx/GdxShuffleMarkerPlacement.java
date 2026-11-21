/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.ConvexHull;
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
    private final float[] edgePadding = new float[12];
    private float minY, maxY;
    private boolean reserveShuffle;
    private final Rectangle leftBank = new Rectangle(), rightBank = new Rectangle();
    private final Rectangle peekButton = new Rectangle();
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
        minY = maxY = outline.items[1];
        for (int i = 3; i < outline.size; i += 2) {
            minY = Math.min(minY, outline.items[i]);
            maxY = Math.max(maxY, outline.items[i]);
        }
        baseX = Float.NaN;
    }

    Vector2 place(float x, float y, float rx, float ry, Rectangle left, Rectangle right, Rectangle peek,
            boolean avoidShuffle) {
        if (baseX == x && baseY == y && radiusX == rx && radiusY == ry
                && reserveShuffle == avoidShuffle && leftBank.equals(left) && rightBank.equals(right)
                && peekButton.equals(peek)) return result;
        if (avoidShuffle && (!reserveShuffle || Float.isNaN(baseX) || radiusX != rx || radiusY != ry)) {
            // Offset each edge by the support radius of the projected disc along its normal.
            for (int i = 2; i < outline.size; i += 2) {
                float nx = outline.items[i+1] - outline.items[i-1];
                float ny = outline.items[i-2] - outline.items[i];
                edgePadding[i/2-1] = (float) Math.hypot(rx * nx, ry * ny);
            }
        }
        reserveShuffle = avoidShuffle;
        baseX = x; baseY = y; radiusX = rx; radiusY = ry;
        leftBank.set(left); rightBank.set(right); peekButton.set(peek);
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
            if (peek.width > 0f) considerRow(peek.y + peek.height + ry);
        }
        return result;
    }

    private void considerRow(float y) {
        float dy = y - baseY;
        if (dy * dy >= bestDistance) return;
        lowX = Float.POSITIVE_INFINITY;
        highX = Float.NEGATIVE_INFINITY;
        if (reserveShuffle && y > minY - radiusY && y < maxY + radiusY) {
            lowX = Float.NEGATIVE_INFINITY;
            highX = Float.POSITIVE_INFINITY;
            // The hull is counterclockwise. Intersect its outward-offset half-planes with this row.
            for (int i = 2; i < outline.size; i += 2) {
                float x0 = outline.items[i-2], y0 = outline.items[i-1];
                float dx = outline.items[i] - x0, dyEdge = outline.items[i+1] - y0;
                if (dyEdge == 0f) continue;
                float limit = x0 + (edgePadding[i/2-1] + dx * (y - y0)) / dyEdge;
                if (dyEdge < 0f) lowX = Math.max(lowX, limit);
                else highX = Math.min(highX, limit);
            }
        }
        // On this row the nearest legal x is the original x or an obstacle boundary.
        consider(baseX, y);
        if (lowX <= highX) { consider(lowX, y); consider(highX, y); }
        considerSides(leftBank, y);
        considerSides(rightBank, y);
        considerSides(peekButton, y);
    }

    private void considerSides(Rectangle obstacle, float y) {
        if (obstacle.width <= 0f) return;
        consider(obstacle.x - radiusX, y);
        consider(obstacle.x + obstacle.width + radiusX, y);
    }

    private void consider(float x, float y) {
        if ((x > lowX && x < highX) || blocks(leftBank, x, y)
                || blocks(rightBank, x, y) || blocks(peekButton, x, y)) return;
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
