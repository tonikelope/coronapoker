/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;

/** Batches the original shadow fans with a shared vertex at each triangle edge. */
final class GdxFlyingChipShadows implements Disposable {
    private static final int SEGMENTS = 24;
    private static final int VERTICES = SEGMENTS + 2;
    private static final int INDICES = SEGMENTS * 3;
    private static final int STRIDE = 6;
    private static final int MAX_SHADOWS = (1 << 16) / VERTICES;
    private static final float[] RING_X = new float[SEGMENTS + 1];
    private static final float[] RING_Z = new float[SEGMENTS + 1];

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            float angle = MathUtils.PI2 * i / SEGMENTS;
            RING_X[i] = MathUtils.cos(angle);
            RING_Z[i] = MathUtils.sin(angle);
        }
    }

    private final FloatArray geometry = new FloatArray();
    private Mesh mesh;
    private int capacity;

    void clear() {
        geometry.clear();
    }

    void add(float x, float z, float radius, float shade) {
        geometry.ensureCapacity(VERTICES * STRIDE);
        geometry.add(x, .02f, z, .5f);
        geometry.add(.5f, shade);
        for (int i = 0; i <= SEGMENTS; i++) {
            float dx = RING_X[i], dz = RING_Z[i];
            geometry.add(x + radius * dx, .02f, z + radius * dz, .5f + .5f * dx);
            geometry.add(.5f + .5f * dz, shade);
        }
    }

    void draw(ShaderProgram shader) {
        int count = geometry.size / (VERTICES * STRIDE);
        ensureMesh(Math.min(count, MAX_SHADOWS));
        // A shadow replaces the previous colour at the same depth. Visiting
        // those triangles backwards gives the original last contributor
        // ownership of the tie, allowing the depth test to skip covered work.
        Gdx.gl.glDepthFunc(GL20.GL_LESS);
        try {
            for (int end = count; end > 0; ) {
                int batch = Math.min(capacity, end);
                int first = end - batch;
                mesh.setVertices(geometry.items, first * VERTICES * STRIDE, batch * VERTICES * STRIDE);
                mesh.render(shader, GL20.GL_TRIANGLES, (capacity - batch) * INDICES, batch * INDICES);
                end = first;
            }
        } finally {
            Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        }
    }

    private void ensureMesh(int count) {
        if (count <= capacity) return;
        if (mesh != null) mesh.dispose();
        capacity = Math.min(MAX_SHADOWS, Math.max(24, MathUtils.nextPowerOfTwo(count)));
        short[] indices = new short[capacity * INDICES];
        for (int shadow = 0; shadow < capacity; shadow++) {
            int center = shadow * VERTICES;
            for (int edge = 0; edge < SEGMENTS; edge++) {
                int index = ((capacity - 1 - shadow) * SEGMENTS + SEGMENTS - 1 - edge) * 3;
                indices[index] = (short) center;
                indices[index + 1] = (short) (center + edge + 1);
                indices[index + 2] = (short) (center + edge + 2);
            }
        }
        mesh = new Mesh(false, true, capacity * VERTICES, indices.length, new VertexAttributes(
                new VertexAttribute(Usage.Position, 3, "a_position"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                new VertexAttribute(Usage.Generic, 1, "a_light")));
        mesh.setIndices(indices);
    }

    @Override public void dispose() {
        if (mesh != null) mesh.dispose();
    }
}
