/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
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
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;

/** Table geometry in world space. The existing HUD keeps its own camera. */
final class GdxPerspectiveTable implements Disposable {

    private static final float FOV = 36f;
    private static final float TABLE_ZOOM = 1.10f;
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
            uniform vec4 u_chipPreviewTint;
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
                if (u_material == 9) {
                    if (v_light < 0.0) discard; // The contact shadow has no solid surface.
                    gl_FragColor = vec4(0.0);
                    return;
                }
                if (u_material == 8 && v_light < 0.0) {
                    float shade = 1.0 - smoothstep(0.34, 0.50, length(v_uv - 0.5));
                    gl_FragColor = vec4(0.01, 0.01, 0.01, shade * 0.28 * (-v_light) * u_opacity);
                    return;
                }
                if (u_material == 6 || u_material == 8) {
                    vec4 ink = sampleProjectedCard(u_texture, v_uv, 1.0, 0.0);
                    ink.rgb = mix(texture2D(u_texture, vec2(.5,.12)).rgb, ink.rgb, ink.a);
                    vec3 n = normalize(v_normal);
                    vec3 lightDir = normalize(vec3(-0.35,0.88,0.32));
                    float light = 0.58 + 0.42 * max(0.0, dot(n, lightDir));
                    vec3 eye = normalize(u_eye - v_position);
                    float sheen = 0.055 * pow(max(0.0, dot(n, normalize(lightDir + eye))), 36.0);
                    vec3 colour = ink.rgb * light + sheen;
                    if (u_chipPreviewTint.a > 0.0) {
                        vec3 luma = vec3(0.2126, 0.7152, 0.0722);
                        float luminance = dot(ink.rgb, luma);
                        vec3 gold = u_chipPreviewTint.rgb * (0.55 + 0.45 * luminance)
                                * (0.65 + 0.35 * light) + sheen;
                        float goldLight = dot(gold, luma);
                        float peakLight = mix(dot(min(colour, vec3(1.0)), luma), 1.0, 0.17);
                        float lift = clamp((peakLight - goldLight) / max(1.0 - goldLight, 0.001), 0.0, 1.0);
                        colour = mix(colour, mix(gold, vec3(1.0), lift), u_chipPreviewTint.a);
                    }
                    gl_FragColor = vec4(colour, u_opacity * (u_material == 8 ? v_light : 1.0));
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
                    vec3 litInk = ink.rgb * light + vec3(coat);
                    vec3 printed = mix(litInk, vec3(1.0, 0.925, 0.0), u_showdownTint);
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
    private final Matrix4 layoutInverseView = new Matrix4();
    private final FloatArray[] chipVertices = {new FloatArray(), new FloatArray(), new FloatArray(), new FloatArray()};
    private final GdxFlyingChipBatch flyingChips = new GdxFlyingChipBatch();
    private final StandingChipPile[] potPiles = new StandingChipPile[GdxTableChipSlots.POT_SLOT_COUNT];
    private final StandingChipPile[] localPiles = new StandingChipPile[GdxTableChipSlots.LOCAL_SLOT_COUNT];
    private final StandingChipPile[] visiblePiles = new StandingChipPile[GdxTableChipSlots.LOCAL_SLOT_COUNT];
    private final BoundingBox standingChipBounds = new BoundingBox();
    private int visiblePileCount;
    private long chipFrame;
    private long standingChipFrameNanos;
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
    private final Vector3 dealTarget = new Vector3();
    private final Vector3 dealPosition = new Vector3();
    private final Vector3 dealPlanningTarget = new Vector3();
    private final ArrayList<GdxCardDealMotion.Pile> dealPiles = new ArrayList<>();
    private final ArrayList<GdxCardDealMotion.BoardCard> dealtBoardCards = new ArrayList<>(5);
    private final Vector3 projectedPoint = new Vector3();
    private final FloatArray cardVertices = new FloatArray(MAX_CARD_VERTICES * 6);
    private final FloatArray cardGrid = new FloatArray((REVEAL_COLUMNS + 1) * (REVEAL_ROWS + 1) * 6);
    private final IntBuffer framebufferBinding = BufferUtils.newIntBuffer(1);
    private final IntBuffer viewportBinding = BufferUtils.newIntBuffer(4);
    private ShaderProgram shader;
    private Mesh surface;
    private Mesh rail;
    private Mesh card;
    private final GdxCardShell shell;
    private final GdxCardShell pocketShell;
    private final GdxCardShell boardShell;
    private boolean boardCardPass;
    private final PocketDraw[] pocketDraws = {new PocketDraw(), new PocketDraw()};
    private final Vector3 cardBoundsMin = new Vector3();
    private final Vector3 cardBoundsMax = new Vector3();
    private final Vector3 cardBoundsCorner = new Vector3();
    private final Rectangle boardCardPixels = new Rectangle();
    private boolean trackBoardCardPixels;
    private boolean pocketPair;
    private int pocketDrawCount;
    private final FloatArray markerVertices = new FloatArray();
    private int meshColumns;
    private int meshRows;
    private float cardWorldWidth;
    private FrameBuffer backdrop;
    private Texture backdropFelt;
    private boolean backdropStretch;
    private float backdropTileSpan;
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

    private final GdxCardShell chipShell;

    GdxPerspectiveTable(int msaaSamples) {
        shell = new GdxCardShell(msaaSamples);
        pocketShell = new GdxCardShell(msaaSamples);
        boardShell = new GdxCardShell(msaaSamples);
        chipShell = new GdxCardShell(msaaSamples);
    }

    private final GdxPlasticCard.UncoverShape uncoverShape = new GdxPlasticCard.UncoverShape();
    private final GdxPlasticCard.CommunityTurn communityTurn = new GdxPlasticCard.CommunityTurn();
    private float uncoverStartPeek;
    private boolean uncoveringPacket;

    void beginPocketUncover() { uncoverStartPeek = peek; }

    void preparePocket(float aspect) {
        pocketShell.prepare(POCKET_COLUMNS, CARD_STRIPS, aspect);
        for (GdxCardPickMesh pick : pocketPicks) {
            pick.triangles.ensureCapacity(Math.max(0, MAX_CARD_VERTICES * 9 - pick.triangles.size));
        }
    }

    Matrix4 boardProjection() {
        return board;
    }

    private final Matrix4 feltProjection = new Matrix4();

    Matrix4 feltProjection() {
        return feltProjection;
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
        layoutInverseView.setToTranslation(0f, 2f * boardCenterY / height - 1f, 0f)
                .mul(camera.combined).inv();
        view.setToTranslation(0f, 2f * boardCenterY / height - 1f, 0f)
                .scale(MathUtils.lerp(1f, TABLE_ZOOM, progress), MathUtils.lerp(1f, TABLE_ZOOM, progress), 1f)
                .mul(camera.combined);
        inverseView.set(view).inv();
        board.set(view).translate(-width * 0.5f, CARD_REST_HEIGHT, boardCenterY)
                .rotate(Vector3.X, -90f);
        feltProjection.set(view).translate(-width * .5f, .02f, boardCenterY)
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

    Vector3 layoutToScreen(float x, float y) {
        float zoom = MathUtils.lerp(1f, TABLE_ZOOM, cameraProgress);
        return projectedPoint.set(width * .5f + (x - width * .5f) * zoom,
                boardCenterY + (y - boardCenterY) * zoom, 0f);
    }

    Vector3 tablePointToScreen(float x, float y, float elevation) {
        return projectWorld(near.set(x - width * .5f, CARD_REST_HEIGHT + elevation, boardCenterY - y));
    }

    Vector3 layoutBoardAt(float x, float y) {
        Vector3 point = surfaceAt(x, y, 0f, layoutInverseView);
        return point.set(point.x + width * 0.5f, boardCenterY - point.z, 0f);
    }

    Vector3 chipPointToScreen(float x, float y, float elevation) {
        return projectWorld(near.set(x - width * .5f, elevation, boardCenterY - y));
    }

    Vector3 boardAt(float x, float y) {
        Vector3 point = surfaceAt(x, y, CARD_REST_HEIGHT);
        return point.set(point.x + width * 0.5f, boardCenterY - point.z, 0f);
    }

    private Vector3 surfaceAt(float x, float y, float level) {
        return surfaceAt(x, y, level, inverseView);
    }

    private Vector3 surfaceAt(float x, float y, float level, Matrix4 inverse) {
        float nx = 2f * x / width - 1f;
        float ny = 2f * y / height - 1f;
        near.set(nx, ny, -1f).prj(inverse);
        far.set(nx, ny, 1f).prj(inverse);
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
        float omega = held ? 12f : 17f;
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

    void dragPeek(float amount) {
        peek = MathUtils.clamp(amount, 0f, 1f);
        peekVelocity = 0f;
    }

    void coverCards() {
        dragPeek(0f);
    }

    boolean peeking() {
        return peek > 0f;
    }

    void drawBackdrop(SpriteBatch batch, Texture felt, boolean stretch, float tileSpan,
            int pixelWidth, int pixelHeight, float opacity) {
        if (backdrop == null || backdrop.getWidth() != pixelWidth
                || backdrop.getHeight() != pixelHeight || backdropFelt != felt
                || backdropStretch != stretch || backdropTileSpan != tileSpan) {
            renderBackdrop(felt, stretch, tileSpan, pixelWidth, pixelHeight);
        }
        batch.begin();
        batch.setColor(1f, 1f, 1f, opacity);
        batch.draw(backdrop.getColorBufferTexture(), 0f, 0f, width, height,
                0f, 0f, 1f, 1f);
        batch.setColor(1f, 1f, 1f, 1f);
        batch.end();
    }

    private void renderBackdrop(Texture felt, boolean stretch, float tileSpan,
            int pixelWidth, int pixelHeight) {
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
            drawGeometry(felt, stretch, tileSpan);
            backdropFelt = felt;
            backdropStretch = stretch;
            backdropTileSpan = tileSpan;
        } finally {
            gl.glDisable(GL20.GL_DEPTH_TEST);
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebufferBinding.get(0));
            gl.glViewport(viewportBinding.get(0), viewportBinding.get(1),
                    viewportBinding.get(2), viewportBinding.get(3));
        }
    }

    private void prepareShader() {
        if (shader == null) {
            shader = new ShaderProgram(VERTEX, FRAGMENT);
            if (!shader.isCompiled()) {
                String log = shader.getLog();
                shader.dispose();
                shader = null;
                throw new IllegalStateException(log);
            }
            flyingChips.prepare(FRAGMENT);
        }
    }

    private void drawGeometry(Texture felt, boolean stretch, float tileSpan) {
        prepareShader();
        if (surface == null) buildTable();
        begin(0, felt, felt, 1f);
        shader.setUniformf("u_repeat", stretch ? 1f : feltWidth / tileSpan,
                stretch ? 1f : feltDepth / tileSpan);
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
        pocketPair = true;
        pocketDrawCount = 0;
    }
    void endPocketPair() {
        flushPocketPair();
        pocketPair = false;
        activePocketSlot = -1;
    }

    void flushPocketPair() {
        if (pocketDrawCount == 0) return;
        // Compute one crop for both cards so their shared depth and the
        // overlap during the bend use the same subpixel grid.
        resetCardBounds();
        for (int i = 0; i < pocketDrawCount; i++) {
            includeCardBounds(pocketDraws[i].vertices);
        }
        beginCroppedCardLayer(pocketShell);
        try {
            for (int i = 0; i < pocketDrawCount; i++) {
                PocketDraw draw = pocketDraws[i];
                drawShell(pocketShell, draw.vertices, draw.columns, draw.rows, draw.material,
                        draw.top, draw.bottom, draw.cardWidth, draw.aspect,
                        draw.opacity, draw.highlight, draw.pickTarget);
            }
        } finally {
            pocketShell.endPair();
            pocketDrawCount = 0;
        }
    }

    private void resetCardBounds() {
        cardBoundsMin.set(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        cardBoundsMax.set(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
    }

    private void includeCardBounds(FloatArray geometry) {
        float[] vertices = geometry.items;
        for (int at = 0; at < geometry.size; at += 6) {
            float x = vertices[at], y = vertices[at + 1], z = vertices[at + 2];
            cardBoundsMin.set(Math.min(cardBoundsMin.x, x), Math.min(cardBoundsMin.y, y), Math.min(cardBoundsMin.z, z));
            cardBoundsMax.set(Math.max(cardBoundsMax.x, x), Math.max(cardBoundsMax.y, y), Math.max(cardBoundsMax.z, z));
        }
    }

    private void beginCroppedCardLayer(GdxCardShell target) {
        beginCroppedLayer(target, view, width, height);
    }

    private void beginCroppedLayer(GdxCardShell target, Matrix4 projection, float width, float height) {
        float left = width, right = 0f, bottom = height, top = 0f;
        for (int corner = 0; corner < 8; corner++) {
            cardBoundsCorner.set((corner & 1) == 0 ? cardBoundsMin.x : cardBoundsMax.x,
                    (corner & 2) == 0 ? cardBoundsMin.y : cardBoundsMax.y,
                    (corner & 4) == 0 ? cardBoundsMin.z : cardBoundsMax.z);
            cardBoundsCorner.prj(projection);
            float x = (cardBoundsCorner.x + 1f) * width * .5f;
            float y = (cardBoundsCorner.y + 1f) * height * .5f;
            left = Math.min(left, x);
            right = Math.max(right, x);
            bottom = Math.min(bottom, y);
            top = Math.max(top, y);
        }
        int w = Gdx.graphics.getBackBufferWidth(), h = Gdx.graphics.getBackBufferHeight();
        // Pad for the shell thickness and the downsample footprint.
        int x0 = MathUtils.clamp(MathUtils.floor(left * w / width) - 8, 0, w - 1);
        int x1 = MathUtils.clamp(MathUtils.ceil(right * w / width) + 8, x0 + 1, w);
        int y0 = MathUtils.clamp(MathUtils.floor(bottom * h / height) - 8, 0, h - 1);
        int y1 = MathUtils.clamp(MathUtils.ceil(top * h / height) + 8, y0 + 1, h);
        if (target != null) target.beginPair(x0, y0, x1 - x0, y1 - y0);
        if (trackBoardCardPixels && (target == null || target == boardShell)) {
            if (boardCardPixels.width == 0f) {
                boardCardPixels.set(x0, y0, x1 - x0, y1 - y0);
            } else {
                boardCardPixels.merge(x0, y0).merge(x1, y1);
            }
        }
    }

    private static final class PocketDraw {
        final FloatArray vertices = new FloatArray(MAX_CARD_VERTICES * 6);
        int columns, rows, material;
        float cardWidth, aspect, opacity, highlight;
        Texture top, bottom;
        GdxCardPickMesh pickTarget;
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

    static float showdownTintAmount(boolean highlighted) {
        return highlighted ? .31f : 0f;
    }

    static float pocketLayerHeight(int slot, float width) {
        return Math.max(0, slot) * (GdxPlasticCard.thickness(width) + 0.10f);
    }

    private static float coveredFan(int slot) {
        return slot == 0 ? -3f : 3f;
    }

    private Vector3 pocketAt(float x, float y, float cardWidth, int slot, float spread) {
        Vector3 point = surfaceAt(x, y, 0f, layoutInverseView);
        float shown = -(point.x - Math.signum(point.x) * cardWidth * .22f * cameraProgress);
        float covered = (slot == 0 ? .18f : -.18f) * cardWidth;
        point.x = MathUtils.lerp(point.x, MathUtils.lerp(covered, shown, spread), cameraProgress);
        return point;
    }

    Vector3 pocketDealSource(float firstX, float firstY, float secondX, float secondY, float cardWidth) {
        Vector3 first = pocketAt(firstX, firstY, cardWidth, 0, 0f);
        float x = first.x, z = first.z;
        Vector3 second = pocketAt(secondX, secondY, cardWidth, 1, 0f);
        return projectWorld(near.set((x + second.x) * .5f, CARD_REST_HEIGHT,
                (z + second.z) * .5f));
    }

    void drawPocket(Texture back, Texture face, float x, float y,
            float cardWidth, float cardHeight, float angle, boolean publicFace,
            float opacity, int slot) {
        drawPocket(back, face, x, y, cardWidth, cardHeight, angle, publicFace,
                opacity, slot, GdxPocketOrder.Pose.resting(slot), false);
    }

    void drawPocket(Texture back, Texture face, float x, float y,
            float cardWidth, float cardHeight, float angle, boolean publicFace,
            float opacity, int logicalSlot, GdxPocketOrder.Pose pose, boolean highlight) {
        activePocketSlot = logicalSlot;
        int slot = pose.slot();
        Vector3 center = pocketAt(x, y, cardWidth, slot, publicFace ? 1f : 0f);
        float cx = center.x;
        float cz = center.z;
        float lift = publicFace || face == back ? 0f : peek;
        if (!publicFace) cx += (pose.x() - GdxPocketOrder.Pose.resting(slot).x()) * cardWidth * cameraProgress;
        // A compact fan remains a fan while being lifted. Both sheets follow
        // the same broad bend, retaining their full material size.
        cz += cardHeight * 0.06f * lift;
        angle = MathUtils.lerp(angle, publicFace ? -angle : pose.fan(), cameraProgress);
        float meshWidth = cardWidth * EDGE_MARGIN_SCALE;
        float meshHeight = cardHeight * EDGE_MARGIN_SCALE;
        // Both sheets share a bend in world space, as a fan lifted together.
        // Separate them along the surface normal by their actual thickness.
        if (Float.isNaN(pocketAnchorZ)) pocketAnchorZ = cz;
        packetLayerHeight = publicFace ? pocketLayerHeight(1 - slot, cardWidth)
                : pose.layers() * pocketLayerHeight(1, cardWidth) + pose.lift() * cardWidth;
        packetBend = true;
        fillPocket(cx, cz, meshWidth, meshHeight, angle, lift, true);
        renderPocket(3, back, back, 0.20f * opacity, cardHeight / cardWidth);
        fillPocket(cx, cz, meshWidth, meshHeight, angle, lift, false);
        renderPocket(2, publicFace ? face : back, face, opacity, cardHeight / cardWidth, showdownTintAmount(highlight));
        packetBend = false;
    }

    void drawPocketFlip(Texture back, Texture face, float x, float y,
            float w, float h, float fan, float progress, GdxPlasticCard.Motion motion,
            float opacity, int logicalSlot, int slot) {
        activePocketSlot = logicalSlot;
        float p = MathUtils.clamp(progress, 0f, 1f);
        float turn = GdxPlasticCard.uncoverTurn(uncoverStartPeek, p, motion);
        float lift = GdxPlasticCard.uncoverLift(uncoverStartPeek, p, motion);
        Vector3 center = pocketAt(x, y, w, slot, turn);
        float cx = center.x, cz = center.z + h * .06f * lift * (1f - turn);
        if (Float.isNaN(pocketAnchorZ)) pocketAnchorZ = cz;
        packetLayerHeight = pocketLayerHeight(slot, w);
        packetBend = true;
        uncoveringPacket = true;
        float fanPulse = 4f * p * (1f - p);
        float fanScale = 1f + 0.70f * motion.corner() * fanPulse * fanPulse;
        float bendingFan = MathUtils.lerp(fan,
                MathUtils.lerp(coveredFan(slot), fan, turn) * fanScale, cameraProgress);
        float angle = Math.abs(bendingFan);
        float extent = .5f * (MathUtils.cosDeg(angle) + w / h * MathUtils.sinDeg(angle));
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

    static final class DealRoute {
        private final Vector3 source = new Vector3();
        final GdxPlasticCard.Motion style;
        private final float flightSeconds;
        private float dealerU, dealerV;
        private GdxCardDealMotion.Route route;
        private float width, height, camera;

        DealRoute() {
            style = GdxPlasticCard.STANDARD;
            flightSeconds = 0f;
        }

        DealRoute(long sequence, float flightSeconds) {
            style = GdxPlasticCard.motion(sequence);
            this.flightSeconds = flightSeconds;
        }
    }

    void clearDealPiles() {
        dealPiles.clear();
    }

    void addDealPile(float x, float y, float radius, float top) {
        dealPiles.add(new GdxCardDealMotion.Pile(x - width * .5f, boardCenterY - y, radius, top));
    }

    void clearDealtBoardCards() {
        dealtBoardCards.clear();
    }

    void addDealtBoardCard(float x, float y, float w, float h) {
        dealtBoardCards.add(new GdxCardDealMotion.BoardCard(x - width * .5f,
                boardCenterY - y, w, h));
    }

    void drawPocketFlight(Texture back, float sourceX, float sourceY, float x, float y,
            float w, float h, float fan, float progress, int slot, int order, DealRoute route) {
        Vector3 target = pocketAt(x, y, w, slot, 0f);
        dealTarget.set(target.x, pocketLayerHeight(slot, w), target.z);
        drawDeal(back, sourceX, sourceY, w, h, MathUtils.lerp(fan, coveredFan(slot), cameraProgress),
                progress, order, dealTarget.y, route, false);
    }

    void drawBoardFlight(Texture back, float sourceX, float sourceY, float x, float y,
            float w, float h, float fan, float progress, int order, DealRoute route) {
        dealTarget.set(x - width * 0.5f, 0f, boardCenterY - y);
        drawDeal(back, sourceX, sourceY, w, h, fan, progress, order, 0f, route, true);
    }

    private void drawDeal(Texture back, float sourceX, float sourceY, float w, float h,
            float fan, float progress, int order, float lowestLayer, DealRoute plan, boolean community) {
        float startAngle = ((order & 1) == 0 ? -1f : 1f)
                * (18f + (plan.style.bend() - 1f) * 32f);
        if (plan.route == null) {
            // Lock the release point even if the dealer's seat moves after the throw.
            plan.dealerU = sourceX / width;
            plan.dealerV = sourceY / height;
        }
        if (plan.route == null || plan.width != width || plan.height != height
                || plan.camera != cameraProgress) {
            float launchHeight = w * (0.055f + plan.style.tempo() * 0.34f);
            dealOriginAtHeight(plan.source, plan.dealerU * width, plan.dealerV * height, launchHeight);
            dealPlanningTarget.set(dealTarget.x, lowestLayer, dealTarget.z);
            float side = width * .5f - w * .5f;
            var bounds = new GdxCardDealMotion.Bounds(
                    Math.min(-side, Math.min(plan.source.x, dealTarget.x)),
                    Math.max(side, Math.max(plan.source.x, dealTarget.x)),
                    Math.min(rearEdge + h * .5f, Math.min(plan.source.z, dealTarget.z)),
                    Math.max(frontEdge - h * .5f, Math.max(plan.source.z, dealTarget.z)));
            plan.route = community
                    ? GdxCardDealMotion.planCommunity(plan.source, dealPlanningTarget, w, h,
                            startAngle, fan, CARD_REST_HEIGHT, dealPiles,
                            dealtBoardCards, bounds)
                    : GdxCardDealMotion.plan(plan.source, dealPlanningTarget, w, h,
                            startAngle, fan, CARD_REST_HEIGHT, dealPiles, bounds);
            plan.width = width;
            plan.height = height;
            plan.camera = cameraProgress;
        }
        float pathProgress = community ? GdxCardDealMotion.communityProgress(progress, plan.flightSeconds, w, plan.route)
                : progress;
        GdxCardDealMotion.sample(dealPosition, plan.source, dealTarget, pathProgress, w, plan.route, community);
        float angle = GdxCardDealMotion.angle(startAngle, fan, pathProgress, plan.route);
        float shadow = .35f;
        if (community) {
            float heightFraction = MathUtils.clamp((dealPosition.y - dealTarget.y) / (w * .15f), 0f, 1f);
            float distanceFromFelt = Interpolation.smooth.apply(heightFraction);
            shadow = MathUtils.lerp(1f, shadow, distanceFromFelt);
        }
        drawFlexibleFlight(back, dealPosition.x, dealPosition.z, w, h, angle,
                dealPosition.y, plan.route.airborne(pathProgress), plan.style.bend(), shadow);
    }

    void dealOrigin(Vector3 out, float screenX, float screenY, float cardWidth) {
        dealOriginAtHeight(out, screenX, screenY, cardWidth * .055f);
    }

    private void dealOriginAtHeight(Vector3 out, float screenX, float screenY, float launchHeight) {
        Vector3 origin = surfaceAt(screenX, screenY, CARD_REST_HEIGHT + launchHeight);
        out.set(origin.x, launchHeight, origin.z);
    }

    void beginChipRack() {
        for (FloatArray vertices : chipVertices) vertices.clear();
        flyingChips.clear();
        resetCardBounds();
    }

    void beginStandingChipRack(long frameNanos) {
        standingChipFrameNanos = frameNanos;
        visiblePileCount = 0;
        standingChipBounds.inf();
    }

    void clearLocalStackBetPreview() {
        for (StandingChipPile pile : localPiles) {
            if (pile != null) pile.previewFade = null;
        }
    }

    void beginChipFrame() {
        chipFrame++;
    }

    void addStandingChipPile(boolean pot, int slot, int pileIndex, int column,
            int seed, float x, float y, int count, float diameter, float thickness,
            float depthSway, GdxTableChipRack.Impact impact, int previewCount) {
        StandingChipPile[] piles = pot ? potPiles : localPiles;
        StandingChipPile pile = piles[slot];
        if (pile == null) {
            pile = new StandingChipPile();
            piles[slot] = pile;
        }
        if (pile.drawnFrame < chipFrame - 1 || pile.column != column
                || pile.index != pileIndex || pile.seed != seed) {
            pile.previewFade = null;
        }
        // Vertices are in table space. A camera move only changes projection;
        // a resized table or a landing that moves the pile changes its mesh.
        boolean moving = impact != null && impact.moving();
        if (pile.mesh == null || moving || pile.moving || pile.count != count
                || pile.column != column || pile.index != pileIndex || pile.seed != seed
                || pile.x != x || pile.y != y || pile.tableWidth != width
                || pile.boardY != boardCenterY || pile.diameter != diameter) {
            int color = GdxChipDenominations.color(column);
            FloatArray geometry = chipVertices[color];
            geometry.clear();
            resetCardBounds();
            for (int level = 0; level < count; level++) {
                float offset = CoronaPokerGdxTable.detailedStackChipLateralOffset(seed, column, level, diameter);
                float rotation = CoronaPokerGdxTable.tableChipRotation(seed, column, pileIndex, level);
                float dx = impact == null ? 0f : impact.offsetX(level, diameter);
                float dy = impact == null ? 0f : impact.offsetY(level, diameter) * depthSway;
                int start = geometry.size;
                addTableChip(color, x + offset + dx, y + dy, diameter,
                        level * thickness, thickness, rotation, level == 0);
                pile.verticesPerTopChip = (geometry.size - start) / 9;
            }
            pile.mesh = GdxCardShell.uploadSolid(pile.mesh, geometry);
            pile.min.set(cardBoundsMin);
            pile.max.set(cardBoundsMax);
            pile.count = count;
            pile.column = column;
            pile.index = pileIndex;
            pile.seed = seed;
            pile.x = x;
            pile.y = y;
            pile.tableWidth = width;
            pile.boardY = boardCenterY;
            pile.diameter = diameter;
            pile.moving = moving;
        }
        if (previewCount > 0 && pile.previewFade == null) pile.previewFade = new ChipPreviewFade();
        if (pile.previewFade != null) {
            pile.previewFade.retarget(count, previewCount, standingChipFrameNanos);
        }
        pile.drawnFrame = chipFrame;
        visiblePiles[visiblePileCount++] = pile;
        standingChipBounds.ext(pile.min).ext(pile.max);
    }

    void drawStandingChipRack(Texture[] faces, float opacity, Color previewColor, float pulse) {
        if (visiblePileCount == 0) return;
        cardBoundsMin.set(standingChipBounds.min);
        cardBoundsMax.set(standingChipBounds.max);
        begin(8, faces[0], faces[0], opacity);
        beginCroppedCardLayer(chipShell);
        try {
            chipShell.renderLayer(() -> {
                // Depth and blending are shared across the rack. Keep the
                // original denomination order and then the original pile order.
                for (int color = 0; color < faces.length; color++) {
                    faces[color].bind(0);
                    int column = GdxChipDenominations.columnForColor(color);
                    for (int p = 0; p < visiblePileCount; p++) {
                        StandingChipPile pile = visiblePiles[p];
                        if (pile.column != column) continue;
                        drawStandingChipPile(pile, previewColor, pulse);
                    }
                }
            }, width, height);
        } finally {
            chipShell.endPair();
        }
    }

    private void drawStandingChipPile(StandingChipPile pile, Color gold, float pulse) {
        ChipPreviewFade fade = pile.previewFade;
        if (fade == null || pulse <= 0f) {
            pile.mesh.render(shader, GL20.GL_TRIANGLES);
            return;
        }
        float progress = fade.progress(standingChipFrameNanos);
        if (progress >= 1f) {
            int firstTinted = Math.min(pile.count, fade.firstTinted);
            if (firstTinted > 0) drawStandingChipRange(pile, 0, firstTinted);
            if (firstTinted == pile.count) return;
            shader.setUniformf("u_chipPreviewTint", gold.r, gold.g, gold.b, pulse * .88f);
            drawStandingChipRange(pile, firstTinted, pile.count);
        } else {
            // Chips with the same opacity share one draw in the original mesh.
            // Both sides of a changing selection can fade within the same pile.
            int first = 0;
            float strength = fade.strength(0, progress);
            for (int level = 1; level < pile.count; level++) {
                float nextStrength = fade.strength(level, progress);
                if (nextStrength == strength) continue;
                shader.setUniformf("u_chipPreviewTint", gold.r, gold.g, gold.b, pulse * .88f * strength);
                drawStandingChipRange(pile, first, level);
                first = level;
                strength = nextStrength;
            }
            shader.setUniformf("u_chipPreviewTint", gold.r, gold.g, gold.b, pulse * .88f * strength);
            drawStandingChipRange(pile, first, pile.count);
        }
        shader.setUniformf("u_chipPreviewTint", 0f, 0f, 0f, 0f);
    }

    private void drawStandingChipRange(StandingChipPile pile, int first, int end) {
        // The base level includes the contact shadow, so count backwards from
        // the end of the mesh for any range that starts above the base.
        int total = pile.mesh.getNumVertices();
        int offset = first == 0 ? 0 : total - (pile.count - first) * pile.verticesPerTopChip;
        int limit = total - (pile.count - end) * pile.verticesPerTopChip;
        pile.mesh.render(shader, GL20.GL_TRIANGLES, offset, limit - offset);
    }

    private static final class ChipPreviewFade {
        private static final long DURATION_NANOS = 160_000_000L;

        private float[] startStrengths = new float[0];
        private int count;
        private int firstTinted = Integer.MAX_VALUE;
        private long startedAt;

        void retarget(int chipCount, int selected, long now) {
            int newFirst = selected == 0 ? Integer.MAX_VALUE : chipCount - selected;
            if (newFirst == firstTinted && chipCount == count) return;
            float progress = progress(now);
            if (startStrengths.length < chipCount) {
                startStrengths = Arrays.copyOf(startStrengths, chipCount);
            }
            for (int level = 0; level < chipCount; level++) {
                startStrengths[level] = level < count ? strength(level, progress) : 0f;
            }
            count = chipCount;
            firstTinted = newFirst;
            startedAt = now;
        }

        float progress(long now) {
            float fraction = MathUtils.clamp((now - startedAt) / (float) DURATION_NANOS, 0f, 1f);
            return Interpolation.smoother.apply(fraction);
        }

        float strength(int level, float progress) {
            return MathUtils.lerp(startStrengths[level], level >= firstTinted ? 1f : 0f, progress);
        }
    }

    private static final class StandingChipPile {
        private long drawnFrame;
        private Mesh mesh;
        private final Vector3 min = new Vector3(), max = new Vector3();
        private int count, column, index, seed;
        private int verticesPerTopChip;
        private ChipPreviewFade previewFade;
        private float x, y, tableWidth, boardY, diameter;
        private boolean moving;
    }

    private final Quaternion chipAttitude = new Quaternion();
    private final Vector3 chipNormal = new Vector3();

    /** Extruded chip, with the base height measured from the felt. */
    void addTableChip(int color, float x, float y, float diameter, float bottom,
            float thickness, float rotation, boolean contactShadow) {
        float cx = x - width * .5f, cz = boardCenterY - y;
        float extent = diameter * .5f * (contactShadow ? 1.08f : 1f);
        cardBoundsMin.set(Math.min(cardBoundsMin.x, cx - extent),
                Math.min(cardBoundsMin.y, bottom), Math.min(cardBoundsMin.z, cz - extent));
        cardBoundsMax.set(Math.max(cardBoundsMax.x, cx + extent),
                Math.max(cardBoundsMax.y, bottom + .04f + thickness), Math.max(cardBoundsMax.z, cz + extent));
        appendChip(chipVertices[color], cx, cz, diameter, bottom, thickness, rotation, contactShadow, false);
    }

    static FloatArray flyingChipTemplate() {
        var vertices = new FloatArray();
        appendChip(vertices, 0f, 0f, 1f, -.54f, 1f, 0f, false, true);
        // Encode the cap and the inset bevel ring separately so the shader can
        // reproduce min(thickness * .20, diameter * .015) at any chip scale.
        for (int i = 1; i < vertices.size; i += 9) {
            float y = vertices.items[i];
            vertices.items[i] = Math.copySign(Math.abs(y) > .49f ? 1f : 2f, y);
        }
        return vertices;
    }

    private static void appendChip(FloatArray vertices, float cx, float cz, float diameter, float bottom,
            float thickness, float rotation, boolean contactShadow, boolean underside) {
        float visibility = 1f;
        float radius = diameter * .5f;
        float base = bottom + .04f;
        float extent = contactShadow ? radius * 1.08f : radius;
        float bevel = Math.min(thickness * .20f, diameter * .015f);
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            float a = i * MathUtils.PI2 / segments, b = (i + 1) * MathUtils.PI2 / segments;
            if (contactShadow) {
                float contact = bottom + .02f;
                potVertex(vertices, cx, cz, extent, contact, 0f, 0f, rotation, true, 1f, -1f);
                potVertex(vertices, cx, cz, extent, contact, 1f, a, rotation, true, 1f, -1f);
                potVertex(vertices, cx, cz, extent, contact, 1f, b, rotation, true, 1f, -1f);
            }
            if (underside) {
                potVertex(vertices, cx, cz, radius, base, 0f, 0f, rotation, true, -1f, visibility);
                potVertex(vertices, cx, cz, radius, base, .985f, b, rotation, true, -1f, visibility);
                potVertex(vertices, cx, cz, radius, base, .985f, a, rotation, true, -1f, visibility);
            }
            potVertex(vertices, cx, cz, radius, base + thickness, 0f, 0f, rotation, true, 1f, visibility);
            potVertex(vertices, cx, cz, radius, base + thickness, .985f, a, rotation, true, 1f, visibility);
            potVertex(vertices, cx, cz, radius, base + thickness, .985f, b, rotation, true, 1f, visibility);
            for (int band = 0; band < 3; band++) {
                float low = band == 0 ? 0f : band == 1 ? bevel : thickness - bevel;
                float high = band == 0 ? bevel : band == 1 ? thickness - bevel : thickness;
                float r0 = band == 0 ? .985f : 1f, r1 = band == 2 ? .985f : 1f;
                float ny = band == 0 ? -.6f : band == 2 ? .6f : 0f;
                potVertex(vertices,cx,cz,radius,base+low,r0,a,rotation,false,ny,visibility);
                potVertex(vertices,cx,cz,radius,base+high,r1,a,rotation,false,ny,visibility);
                potVertex(vertices,cx,cz,radius,base+high,r1,b,rotation,false,ny,visibility);
                potVertex(vertices,cx,cz,radius,base+low,r0,a,rotation,false,ny,visibility);
                potVertex(vertices,cx,cz,radius,base+high,r1,b,rotation,false,ny,visibility);
                potVertex(vertices,cx,cz,radius,base+low,r0,b,rotation,false,ny,visibility);
            }
        }
    }

    void addFlyingTableChip(int color, GdxTableChipFlight.Pose pose, float diameter, float thickness) {
        float half = thickness * .5f;
        pose.attitude(chipAttitude);
        chipAttitude.transform(chipNormal.set(Vector3.Y)).nor();
        float centerY = pose.centerHeight(diameter * .5f, half, chipNormal.y) + .04f;
        flyingChips.add(color, pose.x - width * .5f, centerY, boardCenterY - pose.y,
                diameter, thickness, chipAttitude, pose.rotation);
    }

    void addFlyingChipShadow(GdxTableChipFlight.Pose pose, float diameter,
            float progress, float landingHeight) {
        FloatArray vertices = chipVertices[0];
        float cx = pose.x - width * .5f, cz = boardCenterY - pose.y;
        float altitude = pose.height / diameter;
        float radius = diameter * (.54f + altitude * .18f);
        float settle = Interpolation.smooth.apply(MathUtils.clamp((progress - .80f) / .20f, 0f, 1f));
        // A base chip owns its contact shadow. A chip joining a pile gives
        // way to the shadow already cast by that pile's base.
        float shade = MathUtils.lerp(-.66f / (1f + altitude * .85f),
                landingHeight <= 0f ? -1f : 0f, settle);
        if (shade == 0f) return;
        cardBoundsMin.set(Math.min(cardBoundsMin.x, cx - radius), .02f,
                Math.min(cardBoundsMin.z, cz - radius));
        cardBoundsMax.set(Math.max(cardBoundsMax.x, cx + radius), .02f,
                Math.max(cardBoundsMax.z, cz + radius));
        for (int i = 0; i < 24; i++) {
            float a = MathUtils.PI2 * i / 24f, b = MathUtils.PI2 * (i + 1) / 24f;
            potVertex(vertices, cx, cz, radius, .02f, 0f, 0f, 0f, true, 1f, shade);
            potVertex(vertices, cx, cz, radius, .02f, 1f, a, 0f, true, 1f, shade);
            potVertex(vertices, cx, cz, radius, .02f, 1f, b, 0f, true, 1f, shade);
        }
    }

    private static void potVertex(FloatArray vertices, float cx, float cz, float radius,
            float elevation, float radial, float angle, float rotation, boolean top,
            float normalY, float visibility) {
        float x = MathUtils.cos(angle), z = MathUtils.sin(angle);
        float uvRadius = visibility < 0f ? .5f : top ? .453125f : .427f;
        float uvRadial = top ? radial : 1f;
        float normalRadial = top ? 0f : (float)Math.sqrt(1f - normalY * normalY);
        vertices.add(cx + radius * radial * x, elevation, cz + radius * radial * z,
                .5f + uvRadius * uvRadial * MathUtils.cos(angle + rotation));
        vertices.add(.5f + uvRadius * uvRadial * MathUtils.sin(angle + rotation), visibility,
                x * normalRadial, normalY);
        vertices.add(z * normalRadial);
    }

    void drawChipRack(Texture[] faces, float opacity, boolean airborne) {
        if (airborne ? flyingChips.isEmpty() : !Float.isFinite(cardBoundsMin.x)) return;
        begin(8, faces[0], faces[0], opacity);
        if (airborne) {
            shell.renderLayer(() -> {
                // The racks already own their colour below the HUD. Recover their
                // depth in this overlay so a flight can pass behind a nearer rim.
                shader.bind();
                shader.setUniformi("u_material", 9);
                renderRackDepth(potPiles);
                renderRackDepth(localPiles);
                flyingChips.draw(view, camera.position, faces, opacity);
            }, width, height);
            return;
        }
        renderChipGeometry(faces, view, width, height);
    }

    private void renderRackDepth(StandingChipPile[] piles) {
        for (StandingChipPile pile : piles) {
            if (pile != null && pile.drawnFrame == chipFrame) pile.mesh.render(shader, GL20.GL_TRIANGLES);
        }
    }

    private final Matrix4 chipCatalogView = new Matrix4();

    static float catalogFaceHeight(float diameter) {
        return diameter * MathUtils.sinDeg(ELEVATION);
    }

    static float catalogChipStep(float diameter) {
        return GdxTableChipRack.thickness(diameter) * MathUtils.cosDeg(ELEVATION);
    }

    /** The viewer has its own framing; emit the same physical chip mesh. */
    void addCatalogChip(int color, float centerX, float baseCenterY,
            float diameter, int level, float rotation) {
        float floorY = baseCenterY - catalogChipStep(diameter);
        float tableY = boardCenterY + floorY / MathUtils.sinDeg(ELEVATION);
        float thickness = GdxTableChipRack.thickness(diameter);
        addTableChip(color, centerX + width * .5f, tableY,
                diameter, level * thickness, thickness, rotation, level == 0);
    }

    void drawCatalogChips(Texture[] faces, float width, float height) {
        if (!Float.isFinite(cardBoundsMin.x)) return;
        prepareShader();
        begin(8, faces[0], faces[0], 1f);
        float depth = (width + height) * 2f;
        chipCatalogView.setToOrtho(0f, width, 0f, height, -depth, depth)
                .rotate(Vector3.X, ELEVATION);
        shader.setUniformMatrix("u_projection", chipCatalogView);
        shader.setUniformf("u_eye", width * .5f,
                MathUtils.sinDeg(ELEVATION) * depth, MathUtils.cosDeg(ELEVATION) * depth);
        renderChipGeometry(faces, chipCatalogView, width, height);
    }

    private void renderChipGeometry(Texture[] faces, Matrix4 projection, float width, float height) {
        beginCroppedLayer(chipShell, projection, width, height);
        try {
            chipShell.renderSolids(shader, chipVertices, faces, width, height);
        } finally {
            chipShell.endPair();
        }
    }

    private float markerCenterY, markerTilt, markerArtScale;

    static float positionMarkerThickness(float diameter) { return diameter * .095f; }

    void drawPositionMarker(Texture texture, float screenX, float screenY,
            float diameter, float hudSize, float rotation, float opacity, float onTable, float lift) {
        float level = 0f;
        for (int i=0; i<3 && onTable < 1f; i++) {
            float right = surfaceAt(screenX + hudSize * .5f, screenY, level).x;
            float left = surfaceAt(screenX - hudSize * .5f, screenY, level).x;
            float physical = MathUtils.lerp(right - left, diameter, onTable);
            level = lift + physical * .5f * MathUtils.sinDeg((90f - ELEVATION) * (1f-onTable))
                    + positionMarkerThickness(physical) * .5f * MathUtils.cosDeg((90f - ELEVATION) * (1f-onTable));
            if (i == 2) diameter = physical;
        }
        markerArtScale = MathUtils.lerp(.5f, .48f, onTable);
        float radius = diameter * 0.5f;
        float thickness = positionMarkerThickness(diameter);
        markerTilt = (90f - ELEVATION) * (1f - onTable) * MathUtils.degreesToRadians;
        float elevation = lift + radius * Math.abs(MathUtils.sin(markerTilt));
        markerCenterY = elevation + thickness * 0.5f * Math.abs(MathUtils.cos(markerTilt));
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
            float w, float h, float fan, float lift, float progress, float bend, float shadow) {
        if (shadow > 0f) drawContact(back, cx, cz, w, h, fan, shadow);
        meshColumns = 1;
        meshRows = CARD_STRIPS;
        cardWorldWidth = w;
        cardGrid.clear();
        for (int row = 0; row <= meshRows; row++) {
            float v = row / (float) meshRows;
            putFlight(cx, cz, w, h, fan, lift, progress, bend, 0f, v);
            putFlight(cx, cz, w, h, fan, lift, progress, bend, 1f, v);
        }
        triangulateCardGrid();
        renderPocket(4, back, back, 1f, h / w);
    }

    private void putFlight(float cx, float cz, float w, float h, float fan,
            float lift, float progress, float bend, float u, float v) {
        GdxPlasticCard.flight(cardSample, w * EDGE_MARGIN_SCALE,
                h * EDGE_MARGIN_SCALE, u, v, progress, bend);
        float cosine = MathUtils.cosDeg(fan);
        float sine = MathUtils.sinDeg(fan);
        put(cardGrid, cx + cardSample.x * cosine - cardSample.z * sine,
                CARD_REST_HEIGHT + lift + cardSample.y,
                cz + cardSample.x * sine + cardSample.z * cosine, u, v, 1f);
    }

    void beginBoardCardPass(boolean trackPixels) {
        boardCardPass = true;
        trackBoardCardPixels = trackPixels;
        boardCardPixels.set(0f, 0f, 0f, 0f);
    }

    void endBoardCardPass() {
        boardCardPass = false;
        trackBoardCardPixels = false;
    }

    Rectangle boardCardPixels() {
        return boardCardPixels;
    }

    void drawBoardCard(Texture back, Texture face, float x, float y, float w,
            float h, float fan, float opacity, boolean highlight) {
        drawBoardCard(back, face, x, y, w, h, fan, opacity, highlight, 0f);
    }

    void drawBoardCard(Texture back, Texture face, float x, float y, float w,
            float h, float fan, float opacity, boolean highlight, float elevation) {
        fillPocket(x - width * 0.5f, boardCenterY - y, w * EDGE_MARGIN_SCALE,
                h * EDGE_MARGIN_SCALE, fan, 0f, false);
        if (elevation > 0f) {
            for (int vertexY = 1; vertexY < cardVertices.size; vertexY += 6) {
                cardVertices.items[vertexY] += elevation;
            }
        }
        renderPocket(2, face, back, opacity, h / w, showdownTintAmount(highlight));
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
            float turn, float direction, GdxPlasticCard.Motion motion, float layer, int slot, DealRoute route) {
        if (flying) {
            dealTarget.set(x - width * 0.5f, layer, boardCenterY - y);
            drawDeal(back, sourceX, sourceY, w, h, 0f, flightProgress, 0, 0f, route, true);
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
            float w, float h, float fan, float progress, float direction, float opacity,
            GdxPlasticCard.Motion motion) {
        float p = MathUtils.clamp(progress, 0f, 1f);
        communityTurn.set(w, h, p, direction, motion);
        float flex = MathUtils.sin(MathUtils.PI * p);
        float projectedWidth = MathUtils.lerp(w, w * 0.20f, flex);
        drawContact(back, cx, cz, projectedWidth, h, fan, (1f - flex * 0.42f) * opacity);
        meshColumns = REVEAL_COLUMNS;
        meshRows = REVEAL_ROWS;
        cardWorldWidth = w;
        float cosine = MathUtils.cosDeg(fan);
        float sine = MathUtils.sinDeg(fan);
        cardGrid.clear();
        for (int row = 0; row <= REVEAL_ROWS; row++) {
            float v = row / (float) REVEAL_ROWS;
            for (int column = 0; column <= REVEAL_COLUMNS; column++) {
                float u = column / (float) REVEAL_COLUMNS;
                putReveal(cx, cz, u, v, cosine, sine);
            }
        }
        triangulateCardGrid();
        renderPocket(4, back, face, opacity, h / w);
    }

    private void triangulateCardGrid() {
        // Evaluate each animated point once and keep the original winding
        // and diagonal used by the shell's surface and rim interpolation.
        cardVertices.clear();
        int stride = (meshColumns + 1) * 6;
        for (int row = 0; row < meshRows; row++) {
            for (int column = 0; column < meshColumns; column++) {
                int a = row * stride + column * 6;
                int b = a + stride;
                cardVertices.addAll(cardGrid.items, a, 6);
                cardVertices.addAll(cardGrid.items, b, 6);
                cardVertices.addAll(cardGrid.items, a + 6, 6);
                cardVertices.addAll(cardGrid.items, a + 6, 6);
                cardVertices.addAll(cardGrid.items, b, 6);
                cardVertices.addAll(cardGrid.items, b + 6, 6);
            }
        }
    }

    private void putReveal(float cx, float cz, float u, float v,
            float cosine, float sine) {
        float artU = (u - 0.5f) * EDGE_MARGIN_SCALE + 0.5f;
        float artV = (v - 0.5f) * EDGE_MARGIN_SCALE + 0.5f;
        communityTurn.sample(cardSample, artU, artV);
        put(cardGrid, cx + cardSample.x * cosine - cardSample.z * sine,
                CARD_REST_HEIGHT + revealBaseHeight + cardSample.y,
                cz + cardSample.x * sine + cardSample.z * cosine, u, v, 1f);
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
        if (material == 2 || material == 4) {
            GdxCardPickMesh pick = activePocketSlot >= 0 ? pocketPicks[activePocketSlot] : null;
            if (pocketPair) {
                PocketDraw draw = pocketDraws[pocketDrawCount++];
                draw.vertices.clear();
                draw.vertices.addAll(cardVertices);
                draw.columns = meshColumns;
                draw.rows = meshRows;
                draw.material = material;
                draw.top = top;
                draw.bottom = bottom;
                draw.cardWidth = cardWorldWidth;
                draw.aspect = aspect;
                draw.opacity = opacity;
                draw.highlight = highlight;
                draw.pickTarget = pick;
            } else if (boardCardPass) {
                // Resolve each card in paint order so a translucent or
                // airborne card still composites over the cards below it.
                resetCardBounds();
                includeCardBounds(cardVertices);
                beginCroppedCardLayer(boardShell);
                try {
                    drawShell(boardShell, cardVertices, meshColumns, meshRows,
                            material, top, bottom, cardWorldWidth, aspect, opacity,
                            highlight, null);
                } finally {
                    boardShell.endPair();
                }
            } else {
                drawShell(shell, cardVertices,
                        meshColumns, meshRows, material, top, bottom,
                        cardWorldWidth, aspect, opacity, highlight, pick);
            }
            return;
        }
        if (card == null) {
            card = new Mesh(false, MAX_CARD_VERTICES, 0,
                    new VertexAttribute(Usage.Position, 3, "a_position"),
                    new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                    new VertexAttribute(Usage.Generic, 1, "a_light"));
        }
        card.setVertices(cardVertices.items, 0, cardVertices.size);
        if (trackBoardCardPixels) {
            // Contact shadows paint directly into the SMAA capture.
            resetCardBounds();
            includeCardBounds(cardVertices);
            beginCroppedCardLayer(null);
        }
        begin(material, top, bottom, opacity);
        shader.setUniformf("u_aspect", aspect);
        card.render(shader, GL20.GL_TRIANGLES);
    }

    private void drawShell(GdxCardShell target, FloatArray vertices, int columns, int rows,
            int material, Texture top, Texture bottom, float cardWidth, float aspect,
            float opacity, float highlight, GdxCardPickMesh pickTarget) {
        begin(material, top, bottom, opacity);
        shader.setUniformf("u_aspect", aspect);
        shader.setUniformf("u_showdownTint", highlight);
        target.pickTarget = pickTarget;
        try {
            target.render(shader, vertices, columns, rows, material, top, bottom,
                    cardWidth, aspect, width, height);
        } finally {
            target.pickTarget = null;
        }
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
        shader.setUniformf("u_chipPreviewTint", 0f, 0f, 0f, 0f);
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
        pocketShell.dispose();
        boardShell.dispose();
        chipShell.dispose();
        flyingChips.dispose();
        for (StandingChipPile pile : potPiles) if (pile != null && pile.mesh != null) pile.mesh.dispose();
        for (StandingChipPile pile : localPiles) if (pile != null && pile.mesh != null) pile.mesh.dispose();
    }
}
