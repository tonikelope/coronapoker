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
    enum View {
        PERSPECTIVE("Perspectiva 3D"), OVERHEAD("Cenital"), FRONT("Frontal");
        final String label;
        View(String label) { this.label = label; }
        View next() { return switch (this) {
            case PERSPECTIVE -> OVERHEAD;
            case OVERHEAD -> FRONT;
            case FRONT -> PERSPECTIVE;
        }; }
    }

    static final float PREVIEW_EYE_X = 1.597f;
    static final float PREVIEW_EYE_Z = 2.735f;
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
            uniform sampler2D u_face;
            uniform float u_gameFilter;
            uniform float u_frontView;
            uniform sampler2D u_shadow;
            uniform float u_shadowTexel;
            varying vec3 v_light;
            varying vec2 v_uv;
            varying vec2 v_material;
            """ + CoronaPokerGdxTable.CARD_ARTWORK_SAMPLING_GLSL + """
            vec3 artwork(sampler2D image, vec2 uv) {
                if (u_gameFilter > 0.5) return sampleCardArtwork(image, uv).rgb;
                return texture2D(image, uv).rgb;
            }
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
                if (id < -3.5) {
                    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.30 * (1.0 - visibility()));
                    return;
                }
                vec3 color;
                if (id < -1.5) {
                    color = vec3(1.0, 0.99, 0.98);
                } else if (id < -0.5) {
                    color = artwork(u_back, v_uv);
                } else {
                    color = artwork(u_face, v_uv);
                }
                if (u_frontView > 0.5) {
                    gl_FragColor = vec4(color, 1.0);
                    return;
                }
                float fillLight = id < -1.5 ? 0.95 : (id < -0.5 ? 0.78 : 0.94);
                gl_FragColor = vec4(color * v_material.y * mix(fillLight, 1.0, visibility()), 1.0);
            }
            """;

    private final GdxShuffleGeometry geometry = new GdxShuffleGeometry();
    private final Mesh mesh;
    private final Mesh felt;
    private final ShaderProgram shader;
    private final ShaderProgram shadowShader;
    private final FrameBuffer shadowMap;
    private final OrthographicCamera light = new OrthographicCamera(2.6f, 2.2f);
    private final PerspectiveCamera perspective = new PerspectiveCamera();
    private final OrthographicCamera overhead = new OrthographicCamera();
    private final OrthographicCamera front = new OrthographicCamera();
    private float meshTime = Float.NaN;
    private float meshAspect;
    private boolean shadowReady;
    private GdxShuffleGeometry.Motion meshMotion;

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
        // Flat receiver in the same model coordinates as the deck and light.
        felt = new Mesh(true, 4, 6, mesh.getVertexAttributes());
        felt.setVertices(new float[]{
                -1.7f, 0,  1.2f, 0, 0, -4, 1,  1.7f, 0,  1.2f, 0, 0, -4, 1,
                 1.7f, 0, -1.2f, 0, 0, -4, 1, -1.7f, 0, -1.2f, 0, 0, -4, 1});
        felt.setIndices(new short[]{0, 1, 2, 0, 2, 3});
        perspective.fieldOfView = 20.25f;
        perspective.position.set(PREVIEW_EYE_X, 1.212f, PREVIEW_EYE_Z);
        perspective.lookAt(.030f, .240f, 0f);
        perspective.near = .5f;
        perspective.far = 6f;
        overhead.position.set(0f, 3f, 0f);
        overhead.direction.set(0f, -1f, 0f);
        overhead.up.set(0f, 0f, -1f);
        overhead.near = .5f;
        overhead.far = 5f;
        front.position.set(0f, 3f, 0f);
        front.direction.set(0f, -1f, 0f);
        front.up.set(-1f, 0f, 0f);
        front.near = .5f;
        front.far = 5f;
    }

    void prepare(float elapsed, View view) {
        prepare(elapsed, view, GdxShuffleGeometry.Motion.ORIGINAL, GdxShuffleGeometry.STANDARD_ASPECT);
    }

    void prepare(float elapsed, View view, GdxShuffleGeometry.Motion motion, float aspect) {
        float time = view == View.FRONT ? motion.duration() : elapsed;
        if (meshTime != time || meshMotion != motion || meshAspect != aspect) {
            meshAspect = aspect;
            meshMotion = motion;
            geometry.update(time, motion, aspect);
            mesh.setVertices(geometry.vertices);
            meshTime = time;
            shadowReady = false;
        }
        if (view == View.FRONT || shadowReady) return;
        shadowMap.begin();
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glClearColor(1f, 1f, 1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        shadowShader.bind();
        shadowShader.setUniformMatrix("u_light", light.combined);
        mesh.render(shadowShader, GL20.GL_TRIANGLES);
        shadowMap.end();
        shadowReady = true;
    }

    void draw(int x, int y, int width, int height, Texture back, GdxShuffleArtwork faces,
            View view, boolean gameFilter, int face, float frontScale) {
        begin(back, view, gameFilter ? GdxCardSmaaRenderer.RENDERING_SMOOTH : GdxCardSmaaRenderer.RENDERING_BALANCED);
        if (view == View.FRONT) {
            // Draw the same squared card twice, face-on and without directional lighting.
            int halfWidth = width / 2;
            front.viewportHeight = Math.max(1.12f, .81f * height / halfWidth) / frontScale;
            front.viewportWidth = front.viewportHeight * halfWidth / height;
            front.update();
            shader.setUniformMatrix("u_camera", front.combined);
            Gdx.gl.glViewport(x, y, halfWidth, height);
            GdxShuffleArtwork.bind(faces.face(face), 0, gameFilter);
            drawCard(1);
            Gdx.gl.glViewport(x + width - halfWidth, y, halfWidth, height);
            GdxShuffleArtwork.bind(back, 0, gameFilter);
            drawCard(1);
        } else {
            Gdx.gl.glViewport(x, y, width, height);
            float aspect = (float) width / height;
            Camera camera = view == View.OVERHEAD ? overhead : perspective;
            // Keep the original camera pose. The longer opening needs a small,
            // fixed framing margin so neither outer corner exits the panel.
            boolean extended = meshMotion != GdxShuffleGeometry.Motion.ORIGINAL;
            perspective.fieldOfView = extended ? 22.25f : 20.25f;
            camera.viewportWidth = view == View.OVERHEAD ? Math.max(extended ? 2.24f : 2.05f, aspect) : width;
            camera.viewportHeight = camera.viewportWidth / aspect;
            camera.update();
            shader.setUniformMatrix("u_camera", camera.combined);
            for (int order = 0; order < GdxShuffleGeometry.CARDS; order++) {
                GdxShuffleArtwork.bind(faces.face(GdxShuffleGeometry.faceIndex(order)), 1, gameFilter);
                drawCard(order);
            }
        }
        end();
    }

    /** At 1080p the Blender deck occupied 540 px of its 960 px canvas. Scale that framing with the table. */
    static float restWidth(float viewportWidth, float viewportHeight) {
        float canvasWidth = Math.min(viewportHeight * (960f / 1080f), viewportWidth * .78f);
        return canvasWidth * (540f / 960f);
    }

    /** The caller supplies the same camera and board plane used by the live table. */
    void drawOnTable(com.badlogic.gdx.math.Matrix4 projection, Texture back, Texture[] faces, int mode) {
        begin(back, faces == null ? View.OVERHEAD : View.PERSPECTIVE, mode);
        shader.setUniformMatrix("u_camera", projection);
        mesh.unbind(shader);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
                GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDepthMask(false);
        felt.render(shader, GL20.GL_TRIANGLES);
        Gdx.gl.glDepthMask(true);
        mesh.bind(shader);
        if (faces == null) {
            // The orthographic overhead view sees only backs and edges.
            back.bind(1);
            mesh.render(shader, GL20.GL_TRIANGLES, 0, mesh.getNumIndices(), false);
        } else {
            for (int order = 0; order < faces.length; order++) {
                GdxCardSampling.bind(faces[order], 1, mode);
                drawCard(order);
            }
        }
        end();
    }

    private void begin(Texture back, View view, int mode) {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        shader.bind();
        shadowMap.getColorBufferTexture().bind(2);
        GdxCardSampling.bind(back, 0, mode);
        shader.setUniformMatrix("u_light", light.combined);
        shader.setUniformi("u_shadow", 2);
        shader.setUniformf("u_shadowTexel", 1f / SHADOW_SIZE);
        shader.setUniformi("u_back", 0);
        shader.setUniformi("u_face", 1);
        shader.setUniformf("u_gameFilter", mode == GdxCardSmaaRenderer.RENDERING_BALANCED ? 0f : 1f);
        shader.setUniformf("u_frontView", view == View.FRONT ? 1f : 0f);
        shader.setUniformf("u_artworkSmooth", GdxCardSampling.shaderValue(mode));
        shader.setUniformf("u_artworkLodBias", CoronaPokerGdxTable.cardArtworkLodBias(mode));
        shader.setUniformf("u_extraArtworkSampling", 0f);
        shader.setUniformf("u_projectedArtwork", view == View.FRONT ? 0f : 88f / 63f);
        mesh.bind(shader);
    }

    private void end() {
        mesh.unbind(shader);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    private void drawCard(int order) {
        int count = GdxShuffleGeometry.INDICES_PER_CARD;
        mesh.render(shader, GL20.GL_TRIANGLES, order * count, count, false);
    }

    @Override public void dispose() {
        felt.dispose();
        mesh.dispose();
        shader.dispose();
        shadowShader.dispose();
        shadowMap.dispose();
    }
}
