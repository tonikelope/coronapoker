/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.FloatArray;

/** Indexed versions of the original 48-sided table-chip surfaces. */
final class GdxStandingChipMesh {
    private static final int SEGMENTS = 48;
    private static final int RING = SEGMENTS + 1;
    private static final int SHADOW_VERTICES = 1 + RING;
    private static final int CHIP_VERTICES = 1 + RING * 7;
    private static final int CHIP_INDICES = SEGMENTS * 21;
    private static final int SHADOW_INDICES = SEGMENTS * 3;
    static final int MAX_INDEXED_CHIPS = ((1 << 16) - SHADOW_VERTICES) / CHIP_VERTICES;
    private static final float[] ANGLES = new float[RING];

    static {
        for (int i = 0; i < RING; i++) ANGLES[i] = i * MathUtils.PI2 / SEGMENTS;
    }

    private GdxStandingChipMesh() { }

    static void append(FloatArray into, float x, float z, float diameter, float bottom,
            float thickness, float rotation, boolean shadow) {
        float radius = diameter * .5f;
        float bevel = Math.min(thickness * .20f, diameter * .015f);
        float base = bottom + .04f;
        if (shadow) {
            float extent = radius * 1.08f, contact = bottom + .02f;
            GdxPerspectiveTable.potVertex(into, x, z, extent, contact, 0f, 0f, rotation, true, 1f, -1f);
            ring(into, x, z, extent, contact, 1f, rotation, true, 1f, -1f);
        }
        GdxPerspectiveTable.potVertex(into, x, z, radius, base + thickness, 0f, 0f, rotation, true, 1f, 1f);
        ring(into, x, z, radius, base + thickness, .985f, rotation, true, 1f, 1f);
        ring(into, x, z, radius, base, .985f, rotation, false, -.6f, 1f);
        ring(into, x, z, radius, base + bevel, 1f, rotation, false, -.6f, 1f);
        ring(into, x, z, radius, base + bevel, 1f, rotation, false, 0f, 1f);
        ring(into, x, z, radius, base + (thickness - bevel), 1f, rotation, false, 0f, 1f);
        ring(into, x, z, radius, base + (thickness - bevel), 1f, rotation, false, .6f, 1f);
        ring(into, x, z, radius, base + thickness, .985f, rotation, false, .6f, 1f);
    }

    private static void ring(FloatArray into, float x, float z, float radius, float y,
            float radial, float rotation, boolean top, float normalY, float visibility) {
        for (float angle : ANGLES) {
            GdxPerspectiveTable.potVertex(into, x, z, radius, y, radial, angle, rotation, top, normalY, visibility);
        }
    }

    static int firstIndex(int level) {
        return level == 0 ? 0 : level * CHIP_INDICES + SHADOW_INDICES;
    }

    static Mesh upload(Mesh mesh, FloatArray geometry, int chips) {
        if (mesh == null || mesh.getMaxIndices() < firstIndex(chips)) {
            if (mesh != null) mesh.dispose();
            int capacity = Math.min(MAX_INDEXED_CHIPS, MathUtils.nextPowerOfTwo(chips));
            mesh = new Mesh(false, true, capacity * CHIP_VERTICES + SHADOW_VERTICES,
                    firstIndex(capacity), new VertexAttributes(new VertexAttribute(Usage.Position, 3, "a_position"),
                            new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                            new VertexAttribute(Usage.Generic, 1, "a_light"),
                            new VertexAttribute(Usage.Normal, 3, "a_normal")));
            mesh.setIndices(indices(capacity));
        }
        mesh.setVertices(geometry.items, 0, geometry.size);
        return mesh;
    }

    private static short[] indices(int chips) {
        short[] result = new short[firstIndex(chips)];
        int at = 0;
        for (int chip = 0; chip < chips; chip++) {
            int center = SHADOW_VERTICES + chip * CHIP_VERTICES;
            for (int edge = 0; edge < SEGMENTS; edge++) {
                if (chip == 0) at = triangle(result, at, 0, edge + 1, edge + 2);
                at = triangle(result, at, center, center + edge + 1, center + edge + 2);
                for (int band = 0; band < 3; band++) {
                    int low = center + 1 + (band * 2 + 1) * RING + edge;
                    int high = low + RING;
                    at = triangle(result, at, low, high, high + 1);
                    at = triangle(result, at, low, high + 1, low + 1);
                }
            }
        }
        return result;
    }

    private static int triangle(short[] into, int offset, int a, int b, int c) {
        into[offset] = (short) a;
        into[offset + 1] = (short) b;
        into[offset + 2] = (short) c;
        return offset + 3;
    }
}
