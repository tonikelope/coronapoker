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
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
import java.nio.IntBuffer;
import org.lwjgl.opengl.GL11C;

/** Batches the original shadow fans with a shared vertex at each triangle edge. */
final class GdxFlyingChipShadows implements Disposable {
    private static final int SEGMENTS = 24;
    private static final int VERTICES = SEGMENTS + 2;
    private static final int INDICES = SEGMENTS * 3;
    private static final int STRIDE = 6;
    private static final float HEIGHT = .02f;
    private static final int TILE_COLUMNS = 8;
    private static final int TILE_ROWS = 4;
    private static final int MIN_TILED_SHADOWS = 128;
    private static final float MIN_TILED_COVERAGE = 4f;
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
    private final FloatArray circles = new FloatArray();
    private final IntBuffer viewport = BufferUtils.newIntBuffer(4);
    private final IntBuffer scissor = BufferUtils.newIntBuffer(4);
    private final ShortArray tileIndices = new ShortArray();
    private final int[] tileStart = new int[TILE_COLUMNS * TILE_ROWS + 1];
    private final int[] tileX = new int[TILE_COLUMNS + 1];
    private final int[] tileY = new int[TILE_ROWS + 1];
    private final float[] bounds = new float[MAX_SHADOWS * 4];
    private Mesh tiledMesh;
    private short[] indexOrder;
    private Mesh mesh;
    private int capacity;

    void clear() {
        geometry.clear();
        circles.clear();
    }

    void add(float x, float z, float radius, float shade) {
        circles.add(x, z, radius);
        geometry.ensureCapacity(VERTICES * STRIDE);
        geometry.add(x, HEIGHT, z, .5f);
        geometry.add(.5f, shade);
        for (int i = 0; i <= SEGMENTS; i++) {
            float dx = RING_X[i], dz = RING_Z[i];
            geometry.add(x + radius * dx, HEIGHT, z + radius * dz, .5f + .5f * dx);
            geometry.add(.5f + .5f * dz, shade);
        }
    }

    void draw(ShaderProgram shader, Matrix4 view, int targetWidth, int targetHeight) {
        int count = geometry.size / (VERTICES * STRIDE);
        ensureMesh(Math.min(count, MAX_SHADOWS));
        if (count >= MIN_TILED_SHADOWS && count <= MAX_SHADOWS
                && drawTiled(shader, view, count, targetWidth, targetHeight)) return;
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

    /** Draws disjoint screen tiles, preserving the original depth and triangle order in each tile. */
    private boolean drawTiled(ShaderProgram shader, Matrix4 view, int count, int targetWidth, int targetHeight) {
        GL20 gl = Gdx.gl;
        boolean hadScissor = GL11C.glIsEnabled(GL20.GL_SCISSOR_TEST);
        gl.glGetIntegerv(GL20.GL_VIEWPORT, viewport);
        gl.glGetIntegerv(GL20.GL_SCISSOR_BOX, scissor);
        int x0 = 0, y0 = 0, x1 = targetWidth, y1 = targetHeight;
        if (hadScissor) {
            x0 = Math.max(0, scissor.get(0));
            y0 = Math.max(0, scissor.get(1));
            x1 = Math.min(x1, scissor.get(0) + scissor.get(2));
            y1 = Math.min(y1, scissor.get(1) + scissor.get(3));
        }
        if (x0 >= x1 || y0 >= y1) return true;
        float coveredArea = computeBounds(view.val, count, x0, y0, x1, y1);
        // Small or dispersed shadows draw cheaply in one pass. Tiling pays
        // for the index upload only when the projected bounds overlap heavily.
        if (coveredArea < MIN_TILED_COVERAGE * (x1 - x0) * (y1 - y0)) return false;
        tileEdges(tileX, x0, x1);
        tileEdges(tileY, y0, y1);
        tileIndices.clear();
        int tile = 0;
        for (int row = 0; row < TILE_ROWS; row++) {
            int bottom = tileY[row], top = tileY[row + 1];
            for (int col = 0; col < TILE_COLUMNS; col++) {
                int left = tileX[col], right = tileX[col + 1];
                tileStart[tile++] = tileIndices.size;
                for (int shadow = count - 1; shadow >= 0; shadow--) {
                    int at = shadow * 4;
                    if (bounds[at] > right || bounds[at + 2] < left
                            || bounds[at + 1] > top || bounds[at + 3] < bottom) continue;
                    tileIndices.addAll(indexOrder, (capacity - 1 - shadow) * INDICES, INDICES);
                }
            }
        }
        tileStart[tile] = tileIndices.size;
        if (tileIndices.isEmpty()) return true;
        if (tiledMesh == null || tiledMesh.getMaxVertices() < count * VERTICES
                || tiledMesh.getMaxIndices() < tileIndices.size) {
            if (tiledMesh != null) tiledMesh.dispose();
            tiledMesh = createMesh(false, capacity * VERTICES, MathUtils.nextPowerOfTwo(tileIndices.size));
        }
        tiledMesh.setVertices(geometry.items, 0, geometry.size);
        tiledMesh.setIndices(tileIndices.items, 0, tileIndices.size);
        gl.glDepthFunc(GL20.GL_LESS);
        gl.glEnable(GL20.GL_SCISSOR_TEST);
        tiledMesh.bind(shader);
        try {
            tile = 0;
            for (int row = 0; row < TILE_ROWS; row++) {
                int bottom = tileY[row], tileHeight = tileY[row + 1] - bottom;
                for (int col = 0; col < TILE_COLUMNS; col++, tile++) {
                    int first = tileStart[tile], indices = tileStart[tile + 1] - first;
                    if (indices == 0) continue;
                    int left = tileX[col];
                    gl.glScissor(left, bottom, tileX[col + 1] - left, tileHeight);
                    tiledMesh.render(shader, GL20.GL_TRIANGLES, first, indices, false);
                }
            }
        } finally {
            tiledMesh.unbind(shader);
            gl.glScissor(scissor.get(0), scissor.get(1), scissor.get(2), scissor.get(3));
            if (!hadScissor) gl.glDisable(GL20.GL_SCISSOR_TEST);
            gl.glDepthFunc(GL20.GL_LEQUAL);
        }
        return true;
    }

    private static void tileEdges(int[] edges, int start, int end) {
        for (int i = 0; i < edges.length; i++) edges[i] = start + (end - start) * i / (edges.length - 1);
    }

    private float computeBounds(float[] matrix, int count, int x0, int y0, int x1, int y1) {
        float scaleX = viewport.get(2) * .5f, scaleY = viewport.get(3) * .5f;
        float translateX = viewport.get(0) + scaleX, translateY = viewport.get(1) + scaleY;
        float y = HEIGHT;
        float coveredArea = 0f;
        for (int i = 0; i < count; i++) {
            float x = circles.items[3 * i], z = circles.items[3 * i + 1];
            float radius = circles.items[3 * i + 2];
            float minX = Float.POSITIVE_INFINITY, minY = minX, maxX = -minX, maxY = -minX;
            for (int corner = 0; corner < 4; corner++) {
                float cx = x + ((corner & 1) == 0 ? -radius : radius);
                float cz = z + ((corner & 2) == 0 ? -radius : radius);
                float w = matrix[Matrix4.M30] * cx + matrix[Matrix4.M31] * y
                        + matrix[Matrix4.M32] * cz + matrix[Matrix4.M33];
                if (!(w > 1f)) {
                    minX = minY = Float.NEGATIVE_INFINITY;
                    maxX = maxY = Float.POSITIVE_INFINITY;
                    break;
                }
                float pixelX = (matrix[Matrix4.M00] * cx + matrix[Matrix4.M01] * y
                        + matrix[Matrix4.M02] * cz + matrix[Matrix4.M03]) / w * scaleX + translateX;
                float pixelY = (matrix[Matrix4.M10] * cx + matrix[Matrix4.M11] * y
                        + matrix[Matrix4.M12] * cz + matrix[Matrix4.M13]) / w * scaleY + translateY;
                minX = Math.min(minX, pixelX);
                maxX = Math.max(maxX, pixelX);
                minY = Math.min(minY, pixelY);
                maxY = Math.max(maxY, pixelY);
            }
            coveredArea += Math.max(0f, Math.min(maxX, x1) - Math.max(minX, x0))
                    * Math.max(0f, Math.min(maxY, y1) - Math.max(minY, y0));
            int at = i * 4;
            // Padding absorbs raster and floating-point edge rounding in physical pixels.
            bounds[at] = minX - 4f;
            bounds[at + 1] = minY - 4f;
            bounds[at + 2] = maxX + 4f;
            bounds[at + 3] = maxY + 4f;
        }
        return coveredArea;
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
        indexOrder = indices;
        mesh = createMesh(true, capacity * VERTICES, indices.length);
        mesh.setIndices(indices);
    }

    private static Mesh createMesh(boolean staticIndices, int vertices, int indices) {
        return new Mesh(false, staticIndices, vertices, indices, new VertexAttributes(
                new VertexAttribute(Usage.Position, 3, "a_position"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                new VertexAttribute(Usage.Generic, 1, "a_light")));
    }

    @Override public void dispose() {
        if (mesh != null) mesh.dispose();
        if (tiledMesh != null) tiledMesh.dispose();
    }
}
