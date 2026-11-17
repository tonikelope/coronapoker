/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/** Projects a gently flexing plastic sheet into the overhead 2D board. */
final class GdxCommunityTurn2D {
    private static final int COLUMNS = 80;
    private static final int ROWS = 8;
    private static final int STRIDE = COLUMNS + 1;
    private static final int QUAD_FLOATS = 20;
    private static final float GUARD = CoronaPokerGdxTable.cardEdgeGuardUv();
    private static final int SHADOW_TAPS = 9;
    private static final int LAYER_FLOATS = COLUMNS * ROWS * QUAD_FLOATS;
    private final GdxPlasticCard.CommunityTurn sheet = new GdxPlasticCard.CommunityTurn();
    private final Vector3 point = new Vector3();
    private final Vector3 across = new Vector3();
    private final Vector3 along = new Vector3();
    private final Vector3 normal = new Vector3();
    private final Color color = new Color();
    private final float[] physical = new float[STRIDE * (ROWS + 1) * 3];
    private final float[] projected = new float[STRIDE * (ROWS + 1) * 2];
    private final float[] colors = new float[STRIDE * (ROWS + 1)];
    private final float[] backVertices = new float[COLUMNS * ROWS * QUAD_FLOATS];
    private final float[] faceVertices = new float[COLUMNS * ROWS * QUAD_FLOATS];
    private final float[] shadowVertices = new float[LAYER_FLOATS * SHADOW_TAPS];
    private int backCount, faceCount, shadowCount;

    /** No rotation of a flat sprite: each vertex follows the actual sheet above the felt. */
    void prepare(float x, float y, float width, float height, float rotation,
            float scale, float progress, int direction, GdxPlasticCard.Motion motion,
            float shaderCanvasScale) {
        sheet.set(width, height, progress, direction, motion);
        float cos = MathUtils.cosDeg(rotation), sin = MathUtils.sinDeg(rotation);
        float cameraHeight = width * 4f;
        float shadowWeight = MathUtils.sin(MathUtils.PI * MathUtils.clamp(progress, 0f, 1f));
        for (int row = 0; row <= ROWS; row++) {
            for (int column = 0; column <= COLUMNS; column++) {
                int index = row * STRIDE + column;
                float u = coordinate(column, COLUMNS), v = coordinate(row, ROWS);
                sheet.sample(point, u, v);
                physical[index * 3] = point.x;
                physical[index * 3 + 1] = point.y;
                physical[index * 3 + 2] = point.z;
                // A high, nearly overhead viewpoint keeps the flat board's footprint
                // exact, while elevation gives the lifted side a little parallax.
                float perspective = cameraHeight / (cameraHeight - point.y);
                float px = point.x * perspective;
                float py = point.z * perspective + point.y * .10f;
                projected[index * 2] = x + scale * (px * cos - py * sin);
                projected[index * 2 + 1] = y + scale * (px * sin + py * cos);
                sheet.sample(across, u + .0005f, v);
                sheet.sample(along, u, v + .0005f);
                across.sub(point);
                along.sub(point);
                normal.set(along).crs(across).nor();
                float facing = normal.y * (cameraHeight - point.y)
                        - normal.x * point.x - normal.z * point.z;
                float side = facing >= 0f ? 1f : -1f;
                float light = MathUtils.clamp(.78f + .22f * Math.abs(normal.y)
                        + .085f * normal.x * side + .035f * normal.z * side, .64f, 1f);
                colors[index] = color.set(light, light, light, 1f).toFloatBits();
            }
        }
        backCount = faceCount = shadowCount = 0;
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int a = row * STRIDE + column, b = a + STRIDE;
                int c = b + 1, d = a + 1;
                // Winding, rather than a global turn angle, selects the visible
                // side. Both printed faces can be visible around the broad bend.
                boolean face = projectedArea(a, b, c, d) > 0f;
                float[] target = face ? faceVertices : backVertices;
                int count = face ? faceCount : backCount;
                count = vertex(target, count, a, column, row, face, shaderCanvasScale);
                count = vertex(target, count, b, column, row + 1, face, shaderCanvasScale);
                count = vertex(target, count, c, column + 1, row + 1, face, shaderCanvasScale);
                count = vertex(target, count, d, column + 1, row, face, shaderCanvasScale);
                if (face) faceCount = count;
                else backCount = count;
                shadowCount = shadowVertex(shadowCount, a, column, row, x, y, scale,
                        cos, sin, width, shadowWeight, shaderCanvasScale);
                shadowCount = shadowVertex(shadowCount, b, column, row + 1, x, y, scale,
                        cos, sin, width, shadowWeight, shaderCanvasScale);
                shadowCount = shadowVertex(shadowCount, c, column + 1, row + 1, x, y, scale,
                        cos, sin, width, shadowWeight, shaderCanvasScale);
                shadowCount = shadowVertex(shadowCount, d, column + 1, row, x, y, scale,
                        cos, sin, width, shadowWeight, shaderCanvasScale);
            }
        }
    }

    void drawShadow(SpriteBatch batch, Texture back) {
        for (int tap = 0; tap < SHADOW_TAPS; tap++) {
            batch.draw(back, shadowVertices, tap * LAYER_FLOATS, shadowCount);
        }
    }

    void drawBack(SpriteBatch batch, Texture back) {
        if (backCount > 0) batch.draw(back, backVertices, 0, backCount);
    }

    void drawFace(SpriteBatch batch, Texture back) {
        // The rounded-card shader binds the front artwork separately on unit 1.
        if (faceCount > 0) batch.draw(back, faceVertices, 0, faceCount);
    }

    private float projectedArea(int a, int b, int c, int d) {
        return cross(a, b, c) + cross(a, c, d);
    }

    private float cross(int a, int b, int c) {
        return (projected[b * 2] - projected[a * 2])
                * (projected[c * 2 + 1] - projected[a * 2 + 1])
                - (projected[b * 2 + 1] - projected[a * 2 + 1])
                * (projected[c * 2] - projected[a * 2]);
    }

    private int vertex(float[] target, int count, int index, int column, int row,
            boolean face, float canvasScale) {
        target[count++] = projected[index * 2];
        target[count++] = projected[index * 2 + 1];
        target[count++] = colors[index];
        float u = coordinate(column, COLUMNS);
        // Turning the material reverses its horizontal direction. Unmirror the
        // front printing so the final rank/suit agrees with the resting card.
        target[count++] = ((face ? 1f - u : u) - .5f) / canvasScale + .5f;
        target[count++] = (.5f - coordinate(row, ROWS)) / canvasScale + .5f;
        return count;
    }

    private int shadowVertex(int count, int index, int column, int row,
            float x, float y, float scale, float cos, float sin, float width,
            float weight, float canvasScale) {
        float elevation = physical[index * 3 + 1];
        float px = physical[index * 3] + elevation * .23f;
        float py = physical[index * 3 + 2] - elevation * .18f - width * .012f;
        float softness = width * .008f + Math.max(0f, elevation) * .045f;
        float alpha = weight * (.17f - .07f * MathUtils.clamp(elevation / width, 0f, 1f));
        // Diffuse light softens the shadow as the card leaves the felt. Each
        // tap follows the same material, so the penumbra stays at the contact.
        for (int tap = 0; tap < SHADOW_TAPS; tap++) {
            float angle = (tap - 1) * MathUtils.PI2 / (SHADOW_TAPS - 1);
            float dx = tap == 0 ? 0f : softness * MathUtils.cos(angle);
            float dy = tap == 0 ? 0f : softness * MathUtils.sin(angle);
            int offset = tap * LAYER_FLOATS + count;
            shadowVertices[offset++] = x + scale * ((px + dx) * cos - (py + dy) * sin);
            shadowVertices[offset++] = y + scale * ((px + dx) * sin + (py + dy) * cos);
            shadowVertices[offset++] = color.set(0f, 0f, 0f, alpha * (tap == 0 ? .2f : .1f))
                    .toFloatBits();
            shadowVertices[offset++] = (coordinate(column, COLUMNS) - .5f) / canvasScale + .5f;
            shadowVertices[offset] = (.5f - coordinate(row, ROWS)) / canvasScale + .5f;
        }
        return count + 5;
    }

    private static float coordinate(int index, int cells) {
        return -GUARD + (1f + 2f * GUARD) * index / cells;
    }
}
