/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import java.nio.IntBuffer;

/** Table geometry in world space. The existing HUD keeps its own camera. */
final class GdxPerspectiveTable implements Disposable {

    private static final float FOV = 36f;
    private static final float ELEVATION = 37f;
    private static final float CARD_REST_HEIGHT = 0.72f;
    private static final float EDGE_MARGIN_SCALE = 1.065f;
    private static final int RAIL_STEPS = 36;
    private static final int CARD_STRIPS = 48;
    private static final int REVEAL_COLUMNS = 28;
    private static final int REVEAL_ROWS = 16;
    private static final int POCKET_COLUMNS = 32;
    private static final int MAX_CARD_VERTICES = Math.max(
            REVEAL_COLUMNS * REVEAL_ROWS, POCKET_COLUMNS * CARD_STRIPS) * 6;

    // Match the short dimension of the projected texel footprint. Sampling
    // the long dimension separately keeps angled ink from smearing sideways.
    static final String CARD_PROJECTION_SAMPLER = """
            vec4 sampleProjectedCard(sampler2D image, vec2 uv, float aspect, float smoothArtwork) {
                vec2 dx = dFdx(uv);
                vec2 dy = dFdy(uv);
                vec2 scale = vec2(1.0, aspect);
                vec2 tx = dx * scale;
                vec2 ty = dy * scale;
                float xx = dot(tx, tx);
                float yy = dot(ty, ty);
                float area = abs(tx.x * ty.y - tx.y * ty.x);
                float stretch = clamp(max(xx, yy) / max(area, 0.00000001), 1.0, 4.0);
                float bias = (smoothArtwork > 0.5 ? -0.25 : -0.42) - log2(stretch);
                if (smoothArtwork > 0.5) {
                    vec4 resolved = vec4(0.0);
                    resolved += texture2D(image, uv + dx * (-0.03125) + dy * (-0.34375), bias);
                    resolved += texture2D(image, uv + dx * (0.03125) + dy * (0.34375), bias);
                    resolved += texture2D(image, uv + dx * (-0.09375) + dy * (0.15625), bias);
                    resolved += texture2D(image, uv + dx * (0.09375) + dy * (-0.15625), bias);
                    resolved += texture2D(image, uv + dx * (-0.15625) + dy * (-0.46875), bias);
                    resolved += texture2D(image, uv + dx * (0.15625) + dy * (0.46875), bias);
                    resolved += texture2D(image, uv + dx * (-0.21875) + dy * (0.03125), bias);
                    resolved += texture2D(image, uv + dx * (0.21875) + dy * (-0.03125), bias);
                    resolved += texture2D(image, uv + dx * (-0.28125) + dy * (-0.28125), bias);
                    resolved += texture2D(image, uv + dx * (0.28125) + dy * (0.28125), bias);
                    resolved += texture2D(image, uv + dx * (-0.34375) + dy * (0.40625), bias);
                    resolved += texture2D(image, uv + dx * (0.34375) + dy * (-0.40625), bias);
                    resolved += texture2D(image, uv + dx * (-0.40625) + dy * (-0.09375), bias);
                    resolved += texture2D(image, uv + dx * (0.40625) + dy * (0.09375), bias);
                    resolved += texture2D(image, uv + dx * (-0.46875) + dy * (0.21875), bias);
                    resolved += texture2D(image, uv + dx * (0.46875) + dy * (-0.21875), bias);
                    return resolved * 0.0625;
                }
                vec2 along = (xx > yy ? dx : dy) * (1.0 - 1.0 / stretch);
                vec4 ink = texture2D(image, uv, bias) * 0.32;
                ink += (texture2D(image, uv - along * 0.24, bias)
                        + texture2D(image, uv + along * 0.24, bias)) * 0.24;
                ink += (texture2D(image, uv - along * 0.48, bias)
                        + texture2D(image, uv + along * 0.48, bias)) * 0.10;
                return ink;
            }
            """;
    private static final String VERTEX = """
            attribute vec3 a_position;
            attribute vec2 a_texCoord0;
            attribute float a_light;
            attribute vec3 a_normal;
            uniform mat4 u_projection;
            varying vec2 v_uv;
            varying float v_light;
            varying vec3 v_normal;
            varying vec3 v_position;
            void main() {
                v_uv = a_texCoord0;
                v_light = a_light;
                v_normal = a_normal;
                v_position = a_position;
                gl_Position = u_projection * vec4(a_position, 1.0);
            }
            """;
    private static final String FRAGMENT = """
            #ifdef GL_ES
            #extension GL_OES_standard_derivatives : enable
            precision mediump float;
            #endif
            uniform sampler2D u_texture;
            uniform sampler2D u_reverse;
            uniform int u_material;
            uniform int u_cardSide;
            uniform float u_showdownTint;
            uniform vec2 u_repeat;
            uniform float u_opacity;
            uniform float u_aspect;
            uniform vec3 u_eye;
            uniform float u_artworkSmooth;
            varying vec2 v_uv;
            varying float v_light;
            varying vec3 v_normal;
            varying vec3 v_position;
            """ + CARD_PROJECTION_SAMPLER + """
            float cardDistance(vec2 uv) {
                float radius = 0.075;
                vec2 side = (abs(uv - 0.5) - 0.5) * vec2(1.0, u_aspect) + radius;
                return length(max(side, 0.0)) + min(max(side.x, side.y), 0.0) - radius;
            }
            void main() {
                if (u_material == 1) {
                    vec2 uv = v_uv * vec2(1830.0, 220.0);
                    float grain = sin(uv.x + sin(uv.y * 1.6)) * cos(uv.y);
                    float seam = 1.0 - smoothstep(0.004, 0.015, abs(v_uv.y - 0.52));
                    vec3 leather = vec3(0.100, 0.082, 0.071);
                    float crown = v_uv.y - 0.45;
                    float glint = exp(-18.0 * crown * crown);
                    float key = 0.58 + 0.42 * cos(6.2832 * (v_uv.x - 0.73));
                    float texel = max(fwidth(uv.x), fwidth(uv.y));
                    float grainWeight = 1.0 - smoothstep(0.50, 1.85, texel);
                    vec3 colour = leather * (v_light + 0.11 * grain * grainWeight);
                    colour += vec3(0.054, 0.043, 0.034) * glint * key;
                    colour *= 1.0 - seam * 0.045;
                    gl_FragColor = vec4(colour, 1.0);
                    return;
                }
                if (u_material == 7) {
                    float shade = 1.0 - smoothstep(0.36, 0.50, length(v_uv - 0.5));
                    gl_FragColor = vec4(0.015, 0.012, 0.010, 0.30 * shade * u_opacity);
                    return;
                }
                if (u_material == 6) {
                    vec4 ink = sampleProjectedCard(u_texture, v_uv, 1.0, 0.0);
                    ink.rgb = mix(texture2D(u_texture, vec2(.5,.12)).rgb, ink.rgb, ink.a);
                    vec3 n = normalize(v_normal);
                    vec3 lightDir = normalize(vec3(-0.35,0.88,0.32));
                    float light = 0.58 + 0.42 * max(0.0, dot(n, lightDir));
                    vec3 eye = normalize(u_eye - v_position);
                    float sheen = 0.055 * pow(max(0.0, dot(n, normalize(lightDir + eye))), 36.0);
                    gl_FragColor = vec4(ink.rgb * light + sheen, u_opacity);
                    return;
                }
                if (u_material == 5) {
                    gl_FragColor = vec4(vec3(0.91, 0.90, 0.88) * v_light, u_opacity);
                    return;
                }
                if (u_material == 3) {
                    float distance = cardDistance((v_uv - 0.5) * 1.105 + 0.5);
                    float weight = 1.0 - smoothstep(-0.025, 0.035, distance);
                    gl_FragColor = vec4(0.014, 0.012, 0.010, weight * u_opacity * v_light);
                    return;
                }
                if (u_material == 2 || u_material == 4) {
                    vec2 uv = (v_uv - 0.5) * 1.065 + 0.5;
                    vec2 reverseUv = u_material == 2
                            ? vec2(uv.x, 1.0 - uv.y)
                            : vec2(1.0 - uv.x, uv.y);
                    vec4 ink = u_cardSide == 0 ? sampleProjectedCard(u_texture, uv, u_aspect, u_artworkSmooth)
                            : sampleProjectedCard(u_reverse, reverseUv, u_aspect, u_artworkSmooth);
                    vec3 normal = normalize(v_normal);
                    vec3 key = normalize(vec3(-0.28, 0.90, 0.32));
                    vec3 eye = normalize(u_eye - v_position);
                    float diffuse = max(0.0, dot(normal, key));
                    float light = min(1.015, 0.76 + 0.24 * diffuse / key.y);
                    float coat = 0.035 * pow(max(0.0, dot(normal, normalize(key + eye))), 72.0);
                    vec3 printed = mix(ink.rgb * light + vec3(coat), vec3(1.0, 0.925, 0.0), u_showdownTint);
                    gl_FragColor = vec4(printed, ink.a * u_opacity);
                    return;
                }
                vec4 surface = texture2D(u_texture, v_uv * u_repeat);
                float vignette = 1.0 - 0.21 * length((v_uv - 0.5) * vec2(0.8, 1.21));
                gl_FragColor = vec4(surface.rgb * v_light * vignette, 1.0);
            }
            """;

    private final PerspectiveCamera camera = new PerspectiveCamera();
    private final Matrix4 view = new Matrix4();
    private final Matrix4 board = new Matrix4();
    private final Matrix4 inverseView = new Matrix4();
    private final Vector3 near = new Vector3();
    private final Vector3 far = new Vector3();
    private final Vector3 cardSample = new Vector3();
    private final Vector3 pocketNormal = new Vector3();
    private boolean packetBend;
    private float pocketAnchorZ;
    private float packetLayerHeight;
    private int activePocketSlot = -1;
    private float revealBaseHeight;
    private final GdxCardPickMesh[] pocketPicks = {new GdxCardPickMesh(), new GdxCardPickMesh()};
    private final Vector3 dealSource = new Vector3();
    private final Vector3 dealTarget = new Vector3();
    private final Vector3 dealPosition = new Vector3();
    private final Vector3 projectedPoint = new Vector3();
    private final FloatArray cardVertices = new FloatArray(MAX_CARD_VERTICES * 6);
    private final IntBuffer framebufferBinding = BufferUtils.newIntBuffer(1);
    private final IntBuffer viewportBinding = BufferUtils.newIntBuffer(4);
    private ShaderProgram shader;
    private Mesh surface;
    private Mesh rail;
    private Mesh card;
    private final GdxCardShell shell;
    private final FloatArray markerVertices = new FloatArray();
    private int meshColumns;
    private int meshRows;
    private float cardWorldWidth;
    private FrameBuffer backdrop;
    private Texture backdropFelt;
    private boolean backdropStretch;
    private float width;
    private float height;
    private float boardCenterY;
    private float frontEdge;
    private float rearEdge;
    private float feltWidth;
    private float feltDepth;
    private float peek;
    private float peekVelocity;
    private final GdxPlasticCard.PeekShape pocketShape = new GdxPlasticCard.PeekShape();
    private boolean smoothArtwork;
    private float cameraProgress = Float.NaN;

    GdxPerspectiveTable(int msaaSamples) {
        shell = new GdxCardShell(msaaSamples);
    }

    private final GdxPlasticCard.UncoverShape uncoverShape = new GdxPlasticCard.UncoverShape();
    private float uncoverStartPeek;
    private boolean uncoveringPacket;

    void beginPocketUncover() { uncoverStartPeek = peek; }

    Matrix4 boardProjection() {
        return board;
    }

    void layout(float newWidth, float newHeight, float newBoardCenterY) {
        if (width == newWidth && height == newHeight
                && boardCenterY == newBoardCenterY) return;
        width = newWidth;
        height = newHeight;
        boardCenterY = newBoardCenterY;
        cameraProgress = Float.NaN;
        moveCamera(1f);
        frontEdge = surfaceAt(width * 0.5f, height * 0.10f, 0f).z;
        rearEdge = surfaceAt(width * 0.5f, height * 0.89f, 0f).z;
        if (surface != null) {
            surface.dispose();
            rail.dispose();
            surface = null;
            rail = null;
        }
    }

    void moveCamera(float progress) {
        if (cameraProgress == progress) return;
        cameraProgress = progress;
        float elevation = MathUtils.lerp(89.9f, ELEVATION, progress);
        camera.fieldOfView = MathUtils.lerp(2f, FOV, progress);
        float distance = height * 0.5f / MathUtils.tanDeg(camera.fieldOfView * 0.5f);
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.near = Math.max(distance * .10f, distance - height * 4.5f);
        camera.far = distance + height * 7f;
        camera.position.set(0f, distance * MathUtils.sinDeg(elevation),
                distance * MathUtils.cosDeg(elevation));
        camera.up.set(Vector3.Y);
        camera.lookAt(0f, 0f, 0f);
        camera.update();
        view.setToTranslation(0f, 2f * boardCenterY / height - 1f, 0f)
                .mul(camera.combined);
        inverseView.set(view).inv();
        board.set(view).translate(-width * 0.5f, CARD_REST_HEIGHT, boardCenterY)
                .rotate(Vector3.X, -90f);
        backdropFelt = null;
    }

    Matrix4 callTextProjection(float x, float y) {
        return board;
    }

    Vector3 boardToScreen(float x, float y) {
        projectedPoint.set(x, y, 0f).prj(board);
        return projectedPoint.set((projectedPoint.x + 1f) * width * 0.5f,
                (projectedPoint.y + 1f) * height * 0.5f, 0f);
    }

    Vector3 boardAt(float x, float y) {
        Vector3 point = surfaceAt(x, y, CARD_REST_HEIGHT);
        return point.set(point.x + width * 0.5f, boardCenterY - point.z, 0f);
    }

    private Vector3 surfaceAt(float x, float y, float level) {
        float nx = 2f * x / width - 1f;
        float ny = 2f * y / height - 1f;
        near.set(nx, ny, -1f).prj(inverseView);
        far.set(nx, ny, 1f).prj(inverseView);
        return near.lerp(far, (level - near.y) / (far.y - near.y));
    }

    void setSmoothArtwork(boolean smooth) {
        smoothArtwork = smooth;
    }

    float peekAmount() { return peek; }

    void updatePeek(boolean held, float delta) {
        // Exact critically damped response. Smooth velocity on reversal and
        // frame-rate-independent settling, without a rubber-like bounce.
        float dt = MathUtils.clamp(delta, 0f, 0.05f);
        float target = held ? 1f : 0f;
        float omega = held ? 19f : 25f;
        float offset = peek - target;
        float c = peekVelocity + omega * offset;
        float decay = (float) Math.exp(-omega * dt);
        peek = target + (offset + c * dt) * decay;
        peekVelocity = (peekVelocity - omega * c * dt) * decay;
        if (Math.abs(peek - target) < 0.0001f && Math.abs(peekVelocity) < 0.005f) {
            peek = target;
            peekVelocity = 0f;
        }
    }

    void coverCards() {
        peekVelocity = 0f;
        peek = 0f;
    }

    boolean peeking() {
        return peek > 0f;
    }

    void drawBackdrop(SpriteBatch batch, Texture felt, boolean stretch,
            int pixelWidth, int pixelHeight, float opacity) {
        if (backdrop == null || backdrop.getWidth() != pixelWidth
                || backdrop.getHeight() != pixelHeight || backdropFelt != felt
                || backdropStretch != stretch) {
            renderBackdrop(felt, stretch, pixelWidth, pixelHeight);
        }
        batch.begin();
        batch.setColor(1f, 1f, 1f, opacity);
        batch.draw(backdrop.getColorBufferTexture(), 0f, 0f, width, height,
                0f, 0f, 1f, 1f);
        batch.setColor(1f, 1f, 1f, 1f);
        batch.end();
    }

    private void renderBackdrop(Texture felt, boolean stretch, int pixelWidth, int pixelHeight) {
        GL20 gl = Gdx.gl;
        gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, framebufferBinding);
        gl.glGetIntegerv(GL20.GL_VIEWPORT, viewportBinding);
        try {
            if (backdrop == null || backdrop.getWidth() != pixelWidth
                    || backdrop.getHeight() != pixelHeight) {
                if (backdrop != null) backdrop.dispose();
                backdrop = new FrameBuffer(Pixmap.Format.RGBA8888, pixelWidth, pixelHeight, true);
                backdrop.getColorBufferTexture().setFilter(
                        Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            }
            backdrop.begin();
            gl.glClearColor(0.041f, 0.032f, 0.030f, 1f);
            gl.glDepthMask(true);
            gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
            gl.glEnable(GL20.GL_DEPTH_TEST);
            gl.glDepthFunc(GL20.GL_LEQUAL);
            drawGeometry(felt, stretch);
            backdropFelt = felt;
            backdropStretch = stretch;
        } finally {
            gl.glDisable(GL20.GL_DEPTH_TEST);
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebufferBinding.get(0));
            gl.glViewport(viewportBinding.get(0), viewportBinding.get(1),
                    viewportBinding.get(2), viewportBinding.get(3));
        }
    }

    private void drawGeometry(Texture felt, boolean stretch) {
        if (shader == null) {
            shader = new ShaderProgram(VERTEX, FRAGMENT);
            if (!shader.isCompiled()) {
                String log = shader.getLog();
                shader.dispose();
                shader = null;
                throw new IllegalStateException(log);
            }
        }
        if (surface == null) buildTable();
        begin(0, felt, felt, 1f);
        shader.setUniformf("u_repeat", stretch ? 1f : feltWidth / felt.getWidth(),
                stretch ? 1f : feltDepth / felt.getHeight());
        surface.render(shader, GL20.GL_TRIANGLES);
        begin(1, felt, felt, 1f);
        rail.render(shader, GL20.GL_TRIANGLES);
    }

    private void buildTable() {
        float middle = (frontEdge + rearEdge) * 0.5f;
        float radiusZ = (frontEdge - rearEdge) * 0.5f;
        float straight = width * 0.50f;
        float radiusX = width * 0.18f;
        feltWidth = (straight + radiusX) * 2f;
        feltDepth = frontEdge - rearEdge;
        FloatArray felt = new FloatArray();
        FloatArray padding = new FloatArray();
        float[][] crossSection = {
            {-4f, 0.2f, 0.53f}, {5f, 15f, 0.77f},
            {16f, 35f, 1.02f}, {33f, 48f, 1.30f},
            {55f, 52f, 1.50f}, {78f, 46f, 1.30f},
            {100f, 32f, 0.91f}, {114f, 9f, 0.61f},
            {120f, -26f, 0.34f}
        };
        int perimeter = 2 * (RAIL_STEPS + 1);
        for (int i = 0; i < perimeter; i++) {
            int j = (i + 1) % perimeter;
            Vector3 a = railPoint(i, straight, radiusX, radiusZ, middle, 0f, 0f);
            Vector3 b = railPoint(j, straight, radiusX, radiusZ, middle, 0f, 0f);
            put(felt, 0f, 0f, middle, 0.5f, 0.5f, 1f);
            putFelt(felt, a, middle);
            putFelt(felt, b, middle);
            for (int strip = 0; strip < crossSection.length - 1; strip++) {
                float[] inner = crossSection[strip];
                float[] outer = crossSection[strip + 1];
                Vector3 ai = railPoint(i, straight, radiusX, radiusZ, middle, inner[0], inner[1]);
                Vector3 bi = railPoint(j, straight, radiusX, radiusZ, middle, inner[0], inner[1]);
                Vector3 ao = railPoint(i, straight, radiusX, radiusZ, middle, outer[0], outer[1]);
                Vector3 bo = railPoint(j, straight, radiusX, radiusZ, middle, outer[0], outer[1]);
                float u0 = railU(i, straight, radiusX, radiusZ);
                float u1 = j == 0 ? 1f : railU(j, straight, radiusX, radiusZ);
                float v0 = strip / (float) (crossSection.length - 1);
                float v1 = (strip + 1f) / (crossSection.length - 1);
                put(padding, ai, u0, v0, inner[2]);
                put(padding, bi, u1, v0, inner[2]);
                put(padding, ao, u0, v1, outer[2]);
                put(padding, ao, u0, v1, outer[2]);
                put(padding, bi, u1, v0, inner[2]);
                put(padding, bo, u1, v1, outer[2]);
            }
        }
        surface = mesh(true, felt);
        rail = mesh(true, padding);
    }

    private static Vector3 railPoint(int index, float straight, float radiusX,
            float radiusZ, float middle, float offset, float elevation) {
        int side = index / (RAIL_STEPS + 1);
        int step = index % (RAIL_STEPS + 1);
        float angle = -90f + side * 180f + step * 180f / RAIL_STEPS;
        float x = (side == 0 ? straight : -straight)
                + (radiusX + offset) * MathUtils.cosDeg(angle);
        return new Vector3(x, elevation,
                middle + (radiusZ + offset) * MathUtils.sinDeg(angle));
    }

    private static float railU(int index, float straight, float radiusX, float radiusZ) {
        float arc = MathUtils.PI * (radiusX + radiusZ) * 0.5f;
        int side = index / (RAIL_STEPS + 1);
        int step = index % (RAIL_STEPS + 1);
        return (side * (arc + 2f * straight) + step * arc / RAIL_STEPS)
                / (2f * arc + 4f * straight);
    }

    private void putFelt(FloatArray target, Vector3 point, float middle) {
        put(target, point, 0.5f + point.x / feltWidth,
                0.5f + (point.z - middle) / feltDepth, 0.94f);
    }

    void beginPocketPair() {
        pocketAnchorZ = Float.NaN;
        for (GdxCardPickMesh pick : pocketPicks) pick.triangles.clear();
        activePocketSlot = -1;
        shell.beginPair();
    }
    void endPocketPair() {
        shell.endPair();
        activePocketSlot = -1;
    }

    int pocketSlotAt(float x, float y) {
        near.set(2f * x / width - 1f, 2f * y / height - 1f, -1f).prj(inverseView);
        far.set(2f * x / width - 1f, 2f * y / height - 1f, 1f).prj(inverseView).sub(near).nor();
        float nearest = Float.POSITIVE_INFINITY;
        int slot = -1;
        for (int i = 0; i < pocketPicks.length; i++) {
            float distance = pocketPicks[i].distance(near, far);
            if (distance < nearest) { nearest = distance; slot = i; }
        }
        return slot;
    }

    Vector3 projectWorld(Vector3 world) {
        projectedPoint.set(world).prj(view);
        return projectedPoint.set((projectedPoint.x + 1f) * width * 0.5f,
                (projectedPoint.y + 1f) * height * 0.5f, projectedPoint.z);
    }

    static float pocketLayerHeight(int slot, float width) {
        return Math.max(0, slot) * (GdxPlasticCard.thickness(width) + 0.10f);
    }

    private Vector3 pocketAt(float x, float y, float cardWidth, int slot, float spread) {
        Vector3 point = surfaceAt(x, y, 0f);
        float shown = -(point.x - Math.signum(point.x) * cardWidth * .22f * cameraProgress);
        float covered = (slot == 0 ? .16f : -.16f) * cardWidth;
        point.x = MathUtils.lerp(point.x, MathUtils.lerp(covered, shown, spread), cameraProgress);
        return point;
    }

    void drawPocket(Texture back, Texture face, float x, float y,
            float cardWidth, float cardHeight, float angle, boolean publicFace,
            float opacity, int slot) {
        activePocketSlot = slot;
        Vector3 center = pocketAt(x, y, cardWidth, slot, publicFace ? 1f : 0f);
        float cx = center.x;
        float cz = center.z;
        float lift = publicFace || face == back ? 0f : peek;
        // Keep both indexes beside each other in one centred packet.
        // The near short edges lift together, without separating the sheets.
        cz += cardHeight * 0.16f * lift;
        angle = MathUtils.lerp(angle, publicFace ? -angle : 0f, cameraProgress);
        float meshWidth = cardWidth * EDGE_MARGIN_SCALE;
        float meshHeight = cardHeight * EDGE_MARGIN_SCALE;
        // Both sheets share a bend in world space, as a fan lifted together.
        // Separate them along the surface normal by their actual thickness.
        if (Float.isNaN(pocketAnchorZ)) pocketAnchorZ = cz;
        packetLayerHeight = pocketLayerHeight(publicFace ? 1 - slot : slot, cardWidth);
        packetBend = true;
        fillPocket(cx, cz, meshWidth, meshHeight, angle, lift, true);
        renderPocket(3, back, back, 0.20f * opacity, cardHeight / cardWidth);
        fillPocket(cx, cz, meshWidth, meshHeight, angle, lift, false);
        renderPocket(2, publicFace ? face : back, face, opacity, cardHeight / cardWidth);
        packetBend = false;
    }

    void drawPocketFlip(Texture back, Texture face, float x, float y,
            float w, float h, float fan, float progress, float direction, float opacity, int slot) {
        activePocketSlot = slot;
        float p = MathUtils.clamp(progress, 0f, 1f);
        float turn = Interpolation.smoother.apply(MathUtils.clamp((p - .32f) / .68f, 0f, 1f));
        float lift = MathUtils.lerp(uncoverStartPeek, 1f,
                Interpolation.smoother.apply(Math.min(1f, p / .32f)));
        Vector3 center = pocketAt(x, y, w, slot, turn);
        float cx = center.x, cz = center.z + h * .16f * lift * (1f - turn);
        if (Float.isNaN(pocketAnchorZ)) pocketAnchorZ = cz;
        packetLayerHeight = pocketLayerHeight(slot, w);
        packetBend = true;
        uncoveringPacket = true;
        float bendingFan = MathUtils.lerp(fan, fan * turn, cameraProgress);
        float extent = .5f * (MathUtils.cosDeg(bendingFan)
                + w / h * Math.abs(MathUtils.sinDeg(bendingFan)));
        uncoverShape.set(lift, turn, extent);
        try {
            fillPocket(cx, cz, w * EDGE_MARGIN_SCALE, h * EDGE_MARGIN_SCALE,
                    bendingFan, lift, true);
            renderPocket(3, back, back, .20f * opacity, h / w);
            fillPocket(cx, cz, w * EDGE_MARGIN_SCALE, h * EDGE_MARGIN_SCALE,
                    bendingFan, lift, false);
            renderPocket(2, back, face, opacity, h / w);
        } finally {
            uncoveringPacket = packetBend = false;
        }
    }

    void drawPocketFlight(Texture back, float sourceX, float sourceY, float x, float y,
            float w, float h, float fan, float progress, int slot, int order) {
        Vector3 target = pocketAt(x, y, w, slot, 0f);
        dealTarget.set(target.x, pocketLayerHeight(slot, w), target.z);
        drawDeal(back, sourceX, sourceY, w, h, MathUtils.lerp(fan, 0f, cameraProgress), progress, order);
    }

    void drawBoardFlight(Texture back, float sourceX, float sourceY, float x, float y,
            float w, float h, float fan, float progress, int order) {
        dealTarget.set(x - width * 0.5f, 0f, boardCenterY - y);
        drawDeal(back, sourceX, sourceY, w, h, fan, progress, order);
    }

    private void drawDeal(Texture back, float sourceX, float sourceY, float w, float h,
            float fan, float progress, int order) {
        float launchHeight = w * 0.055f;
        Vector3 origin = surfaceAt(sourceX, sourceY, CARD_REST_HEIGHT + launchHeight);
        dealSource.set(origin.x, launchHeight, origin.z);
        GdxCardDealMotion.sample(dealPosition, dealSource, dealTarget, progress, w);
        float turn = Interpolation.smoother.apply(progress);
        float angle = MathUtils.lerp((order & 1) == 0 ? -18f : 18f, fan, turn);
        drawFlexibleFlight(back, dealPosition.x, dealPosition.z, w, h, angle,
                dealPosition.y, GdxCardDealMotion.airborne(progress));
    }

    private float markerCenterY, markerTilt, markerArtScale;

    void drawPositionMarker(Texture texture, float screenX, float screenY,
            float diameter, float hudSize, float rotation, float opacity, float onTable, float lift) {
        float level = 0f;
        for (int i=0; i<3 && onTable < 1f; i++) {
            float right = surfaceAt(screenX + hudSize * .5f, screenY, level).x;
            float left = surfaceAt(screenX - hudSize * .5f, screenY, level).x;
            float physical = MathUtils.lerp(right - left, diameter, onTable);
            level = lift + physical * (.5f * MathUtils.sinDeg((90f - ELEVATION) * (1f-onTable)) + .0225f);
            if (i == 2) diameter = physical;
        }
        markerArtScale = MathUtils.lerp(.5f, .48f, onTable);
        float radius = diameter * 0.5f;
        float thickness = diameter * 0.045f;
        markerTilt = (90f - ELEVATION) * (1f - onTable) * MathUtils.degreesToRadians;
        float elevation = lift + radius * Math.abs(MathUtils.sin(markerTilt));
        markerCenterY = elevation + thickness * 0.5f;
        Vector3 center = surfaceAt(screenX, screenY, markerCenterY);
        float cx = center.x, cz = center.z;
        // The shadow belongs to the support plane, never to a screen-space ellipse.
        markerVertices.clear();
        for (int i = 0; i < 96; i++) {
            float a = i * MathUtils.PI2 / 96f, b = (i + 1f) * MathUtils.PI2 / 96f;
            markerShadowVertex(cx, cz, radius * 1.10f, 0f, 0f);
            markerShadowVertex(cx, cz, radius * 1.10f, a, 1f);
            markerShadowVertex(cx, cz, radius * 1.10f, b, 1f);
        }
        begin(7, texture, texture, opacity * onTable * (float)Math.exp(-elevation / (radius * 0.65f)));
        shell.renderSolid(shader, markerVertices, width, height);
        markerVertices.clear();
        float bevel = thickness * 0.24f;
        float[] ys = {-thickness * .5f, -thickness * .5f + bevel, thickness * .5f - bevel, thickness * .5f};
        float[] rs = {.985f, 1f, 1f, .985f};
        for (int i = 0; i < 128; i++) {
            float a = i * MathUtils.PI2 / 128f, b = (i + 1f) * MathUtils.PI2 / 128f;
            markerVertex(cx, cz, radius, thickness * .5f, 0f, rotation, 0f, true, 1f);
            markerVertex(cx, cz, radius, thickness * .5f, a, rotation, .985f, true, 1f);
            markerVertex(cx, cz, radius, thickness * .5f, b, rotation, .985f, true, 1f);
            for (int j = 0; j < 3; j++) {
                float ny = j == 0 ? -.7f : j == 2 ? .7f : 0f;
                markerVertex(cx, cz, radius, ys[j], a, rotation, rs[j], false, ny);
                markerVertex(cx, cz, radius, ys[j+1], a, rotation, rs[j+1], false, ny);
                markerVertex(cx, cz, radius, ys[j+1], b, rotation, rs[j+1], false, ny);
                markerVertex(cx, cz, radius, ys[j], a, rotation, rs[j], false, ny);
                markerVertex(cx, cz, radius, ys[j+1], b, rotation, rs[j+1], false, ny);
                markerVertex(cx, cz, radius, ys[j], b, rotation, rs[j], false, ny);
            }
        }
        begin(6, texture, texture, opacity);
        shell.renderSolid(shader, markerVertices, width, height);
    }

    private void markerShadowVertex(float cx, float cz, float radius, float angle, float radial) {
        float x = MathUtils.cos(angle) * radial, z = MathUtils.sin(angle) * radial;
        markerVertices.add(cx + radius * x, .06f, cz + radius * z, .5f + .5f * x);
        markerVertices.add(.5f + .5f * z, 1f, 0f, 1f);
        markerVertices.add(0f);
    }

    private void markerVertex(float cx, float cz, float radius, float y, float angle,
            float rotation, float radial, boolean top, float normalY) {
        float turn = angle + rotation * MathUtils.degreesToRadians;
        float x = MathUtils.cos(turn), z = MathUtils.sin(turn);
        float cosine = MathUtils.cos(markerTilt), sine = MathUtils.sin(markerTilt);
        float radialZ = radius * radial * z;
        float normalRadial = top ? 0f : (float)Math.sqrt(1f - normalY * normalY);
        float nz = z * normalRadial;
        markerVertices.add(cx + radius * radial * x, markerCenterY + y * cosine - radialZ * sine,
                cz + y * sine + radialZ * cosine,
                top ? .5f + markerArtScale * radial * MathUtils.cos(angle) : .5f);
        markerVertices.add(top ? .5f + markerArtScale * radial * MathUtils.sin(angle) : .065f, 1f);
        markerVertices.add(x * normalRadial, normalY * cosine - nz * sine, normalY * sine + nz * cosine);
    }

    private void drawFlexibleFlight(Texture back, float cx, float cz,
            float w, float h, float fan, float lift, float progress) {
        drawContact(back, cx, cz, w, h, fan, 0.35f);
        meshColumns = 1;
        meshRows = CARD_STRIPS;
        cardWorldWidth = w;
        cardVertices.clear();
        for (int row = 0; row < meshRows; row++) {
            float v0 = row / (float) meshRows;
            float v1 = (row + 1f) / meshRows;
            putFlight(cx, cz, w, h, fan, lift, progress, 0f, v0);
            putFlight(cx, cz, w, h, fan, lift, progress, 0f, v1);
            putFlight(cx, cz, w, h, fan, lift, progress, 1f, v0);
            putFlight(cx, cz, w, h, fan, lift, progress, 1f, v0);
            putFlight(cx, cz, w, h, fan, lift, progress, 0f, v1);
            putFlight(cx, cz, w, h, fan, lift, progress, 1f, v1);
        }
        renderPocket(4, back, back, 1f, h / w);
    }

    private void putFlight(float cx, float cz, float w, float h, float fan,
            float lift, float progress, float u, float v) {
        GdxPlasticCard.flight(cardSample, w * EDGE_MARGIN_SCALE,
                h * EDGE_MARGIN_SCALE, u, v, progress);
        float cosine = MathUtils.cosDeg(fan);
        float sine = MathUtils.sinDeg(fan);
        put(cardVertices, cx + cardSample.x * cosine - cardSample.z * sine,
                CARD_REST_HEIGHT + lift + cardSample.y,
                cz + cardSample.x * sine + cardSample.z * cosine, u, v, 1f);
    }

    void drawBoardCard(Texture back, Texture face, float x, float y, float w,
            float h, float fan, float opacity, boolean highlight) {
        fillPocket(x - width * 0.5f, boardCenterY - y, w * EDGE_MARGIN_SCALE,
                h * EDGE_MARGIN_SCALE, fan, 0f, false);
        renderPocket(2, face, back, opacity, h / w, highlight ? 0.31f : 0f);
    }

    void drawBoardContact(Texture back, float x, float y, float w, float h, float opacity) {
        drawContact(back, x - width * 0.5f, boardCenterY - y, w, h, 0f, opacity);
    }

    private void drawContact(Texture back, float x, float z, float w,
            float h, float fan, float opacity) {
        fillPocket(x + 0.45f, z + 1.2f, w * 1.10f, h * 1.10f, fan, 0f, true);
        renderPocket(3, back, back, 0.16f * opacity, h / w);
    }

    void drawBoardPacketCard(Texture back, Texture face, float sourceX, float sourceY,
            float x, float y, float w, float h, boolean flying, float flightProgress,
            float turn, float direction, GdxPlasticCard.Motion motion, float layer, int slot) {
        if (flying) {
            dealTarget.set(x - width * 0.5f, layer, boardCenterY - y);
            drawDeal(back, sourceX, sourceY, w, h, 0f, flightProgress, 0);
        } else {
            revealBaseHeight = layer;
            try {
                drawPlasticReveal(back, face, x - width * 0.5f, boardCenterY - y,
                        w, h, 0f, turn, direction, 1f, motion);
            } finally { revealBaseHeight = 0f; }
        }
    }

    void drawBoardReveal(Texture back, Texture face, float x, float y,
            float w, float h, float progress, float direction, GdxPlasticCard.Motion motion) {
        drawPlasticReveal(back, face, x - width * 0.5f, boardCenterY - y,
                w, h, 0f, progress, direction, 1f, motion);
    }

    private void drawPlasticReveal(Texture back, Texture face, float cx, float cz,
            float w, float h, float fan, float progress, float direction, float opacity) {
        drawPlasticReveal(back, face, cx, cz, w, h, fan, progress, direction, opacity,
                GdxPlasticCard.STANDARD);
    }

    private void drawPlasticReveal(Texture back, Texture face, float cx, float cz,
            float w, float h, float fan, float progress, float direction, float opacity,
            GdxPlasticCard.Motion motion) {
        float p = MathUtils.clamp(progress, 0f, 1f);
        float pulse = MathUtils.sin(MathUtils.PI * p);
        float projectedWidth = MathUtils.lerp(w, w * 0.20f, pulse);
        drawContact(back, cx, cz, projectedWidth, h, fan, (1f - pulse * 0.42f) * opacity);
        meshColumns = REVEAL_COLUMNS;
        meshRows = REVEAL_ROWS;
        cardWorldWidth = w;
        cardVertices.clear();
        for (int row = 0; row < REVEAL_ROWS; row++) {
            float v0 = row / (float) REVEAL_ROWS;
            float v1 = (row + 1f) / REVEAL_ROWS;
            for (int column = 0; column < REVEAL_COLUMNS; column++) {
                float u0 = column / (float) REVEAL_COLUMNS;
                float u1 = (column + 1f) / REVEAL_COLUMNS;
                putReveal(cx, cz, w, h, u0, v0, p, direction, fan, motion);
                putReveal(cx, cz, w, h, u0, v1, p, direction, fan, motion);
                putReveal(cx, cz, w, h, u1, v0, p, direction, fan, motion);
                putReveal(cx, cz, w, h, u1, v0, p, direction, fan, motion);
                putReveal(cx, cz, w, h, u0, v1, p, direction, fan, motion);
                putReveal(cx, cz, w, h, u1, v1, p, direction, fan, motion);
            }
        }
        renderPocket(4, back, face, opacity, h / w);
    }

    private void putReveal(float cx, float cz, float w, float h,
            float u, float v, float progress, float direction, float fan, GdxPlasticCard.Motion motion) {
        float artU = (u - 0.5f) * EDGE_MARGIN_SCALE + 0.5f;
        float artV = (v - 0.5f) * EDGE_MARGIN_SCALE + 0.5f;
        GdxPlasticCard.reveal(cardSample, w, h, artU, artV, progress, direction, motion);
        float flex = MathUtils.sin(MathUtils.PI * progress);
        float light = 1f - flex * (0.055f + 0.055f * MathUtils.sin(MathUtils.PI * u));
        float cosine = MathUtils.cosDeg(fan);
        float sine = MathUtils.sinDeg(fan);
        put(cardVertices, cx + cardSample.x * cosine - cardSample.z * sine,
                CARD_REST_HEIGHT + revealBaseHeight + cardSample.y,
                cz + cardSample.x * sine + cardSample.z * cosine, u, v, light);
    }

    private void fillPocket(float x, float z, float w, float h, float fan,
            float liftAngle, boolean shadow) {
        cardVertices.clear();
        float cosine = MathUtils.cosDeg(fan);
        float sine = MathUtils.sinDeg(fan);
        pocketShape.set(liftAngle);
        int rows = liftAngle > 0.001f ? CARD_STRIPS : 1;
        int columns = liftAngle > 0.001f ? POCKET_COLUMNS : 1;
        meshColumns = columns;
        meshRows = rows;
        cardWorldWidth = w / EDGE_MARGIN_SCALE;
        for (int row = 0; row < rows; row++) {
            float v0 = row / (float) rows;
            float v1 = (row + 1f) / rows;
            for (int column = 0; column < columns; column++) {
                float u0 = column / (float) columns;
                float u1 = (column + 1f) / columns;
                putPocket(x, z, w, h, sine, cosine, shadow, liftAngle, u0, v0);
                putPocket(x, z, w, h, sine, cosine, shadow, liftAngle, u0, v1);
                putPocket(x, z, w, h, sine, cosine, shadow, liftAngle, u1, v0);
                putPocket(x, z, w, h, sine, cosine, shadow, liftAngle, u1, v0);
                putPocket(x, z, w, h, sine, cosine, shadow, liftAngle, u0, v1);
                putPocket(x, z, w, h, sine, cosine, shadow, liftAngle, u1, v1);
            }
        }
    }

    private void putPocket(float x, float z, float w, float h, float sine, float cosine,
            boolean shadow, float lift, float u, float v) {
        float wx, wz, light;
        if (packetBend) {
            float dx = (u - 0.5f) * w;
            float dz = (v - 0.5f) * h;
            float flatX = x + cosine * dx - sine * dz;
            float flatZ = z + sine * dx + cosine * dz;
            if (uncoveringPacket) {
                light = uncoverShape.sample(cardSample, pocketNormal, w, h,
                        flatX / w + 0.5f, (flatZ - pocketAnchorZ) / h + 0.5f);
                // Turning a packet reverses which sheet is on top. Lift the
                // support by one thickness when its normal points downwards.
                cardSample.y += pocketLayerHeight(1, cardWorldWidth) * Math.max(0f, -pocketNormal.y);
            } else {
                light = pocketShape.sample(cardSample, pocketNormal, w, h,
                        flatX / w + 0.5f, (flatZ - pocketAnchorZ) / h + 0.5f);
            }
            cardSample.mulAdd(pocketNormal, packetLayerHeight);
            wx = cardSample.x;
            wz = pocketAnchorZ + cardSample.z;
        } else {
            light = pocketShape.sample(cardSample, w, h, u, v);
            wx = x + cosine * cardSample.x - sine * cardSample.z;
            wz = z + sine * cardSample.x + cosine * cardSample.z;
        }
        if (shadow) {
            wx += cardSample.y * 0.31f;
            wz -= cardSample.y * 0.36f;
            light = (float) Math.exp(-cardSample.y / (w * 0.24f));
        }
        put(cardVertices, wx, shadow ? 0.32f : CARD_REST_HEIGHT + cardSample.y,
                wz, u, v, light);
    }

    private void renderPocket(int material, Texture top, Texture bottom, float opacity, float aspect) {
        renderPocket(material, top, bottom, opacity, aspect, 0f);
    }

    private void renderPocket(int material, Texture top, Texture bottom, float opacity,
            float aspect, float highlight) {
        begin(material, top, bottom, opacity);
        shader.setUniformf("u_aspect", aspect);
        shader.setUniformf("u_showdownTint", highlight);
        if (material == 2 || material == 4) {
            shell.pickTarget = activePocketSlot >= 0 ? pocketPicks[activePocketSlot] : null;
            shell.render(shader, cardVertices, meshColumns, meshRows, material, top, bottom,
                    cardWorldWidth, aspect, width, height);
            shell.pickTarget = null;
            return;
        }
        if (card == null) {
            card = new Mesh(false, MAX_CARD_VERTICES, 0,
                    new VertexAttribute(Usage.Position, 3, "a_position"),
                    new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                    new VertexAttribute(Usage.Generic, 1, "a_light"));
        }
        card.setVertices(cardVertices.items, 0, cardVertices.size);
        begin(material, top, bottom, opacity);
        shader.setUniformf("u_aspect", aspect);
        card.render(shader, GL20.GL_TRIANGLES);
    }

    private void begin(int material, Texture top, Texture bottom, float opacity) {
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
                GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        bottom.bind(1);
        top.bind(0);
        shader.bind();
        shader.setUniformMatrix("u_projection", view);
        shader.setUniformf("u_eye", camera.position);
        shader.setUniformi("u_texture", 0);
        shader.setUniformi("u_reverse", 1);
        shader.setUniformi("u_material", material);
        shader.setUniformi("u_cardSide", 0);
        shader.setUniformf("u_showdownTint", 0f);
        shader.setUniformf("u_opacity", opacity);
        shader.setUniformf("u_artworkSmooth", smoothArtwork ? 1f : 0f);
    }

    private static Mesh mesh(boolean fixed, FloatArray vertices) {
        Mesh mesh = new Mesh(fixed, vertices.size / 6, 0,
                new VertexAttribute(Usage.Position, 3, "a_position"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                new VertexAttribute(Usage.Generic, 1, "a_light"));
        mesh.setVertices(vertices.items, 0, vertices.size);
        return mesh;
    }

    private static void put(FloatArray into, Vector3 point, float u, float v, float light) {
        put(into, point.x, point.y, point.z, u, v, light);
    }

    private static void put(FloatArray into, float x, float y, float z,
            float u, float v, float light) {
        into.add(x, y, z, u);
        into.add(v, light);
    }

    @Override
    public void dispose() {
        if (surface != null) surface.dispose();
        if (rail != null) rail.dispose();
        if (card != null) card.dispose();
        if (shader != null) shader.dispose();
        if (backdrop != null) backdrop.dispose();
        shell.dispose();
    }
}
