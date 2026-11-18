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
    private static final float[] TAP_X = new float[SHADOW_TAPS];
    private static final float[] TAP_Y = new float[SHADOW_TAPS];
    static {
        for (int tap = 1; tap < SHADOW_TAPS; tap++) {
            float angle = (tap - 1) * MathUtils.PI2 / (SHADOW_TAPS - 1);
            TAP_X[tap] = MathUtils.cos(angle);
            TAP_Y[tap] = MathUtils.sin(angle);
        }
    }
    private static final int LAYER_FLOATS = COLUMNS * ROWS * QUAD_FLOATS;
    private final GdxPlasticCard.CommunityTurn sheet = new GdxPlasticCard.CommunityTurn();
    private final Vector3 point = new Vector3();
    private final Vector3 across = new Vector3();
    private final Vector3 along = new Vector3();
    private final Vector3 normal = new Vector3();
    private final Color color = new Color();
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
        shadowCount = progress > 0f && progress < 1f ? LAYER_FLOATS : 0;
        for (int row = 0; row <= ROWS; row++) {
            for (int column = 0; column <= COLUMNS; column++) {
                int index = row * STRIDE + column;
                float u = coordinate(column, COLUMNS), v = coordinate(row, ROWS);
                sheet.sample(point, u, v);
                if (shadowCount > 0) {
                    shadowVertex(column, row, x, y, scale, cos, sin, width, shadowWeight,
                            (u - .5f) / shaderCanvasScale + .5f, (.5f - v) / shaderCanvasScale + .5f);
                }
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
        backCount = faceCount = 0;
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
            }
        }
    }

    void drawShadow(SpriteBatch batch, Texture back) {
        if (shadowCount == 0) return;
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

    private void shadowVertex(int column, int row,
            float x, float y, float scale, float cos, float sin, float width,
            float weight, float u, float v) {
        float elevation = point.y;
        float px = point.x + elevation * .23f;
        float py = point.z - elevation * .18f - width * .012f;
        float softness = width * .008f + Math.max(0f, elevation) * .045f;
        float alpha = weight * (.17f - .07f * MathUtils.clamp(elevation / width, 0f, 1f));
        // Diffuse light softens the shadow as the card leaves the felt. Each
        // tap follows the same material, so the penumbra stays at the contact.
        float centerColor = color.set(0f, 0f, 0f, alpha * .2f).toFloatBits();
        float outerColor = color.set(0f, 0f, 0f, alpha * .1f).toFloatBits();
        int quad = (row * COLUMNS + column) * QUAD_FLOATS;
        for (int tap = 0; tap < SHADOW_TAPS; tap++, quad += LAYER_FLOATS) {
            float sx = px + softness * TAP_X[tap], sy = py + softness * TAP_Y[tap];
            float wx = x + scale * (sx * cos - sy * sin);
            float wy = y + scale * (sx * sin + sy * cos);
            float packed = tap == 0 ? centerColor : outerColor;
            // SpriteBatch consumes independent quads. Fill each adjoining corner
            // from the single projection of this physical point.
            if (row < ROWS && column < COLUMNS) putShadow(quad, wx, wy, packed, u, v);
            if (row > 0 && column < COLUMNS) putShadow(quad - COLUMNS * QUAD_FLOATS + 5, wx, wy, packed, u, v);
            if (row > 0 && column > 0) putShadow(quad - STRIDE * QUAD_FLOATS + 10, wx, wy, packed, u, v);
            if (row < ROWS && column > 0) putShadow(quad - QUAD_FLOATS + 15, wx, wy, packed, u, v);
        }
    }

    private void putShadow(int offset, float x, float y, float packedColor, float u, float v) {
        shadowVertices[offset] = x;
        shadowVertices[offset + 1] = y;
        shadowVertices[offset + 2] = packedColor;
        shadowVertices[offset + 3] = u;
        shadowVertices[offset + 4] = v;
    }

    private static float coordinate(int index, int cells) {
        return -GUARD + (1f + 2f * GUARD) * index / cells;
    }
}
