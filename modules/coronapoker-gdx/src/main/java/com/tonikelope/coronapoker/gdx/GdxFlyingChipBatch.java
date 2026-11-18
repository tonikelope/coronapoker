/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import java.util.Arrays;

/** Indexed copies of the same solid, posed in batches on the GL2 vertex shader. */
final class GdxFlyingChipBatch implements Disposable {
    // Three vec4s per chip plus the projection fit the GL2 minimum vertex uniform budget.
    private static final int BATCH = 24;
    private static final String VERTEX = "#define CHIPS " + BATCH + "\n" + """
            attribute vec3 a_position;
            attribute vec2 a_texCoord0;
            attribute float a_slot;
            attribute vec3 a_normal;
            uniform mat4 u_projection;
            uniform vec4 u_centers[CHIPS];
            uniform vec4 u_rotations[CHIPS];
            uniform vec4 u_surfaces[CHIPS];
            varying vec2 v_uv;
            varying float v_light;
            varying vec3 v_normal;
            varying vec3 v_position;
            vec3 rotateChip(vec4 q, vec3 v) {
                return v + 2.0 * cross(q.xyz, cross(q.xyz, v) + q.w * v);
            }
            void main() {
                int i = int(a_slot);
                vec4 center = u_centers[i];
                vec4 q = u_rotations[i];
                vec4 face = u_surfaces[i];
                vec3 local = a_position * center.w;
                local.y = sign(a_position.y) * (face.z - (abs(a_position.y) - 1.0) * face.w);
                v_position = center.xyz + rotateChip(q, local);
                v_normal = rotateChip(q, a_normal);
                v_uv = vec2(0.5) + mat2(face.x, face.y, -face.y, face.x) * (a_texCoord0 - vec2(0.5));
                v_light = 1.0;
                gl_Position = u_projection * vec4(v_position, 1.0);
            }
            """;

    private static final class Instances {
        final FloatArray centers = new FloatArray(), rotations = new FloatArray(), surfaces = new FloatArray();
    }

    private final Instances[] colors = {new Instances(), new Instances(), new Instances(), new Instances()};
    private Mesh mesh;
    private ShaderProgram shader;
    private int indicesPerChip, count;

    void clear() {
        count = 0;
        for (Instances instances : colors) {
            instances.centers.clear();
            instances.rotations.clear();
            instances.surfaces.clear();
        }
    }

    boolean isEmpty() { return count == 0; }

    void add(int color, float x, float y, float z, float diameter, float thickness, Quaternion q, float facing) {
        Instances instances = colors[color];
        instances.centers.add(x, y, z, diameter);
        instances.rotations.add(q.x, q.y, q.z, q.w);
        instances.surfaces.add(MathUtils.cos(facing), MathUtils.sin(facing),
                thickness * .5f, Math.min(thickness * .2f, diameter * .015f));
        count++;
    }

    void draw(Matrix4 projection, Vector3 eye, Texture[] faces, float opacity) {
        shader.bind();
        shader.setUniformMatrix("u_projection", projection);
        shader.setUniformf("u_eye", eye);
        shader.setUniformf("u_opacity", opacity);
        shader.setUniformi("u_texture", 0);
        shader.setUniformi("u_material", 8);
        mesh.bind(shader);
        try {
            for (int color = 0; color < colors.length; color++) {
                Instances instances = colors[color];
                if (instances.centers.isEmpty()) continue;
                faces[color].bind(0);
                for (int offset = 0; offset < instances.centers.size; offset += BATCH * 4) {
                    int floats = Math.min(BATCH * 4, instances.centers.size - offset);
                    shader.setUniform4fv("u_centers[0]", instances.centers.items, offset, floats);
                    shader.setUniform4fv("u_rotations[0]", instances.rotations.items, offset, floats);
                    shader.setUniform4fv("u_surfaces[0]", instances.surfaces.items, offset, floats);
                    mesh.render(shader, GL20.GL_TRIANGLES, 0, floats / 4 * indicesPerChip, false);
                }
            }
        } finally {
            mesh.unbind(shader);
        }
    }

    void prepare(String fragment) {
        shader = new ShaderProgram(VERTEX, fragment);
        if (!shader.isCompiled()) throw new IllegalStateException(shader.getLog());
        FloatArray template = GdxPerspectiveTable.flyingChipTemplate();
        indicesPerChip = template.size / 9;
        FloatArray unique = new FloatArray();
        short[] indices = new short[indicesPerChip * BATCH];
        for (int i = 0; i < template.size; i += 9) {
            int at = 0;
            while (at < unique.size && !Arrays.equals(unique.items, at, at + 9, template.items, i, i + 9)) at += 9;
            if (at == unique.size) unique.addAll(template.items, i, 9);
            indices[i / 9] = (short) (at / 9);
        }
        int verticesPerChip = unique.size / 9;
        float[] vertices = new float[unique.size * BATCH];
        for (int chip = 0; chip < BATCH; chip++) {
            int offset = chip * unique.size;
            System.arraycopy(unique.items, 0, vertices, offset, unique.size);
            for (int i = 5; i < unique.size; i += 9) vertices[offset + i] = chip;
            for (int i = 0; i < indicesPerChip; i++) {
                indices[chip * indicesPerChip + i] = (short) (chip * verticesPerChip + indices[i]);
            }
        }
        mesh = new Mesh(Mesh.VertexDataType.VertexBufferObject, true, BATCH * verticesPerChip, indices.length,
                new VertexAttribute(Usage.Position, 3, "a_position"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                new VertexAttribute(Usage.Generic, 1, "a_slot"),
                new VertexAttribute(Usage.Normal, 3, "a_normal"));
        mesh.setVertices(vertices);
        mesh.setIndices(indices);
    }

    @Override public void dispose() {
        if (mesh != null) mesh.dispose();
        if (shader != null) shader.dispose();
    }
}
