/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/** Draws the shuffle mesh from either table view, with shadows shared by both cameras. */
final class GdxRiffleShuffle implements Disposable {

    private static final int SHADOW_SIZE = 1024;
    private static final String SHADOW_VERTEX = """
            attribute vec3 a_position;
            uniform mat4 u_light;
            void main() {
                gl_Position = u_light * vec4(a_position, 1.0);
            }
            """;
    private static final String SHADOW_FRAGMENT = """
            #ifdef GL_ES
            precision highp float;
            #endif
            void main() {
                float depth = gl_FragCoord.z * 255.0;
                gl_FragColor = vec4(floor(depth) / 255.0, fract(depth), 0.0, 1.0);
            }
            """;

    private static final String VERTEX_SHADER = """
            attribute vec3 a_position;
            attribute vec2 a_texCoord0;
            attribute vec2 a_material;
            uniform mat4 u_camera;
            uniform mat4 u_light;
            varying vec3 v_light;
            varying vec2 v_uv;
            varying vec2 v_material;
            void main() {
                v_uv = a_texCoord0;
                v_material = a_material;
                v_light = (u_light * vec4(a_position, 1.0)).xyz * 0.5 + 0.5;
                gl_Position = u_camera * vec4(a_position, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #ifdef GL_ES
            precision highp float;
            #endif
            uniform sampler2D u_back;
            uniform sampler2D u_faces;
            uniform sampler2D u_shadow;
            uniform float u_shadowTexel;
            varying vec3 v_light;
            varying vec2 v_uv;
            varying vec2 v_material;
            float visibility() {
                vec2 packedDepth = texture2D(u_shadow, v_light.xy).rg;
                float gap = v_light.z - dot(packedDepth, vec2(1.0, 1.0 / 255.0));
                // A lifted sheet casts a wider penumbra than one near contact.
                float radius = clamp(gap * 0.12, u_shadowTexel, 0.016);
                float lit = 0.0;
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        vec2 depth = texture2D(u_shadow, v_light.xy + vec2(float(x), float(y)) * radius).rg;
                        lit += smoothstep(-0.002, 0.001, dot(depth, vec2(1.0, 1.0 / 255.0)) - v_light.z + 0.002);
                    }
                }
                return lit / 9.0;
            }
            void main() {
                float id = v_material.x;
                vec3 color;
                if (id < -1.5) {
                    color = vec3(1.0, 0.99, 0.98);
                } else if (id < -0.5) {
                    color = texture2D(u_back, v_uv).rgb;
                } else {
                    vec2 cell = vec2(mod(id, 8.0), floor(id / 8.0));
                    vec2 inset = vec2(0.5 / 256.0, 0.5 / 352.0);
                    color = texture2D(u_faces, (cell + mix(inset, vec2(1.0) - inset, v_uv)) / 8.0).rgb;
                }
                float fillLight = id < -1.5 ? 0.95 : (id < -0.5 ? 0.78 : 0.94);
                gl_FragColor = vec4(color * v_material.y * mix(fillLight, 1.0, visibility()), 1.0);
            }
            """;

    private final GdxShuffleGeometry geometry = new GdxShuffleGeometry();
    private final Mesh mesh;
    private final ShaderProgram shader;
    private final ShaderProgram shadowShader;
    private final FrameBuffer shadowMap;
    private final OrthographicCamera light = new OrthographicCamera(2.6f, 2.2f);
    private final PerspectiveCamera perspective = new PerspectiveCamera();
    private final OrthographicCamera overhead = new OrthographicCamera();

    GdxRiffleShuffle() {
        shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!shader.isCompiled()) throw new IllegalStateException(shader.getLog());
        shadowShader = new ShaderProgram(SHADOW_VERTEX, SHADOW_FRAGMENT);
        if (!shadowShader.isCompiled()) throw new IllegalStateException(shadowShader.getLog());
        shadowMap = new FrameBuffer(Pixmap.Format.RGBA8888, SHADOW_SIZE, SHADOW_SIZE, true);
        shadowMap.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        light.position.set(3.2f, 3.2f, 2.4f);
        light.lookAt(0f, .22f, 0f);
        light.near = 1f;
        light.far = 8f;
        light.update();
        short[] indices = GdxShuffleGeometry.indices();
        mesh = new Mesh(false, GdxShuffleGeometry.CARDS * GdxShuffleGeometry.VERTICES_PER_CARD, indices.length,
                new VertexAttribute(Usage.Position, 3, "a_position"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                new VertexAttribute(Usage.Generic, 2, "a_material"));
        mesh.setIndices(indices);
        perspective.fieldOfView = 20.25f;
        perspective.position.set(1.597f, 1.212f, 2.735f);
        perspective.lookAt(.030f, .240f, 0f);
        perspective.near = .5f;
        perspective.far = 6f;
        overhead.position.set(0f, 3f, 0f);
        overhead.direction.set(0f, -1f, 0f);
        overhead.up.set(0f, 0f, -1f);
        overhead.near = .5f;
        overhead.far = 5f;
    }

    void draw(float elapsed, int x, int y, int width, int height, Texture back, Texture faces, boolean fromAbove) {
        float aspect = (float) width / height;
        Camera camera = fromAbove ? overhead : perspective;
        if (fromAbove) {
            camera.viewportWidth = Math.max(2.05f, aspect);
            camera.viewportHeight = camera.viewportWidth / aspect;
        } else {
            camera.viewportWidth = width;
            camera.viewportHeight = height;
        }
        camera.update();
        geometry.update(elapsed);
        mesh.setVertices(geometry.vertices);

        shadowMap.begin();
        Gdx.gl.glClearColor(1f, 1f, 1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        shadowShader.bind();
        shadowShader.setUniformMatrix("u_light", light.combined);
        mesh.render(shadowShader, GL20.GL_TRIANGLES);
        shadowMap.end(x, y, width, height);
        shader.bind();
        shadowMap.getColorBufferTexture().bind(2);
        faces.bind(1);
        back.bind(0);
        shader.setUniformMatrix("u_camera", camera.combined);
        shader.setUniformMatrix("u_light", light.combined);
        shader.setUniformi("u_shadow", 2);
        shader.setUniformf("u_shadowTexel", 1f / SHADOW_SIZE);
        shader.setUniformi("u_back", 0);
        shader.setUniformi("u_faces", 1);
        mesh.render(shader, GL20.GL_TRIANGLES);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    @Override public void dispose() {
        mesh.dispose();
        shader.dispose();
        shadowShader.dispose();
        shadowMap.dispose();
    }
}
