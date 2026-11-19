/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/** Deforms the covered Rabbit card into a cylindrical bend with a free end. */
final class GdxRabbitPeel implements Disposable {

    private static final int STRIPS = 36;
    private static final float GUARD = CoronaPokerGdxTable.cardEdgeGuardUv();

    static final String VERTEX_SHADER = """
            attribute vec2 a_texCoord0;
            uniform mat4 u_projTrans;
            uniform vec2 u_center;
            uniform vec2 u_size;
            uniform float u_progress;
            uniform float u_overhead;
            varying vec2 v_texCoords;
            varying float v_bend;
            varying float v_light;
            varying float v_lift;

            void main() {
                float aspect = u_size.y / u_size.x;
                vec2 normal = normalize(vec2(1.0, 1.0 / aspect));
                vec2 point = a_texCoord0 * vec2(1.0, aspect);
                float distance = max(0.0, 0.99 * normal.x * u_progress - dot(point, normal));
                float radius = mix(0.075, 0.145, u_progress);
                float angle = min(distance / radius, 2.94);
                float freeLength = max(0.0, distance - angle * radius);
                float along = radius * sin(angle) + freeLength * cos(angle);
                float lift = radius * (1.0 - cos(angle)) + freeLength * sin(angle);
                point += normal * (distance - along);
                vec2 offset = (point - vec2(0.5, aspect * 0.5)) * vec2(1.0, -1.0);
                // The flat view has an overhead, nearly orthographic camera.
                offset = mix(offset, offset * (1.0 + 0.13 * lift)
                        + vec2(0.025, 0.10) * lift, u_overhead);
                gl_Position = u_projTrans * vec4(u_center + offset * u_size.x,
                        (1.0 - u_overhead) * lift * u_size.x, 1.0);
                v_texCoords = a_texCoord0;
                v_bend = sin(angle * 0.5);
                v_light = 0.72 + 0.22 * abs(cos(angle)) + 0.12 * sin(angle);
                v_lift = lift;
            }
            """;

    private final Mesh mesh;

    GdxRabbitPeel() {
        int stride = STRIPS + 1;
        float[] vertices = new float[stride * stride * 2];
        int coordinate = 0;
        for (int row = 0; row <= STRIPS; row++) {
            for (int column = 0; column <= STRIPS; column++) {
                vertices[coordinate++] = column * (1f + 2f * GUARD) / STRIPS - GUARD;
                vertices[coordinate++] = row * (1f + 2f * GUARD) / STRIPS - GUARD;
            }
        }
        short[] indices = new short[STRIPS * STRIPS * 6];
        int index = 0;
        // Paint from the anchored area toward the free corner. The lifted
        // surface stays above the attached part even in a depthless card FBO.
        for (int diagonal = 2 * (STRIPS - 1); diagonal >= 0; diagonal--) {
            for (int row = Math.max(0, diagonal - STRIPS + 1);
                    row <= Math.min(STRIPS - 1, diagonal); row++) {
                int column = diagonal - row;
                int topLeft = row * stride + column;
                indices[index++] = (short) topLeft;
                indices[index++] = (short) (topLeft + stride);
                indices[index++] = (short) (topLeft + stride + 1);
                indices[index++] = (short) topLeft;
                indices[index++] = (short) (topLeft + stride + 1);
                indices[index++] = (short) (topLeft + 1);
            }
        }
        mesh = new Mesh(true, stride * stride, indices.length,
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates,
                        2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"));
        mesh.setVertices(vertices);
        mesh.setIndices(indices);
    }

    void draw(ShaderProgram shader) {
        mesh.render(shader, GL20.GL_TRIANGLES);
    }

    @Override
    public void dispose() {
        mesh.dispose();
    }
}
