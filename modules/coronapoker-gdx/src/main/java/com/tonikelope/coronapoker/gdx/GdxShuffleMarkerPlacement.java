/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.ConvexHull;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;
import java.util.Arrays;

/** Centers the local position marker in the lane between the hand and the right chip bank. */
final class GdxShuffleMarkerPlacement {
    private final ConvexHull hull = new ConvexHull();
    private final float[] corners = new float[24];
    private final Vector3 point = new Vector3();
    private FloatArray outline;
    private final float[] edgePadding = new float[12];
    private float minY, maxY;
    private boolean reserveShuffle;
    private final Rectangle leftBank = new Rectangle(), rightBank = new Rectangle();
    private final Rectangle pocket = new Rectangle(), peekButton = new Rectangle();
    private Rectangle[] rightRows = new Rectangle[0];
    private final Vector2 result = new Vector2();
    private float baseX = Float.NaN, baseY, radiusX, radiusY;
    private float lowX, highX, bestDistance, markerScale;
    private static final float LANE_GAP = 5f;

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

    Vector2 place(float x, float y, float rx, float ry, Rectangle left, Rectangle right,
            Rectangle[] rows, Rectangle cards, Rectangle peek, boolean avoidShuffle) {
        if (baseX == x && baseY == y && radiusX == rx && radiusY == ry
                && reserveShuffle == avoidShuffle && leftBank.equals(left) && rightBank.equals(right)
                && Arrays.equals(rightRows, rows) && pocket.equals(cards) && peekButton.equals(peek)) return result;
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
        leftBank.set(left); rightBank.set(right); pocket.set(cards); peekButton.set(peek);
        if (rightRows.length != rows.length) {
            rightRows = new Rectangle[rows.length];
            Arrays.setAll(rightRows, index -> new Rectangle());
        }
        for (int i = 0; i < rows.length; i++) rightRows[i].set(rows[i]);

        result.set(x, y);
        markerScale = 1f;
        bestDistance = Float.POSITIVE_INFINITY;
        if (pocket.width > 0f && rightBank.width > 0f) searchRows(true);
        if (!Float.isFinite(bestDistance)) searchRows(false);
        return result;
    }

    float scale() {
        return markerScale;
    }

    private void searchRows(boolean centered) {
        considerRow(baseY, centered);
        // Stay beside the hand where possible, with room above it when the lane is narrow.
        for (int step = 1; step <= 16 && bestDistance > 0f; step++) {
            float offset = step * radiusY * .25f;
            considerRow(baseY + offset, centered);
            if (step <= 5) considerRow(baseY - offset, centered);
        }
        if (rightBank.width > 0f) considerRow(rightBank.y + rightBank.height + radiusY + LANE_GAP, centered);
        if (leftBank.width > 0f) considerRow(leftBank.y + leftBank.height + radiusY, centered);
        if (peekButton.width > 0f) considerRow(peekButton.y + peekButton.height + radiusY, centered);
        if (pocket.width > 0f) considerRow(pocket.y + pocket.height + radiusY + LANE_GAP, centered);
    }

    private void considerRow(float y, boolean centered) {
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
        if (centered) {
            if (!crosses(pocket, y, radiusY + LANE_GAP)) return;
            float innerEdge = Float.POSITIVE_INFINITY;
            for (Rectangle row : rightRows) {
                if (crosses(row, y, radiusY + LANE_GAP)) innerEdge = Math.min(innerEdge, row.x);
            }
            if (!Float.isFinite(innerEdge)) innerEdge = rightBank.x;
            float cardEdge = pocket.x + pocket.width;
            float scale = Math.min(1f, (innerEdge - cardEdge - 2f * LANE_GAP) / (2f * radiusX));
            if (scale >= .65f) consider((cardEdge + innerEdge) * .5f, y, scale);
            return;
        }
        // On this row the nearest legal x is the original x or an obstacle boundary.
        consider(baseX, y);
        if (lowX <= highX) { consider(lowX, y); consider(highX, y); }
        considerSides(leftBank, y, 0f);
        for (Rectangle row : rightRows) considerSides(row, y, LANE_GAP);
        if (rightRows.length == 0) considerSides(rightBank, y, 0f);
        considerSides(peekButton, y, 0f);
        if (pocket.width > 0f) consider(pocket.x + pocket.width + radiusX + LANE_GAP, y);
    }

    private void considerSides(Rectangle obstacle, float y, float gap) {
        if (obstacle.width <= 0f) return;
        consider(obstacle.x - radiusX - gap, y);
        consider(obstacle.x + obstacle.width + radiusX + gap, y);
    }

    private void consider(float x, float y) {
        consider(x, y, 1f);
    }

    private void consider(float x, float y, float scale) {
        float rx = radiusX * scale, ry = radiusY * scale;
        if ((x > lowX && x < highX) || blocks(leftBank, x, y, rx, ry) || blocks(peekButton, x, y, rx, ry)
                || (crosses(pocket, y, ry + LANE_GAP) && x + .001f < pocket.x + pocket.width + rx + LANE_GAP)) return;
        for (Rectangle row : rightRows) {
            if (blocks(row, x, y, rx + LANE_GAP, ry + LANE_GAP)) return;
        }
        if (rightRows.length == 0 && blocks(rightBank, x, y, rx, ry)) return;
        float distance = Vector2.dst2(x, y, baseX, baseY);
        if (distance < bestDistance) {
            bestDistance = distance;
            result.set(x, y);
            markerScale = scale;
        }
    }

    private static boolean blocks(Rectangle obstacle, float x, float y, float rx, float ry) {
        return crosses(obstacle, y, ry)
                && x > obstacle.x - rx + .001f && x < obstacle.x + obstacle.width + rx - .001f;
    }

    private static boolean crosses(Rectangle obstacle, float y, float ry) {
        return obstacle.width > 0f && y > obstacle.y - ry && y < obstacle.y + obstacle.height + ry;
    }
}
