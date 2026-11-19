/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;

/** Two interlaced packets with the timing of the original Goliat loop. */
final class GdxRiffleShuffle implements Disposable {

    static final int CARDS_PER_PACKET = 26;
    static final float CYCLE_SECONDS = 1.72f;
    private static final int CARDS = CARDS_PER_PACKET * 2;
    private static final int STRIPS = 30;
    private static final int VERTICES_PER_STRIP = 6;
    private static final int VERTICES_PER_CARD = (STRIPS + 1) * VERTICES_PER_STRIP;
    private static final int FLOATS_PER_VERTEX = 7;

    private static final float WIDTH = .72f;
    private static final float CORNER = .042f;
    private static final float THICKNESS = .0045f;
    private static final float FIRST_RELEASE = .025f;
    private static final float RELEASE_SPACING = .0196f;
    private static final float FALL_SECONDS = .145f;
    private static final float TUCK_START = 1.44f;
    private static final float TUCK_END = 1.69f;

    private static final String VERTEX_SHADER = """
            attribute vec3 a_position;
            attribute vec2 a_texCoord0;
            attribute vec2 a_material;
            uniform mat4 u_camera;
            varying vec2 v_uv;
            varying vec2 v_material;
            void main() {
                v_uv = a_texCoord0;
                v_material = a_material;
                gl_Position = u_camera * vec4(a_position, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            uniform sampler2D u_back;
            uniform sampler2D u_faceOne;
            uniform sampler2D u_faceTwo;
            varying vec2 v_uv;
            varying vec2 v_material;
            void main() {
                if (v_material.x >= 2.0) {
                    gl_FragColor = vec4(vec3(0.94, 0.93, 0.91) * v_material.y, 1.0);
                    return;
                }
                vec2 corner = max(abs(v_uv - vec2(0.5)) - vec2(0.449, 0.461), vec2(0.0));
                if (length(corner / vec2(0.051, 0.039)) > 1.0) discard;
                vec3 art;
                if (gl_FrontFacing) {
                    art = texture2D(u_back, v_uv).rgb;
                } else if (v_material.x < 0.5) {
                    art = texture2D(u_faceOne, vec2(1.0 - v_uv.x, v_uv.y)).rgb;
                } else {
                    art = texture2D(u_faceTwo, vec2(1.0 - v_uv.x, v_uv.y)).rgb;
                }
                gl_FragColor = vec4(art * v_material.y, 1.0);
            }
            """;

    private final float[] vertices = new float[CARDS * VERTICES_PER_CARD * FLOATS_PER_VERTEX];
    private final Mesh mesh;
    private final ShaderProgram shader;
    private final PerspectiveCamera camera = new PerspectiveCamera();
    private int writeOffset;

    GdxRiffleShuffle() {
        shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!shader.isCompiled()) throw new IllegalStateException(shader.getLog());
        short[] indices = new short[CARDS * (STRIPS * 3 + 2) * 6];
        int next = 0;
        for (int card = 0; card < CARDS; card++) {
            int first = card * VERTICES_PER_CARD;
            for (int strip = 0; strip < STRIPS; strip++) {
                int a = first + strip * VERTICES_PER_STRIP;
                int b = a + VERTICES_PER_STRIP;
                next = quad(indices, next, a, b, b + 1, a + 1);
                next = quad(indices, next, a + 2, b + 2, b + 3, a + 3);
                next = quad(indices, next, a + 4, b + 4, b + 5, a + 5);
            }
            int last = first + STRIPS * VERTICES_PER_STRIP;
            next = quad(indices, next, first + 2, first + 4, first + 5, first + 3);
            next = quad(indices, next, last + 2, last + 4, last + 5, last + 3);
        }
        mesh = new Mesh(false, CARDS * VERTICES_PER_CARD, indices.length,
                new VertexAttribute(Usage.Position, 3, "a_position"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                new VertexAttribute(Usage.Generic, 2, "a_material"));
        mesh.setIndices(indices);
        camera.fieldOfView = 23f;
        camera.position.set(1.18f, 1.63f, 2.65f);
        camera.lookAt(0f, .24f, 0f);
        camera.near = .05f;
        camera.far = 10f;
    }

    void draw(float elapsed, int width, int height, Texture back, Texture faceOne, Texture faceTwo) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();

        float time = MathUtils.clamp(elapsed, 0f, CYCLE_SECONDS);
        float tuck = smooth((time - TUCK_START) / (TUCK_END - TUCK_START));
        writeOffset = 0;
        for (int card = 0; card < CARDS_PER_PACKET; card++) {
            writeCard(time, tuck, card, 0);
            writeCard(time, tuck, card, 1);
        }
        mesh.setVertices(vertices);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        shader.bind();
        faceTwo.bind(2);
        faceOne.bind(1);
        back.bind(0);
        shader.setUniformMatrix("u_camera", camera.combined);
        shader.setUniformi("u_back", 0);
        shader.setUniformi("u_faceOne", 1);
        shader.setUniformi("u_faceTwo", 2);
        mesh.render(shader, GL20.GL_TRIANGLES);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    private void writeCard(float time, float tuck, int card, int side) {
        int order = 2 * card + side;
        float launch = FIRST_RELEASE + order * RELEASE_SPACING;
        float fall = smooth((time - launch) / FALL_SECONDS);
        float heldLayer = Math.max(0f, card - Math.max(0f,
                (time - FIRST_RELEASE - side * RELEASE_SPACING) / (2f * RELEASE_SPACING)));
        float baseY = THICKNESS * MathUtils.lerp(heldLayer, order, fall)
                + .045f * MathUtils.sin(MathUtils.PI * fall);
        float bend = 2.15f * (1f - fall);
        float flat = .25f;
        float outer = MathUtils.lerp(MathUtils.lerp(.80f, .84f, fall), .5f, tuck);
        float direction = side == 0 ? -1f : 1f;
        float depthOffset = MathUtils.sin(order * 2.4f) * .012f * fall * (1f - tuck);
        float material = (card + side + 1) % 2;
        float edgeShade = .83f + .09f * (order % 2);
        for (int strip = 0; strip <= STRIPS; strip++) {
            float u = (float) strip / STRIPS;
            float curved = Math.max(0f, (u - flat) / (1f - flat));
            float angle = bend * curved;
            float along = bend > .0001f
                    ? Math.min(u, flat) + (1f - flat) * MathUtils.sin(angle) / bend : u;
            float lift = bend > .0001f
                    ? (1f - flat) * (1f - MathUtils.cos(angle)) / bend : 0f;
            float x = direction * (outer - along);
            float y = baseY + lift;
            float cornerInset = Math.max(0f, CORNER - Math.min(u, 1f - u));
            float halfWidth = WIDTH * .5f - CORNER
                    + (float) Math.sqrt(CORNER * CORNER - cornerInset * cornerInset);
            float z = direction * halfWidth;
            float texInset = .5f - halfWidth / WIDTH;
            float shade = .83f + .17f * MathUtils.cos(angle - .27f);
            vertex(x, y, depthOffset - z, texInset, u, material, shade);
            vertex(x, y, depthOffset + z, 1f - texInset, u, material, shade);
            vertex(x, y, depthOffset - z, 0f, u, 2f, edgeShade);
            vertex(x, y - THICKNESS, depthOffset - z, 0f, u, 2f, edgeShade);
            vertex(x, y, depthOffset + z, 1f, u, 2f, edgeShade);
            vertex(x, y - THICKNESS, depthOffset + z, 1f, u, 2f, edgeShade);
        }
    }

    private void vertex(float x, float y, float z, float u, float v, float material, float shade) {
        vertices[writeOffset++] = x;
        vertices[writeOffset++] = y;
        vertices[writeOffset++] = z;
        vertices[writeOffset++] = u;
        vertices[writeOffset++] = v;
        vertices[writeOffset++] = material;
        vertices[writeOffset++] = shade;
    }

    private static int quad(short[] indices, int next, int a, int b, int c, int d) {
        indices[next++] = (short) a;
        indices[next++] = (short) b;
        indices[next++] = (short) c;
        indices[next++] = (short) a;
        indices[next++] = (short) c;
        indices[next++] = (short) d;
        return next;
    }

    private static float smooth(float progress) {
        float t = MathUtils.clamp(progress, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    @Override
    public void dispose() {
        mesh.dispose();
        shader.dispose();
    }
}
