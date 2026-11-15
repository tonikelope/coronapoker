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
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import java.nio.IntBuffer;

/** Table geometry in world space. The existing HUD keeps its own camera. */
final class GdxPerspectiveTable implements Disposable {

    private static final float FOV = 40f;
    private static final float ELEVATION = 39f;
    private static final float CARD_REST_HEIGHT = 0.85f;
    private static final int RAIL_STEPS = 36;
    private static final int CARD_STRIPS = 28;
    private static final int CARD_COLUMNS = 10;
    private static final float BEND_X = -MathUtils.sinDeg(9f);
    private static final float BEND_Z = MathUtils.cosDeg(9f);
    private static final String VERTEX = """
            attribute vec3 a_position;
            attribute vec2 a_texCoord0;
            attribute float a_light;
            uniform mat4 u_projection;
            varying vec2 v_uv;
            varying float v_light;
            void main() {
                v_uv = a_texCoord0;
                v_light = a_light;
                gl_Position = u_projection * vec4(a_position, 1.0);
            }
            """;
    private static final String FRAGMENT = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            uniform sampler2D u_texture;
            uniform sampler2D u_reverse;
            uniform int u_material;
            uniform vec2 u_repeat;
            uniform float u_opacity;
            varying vec2 v_uv;
            varying float v_light;
            void main() {
                if (u_material == 1) {
                    vec2 uv = v_uv * vec2(1830.0, 220.0);
                    float grain = sin(uv.x + sin(uv.y * 1.6)) * cos(uv.y);
                    float seam = 1.0 - smoothstep(0.004, 0.015, abs(v_uv.y - 0.52));
                    float stitches = smoothstep(0.15, 0.65, sin(v_uv.x * 2680.0));
                    vec3 leather = vec3(0.105, 0.083, 0.073);
                    vec3 colour = leather * (v_light + 0.07 * grain);
                    colour += vec3(0.21, 0.15, 0.11) * seam * stitches * v_light;
                    gl_FragColor = vec4(colour, 1.0);
                    return;
                }
                if (u_material == 3) {
                    vec2 edge = min(v_uv, 1.0 - v_uv);
                    float alpha = smoothstep(0.0, 0.10, min(edge.x, edge.y));
                    gl_FragColor = vec4(0.018, 0.012, 0.010, alpha * u_opacity * v_light);
                    return;
                }
                if (u_material == 2 || u_material == 4) {
                    vec2 corner = max(abs(v_uv - 0.5) - vec2(0.457, 0.469), 0.0);
                    float radius = length(corner * vec2(1.0, 1.36));
                    float coverage = 1.0 - smoothstep(0.038, 0.044, radius);
                    vec2 reverseUv = u_material == 2
                            ? vec2(v_uv.x, 1.0 - v_uv.y)
                            : vec2(1.0 - v_uv.x, v_uv.y);
                    vec4 ink = gl_FrontFacing ? texture2D(u_texture, v_uv)
                            : texture2D(u_reverse, reverseUv);
                    float light = gl_FrontFacing ? v_light : 0.94;
                    gl_FragColor = vec4(ink.rgb * light, ink.a * coverage * u_opacity);
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
    private final FloatArray cardVertices = new FloatArray(CARD_COLUMNS * CARD_STRIPS * 36);
    private final IntBuffer framebufferBinding = BufferUtils.newIntBuffer(1);
    private final IntBuffer viewportBinding = BufferUtils.newIntBuffer(4);
    private ShaderProgram shader;
    private Mesh surface;
    private Mesh rail;
    private Mesh card;
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
    private float cameraProgress = Float.NaN;

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
        camera.near = Math.max(10f, distance - height * 4.5f);
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

    void updatePeek(boolean held, float delta) {
        float travel = Math.min(0.05f, delta) * (held ? 5.4f : 7.5f);
        peek = held ? Math.min(1f, peek + travel) : Math.max(0f, peek - travel);
    }

    void coverCards() {
        peek = 0f;
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

    void drawPocket(Texture back, Texture face, float x, float y,
            float cardWidth, float cardHeight, float angle, boolean publicFace,
            float viewProgress, float opacity) {
        Vector3 center = surfaceAt(x, y, 0f);
        float cx = center.x;
        float cz = center.z;
        // Physical dimensions are the same as the board. The near card
        // gains apparent size from the camera, without a second projection.
        float curl = publicFace || face == back ? 0f : peek * peek * (3f - 2f * peek);
        fillPocket(cx, cz, cardWidth + 6f, cardHeight + 7f, angle, curl, true);
        renderPocket(3, back, face, 0.28f * opacity);
        if (!publicFace && face != back && viewProgress < 1f) {
            drawFlip(back, face, cx, cz, cardWidth, cardHeight, angle,
                    (1f - viewProgress) * MathUtils.PI, opacity);
            return;
        }
        fillPocket(cx, cz, cardWidth, cardHeight, angle, curl, false);
        renderPocket(2, publicFace ? face : back, face, opacity);
    }

    void drawBoardFlip(Texture back, Texture face, float x, float y,
            float w, float h, float angle) {
        drawFlip(back, face, x - width * 0.5f, boardCenterY - y,
                w, h, 0f, angle, 1f);
    }

    private void drawFlip(Texture back, Texture face, float cx, float cz,
            float w, float h, float fan, float angle, float opacity) {
        float sine = MathUtils.sin(angle);
        float cosine = MathUtils.cos(angle);
        float fanCosine = MathUtils.cosDeg(fan);
        float fanSine = MathUtils.sinDeg(fan);
        float middle = CARD_REST_HEIGHT + Math.abs(sine) * w * 0.5f;
        cardVertices.clear();
        int corners = 6;
        for (int i = 0; i < corners; i++) {
            int corner = switch (i) {
                case 0 -> 0;
                case 1, 4 -> 2;
                case 2, 3 -> 1;
                default -> 3;
            };
            float u = corner % 2;
            float v = corner / 2;
            float dx = (u - 0.5f) * w;
            float dz = (v - 0.5f) * h;
            put(cardVertices, cx + dx * cosine * fanCosine - dz * fanSine,
                    middle - dx * sine, cz + dx * cosine * fanSine + dz * fanCosine,
                    u, v, 1f - 0.13f * Math.abs(sine));
        }
        renderPocket(4, back, face, opacity);
    }

    private void fillPocket(float x, float z, float w, float h, float angle,
            float curl, boolean shadow) {
        cardVertices.clear();
        float hinge = -0.15f * h * BEND_Z;
        float length = (h * BEND_Z - w * BEND_X) * 0.5f - hinge;
        float radius = curl > 0.001f ? length / (curl * 2.26f) : 0f;
        PocketSurface pocket = new PocketSurface(x, z, w, h,
                MathUtils.cosDeg(angle), MathUtils.sinDeg(angle), hinge, radius, shadow);
        for (int row = 0; row < CARD_STRIPS; row++) {
            float from = row / (float) CARD_STRIPS;
            float to = (row + 1f) / CARD_STRIPS;
            for (int column = 0; column < CARD_COLUMNS; column++) {
                float left = column / (float) CARD_COLUMNS;
                float right = (column + 1f) / CARD_COLUMNS;
                pocketVertex(pocket, left, from);
                pocketVertex(pocket, left, to);
                pocketVertex(pocket, right, from);
                pocketVertex(pocket, right, from);
                pocketVertex(pocket, left, to);
                pocketVertex(pocket, right, to);
            }
        }
    }

    private record PocketSurface(float x, float z, float width, float height,
            float cosine, float sine, float hinge, float radius, boolean shadow) {
    }

    private void pocketVertex(PocketSurface pocket, float u, float v) {
        float dx = (u - 0.5f) * pocket.width;
        float dz = (v - 0.5f) * pocket.height;
        float y = pocket.shadow ? 0.32f : CARD_REST_HEIGHT;
        float theta = 0f;
        if (pocket.radius > 0f) {
            // A diagonal cylindrical bend keeps the opposite corner down,
            // with the far portion of the same sheet still on the table.
            float arc = Math.max(0f, BEND_X * dx + BEND_Z * dz - pocket.hinge);
            theta = arc / pocket.radius;
            float lift = pocket.radius * (1f - MathUtils.cos(theta));
            if (pocket.shadow) {
                dz += lift * 0.08f;
            } else {
                float offset = pocket.radius * MathUtils.sin(theta) - arc;
                dx += offset * BEND_X;
                dz += offset * BEND_Z;
                y += lift;
            }
        }
        float light = pocket.shadow ? 1f - 0.36f * theta : 1f - 0.14f * theta;
        put(cardVertices, pocket.x + pocket.cosine * dx - pocket.sine * dz, y,
                pocket.z + pocket.sine * dx + pocket.cosine * dz, u, v, light);
    }

    private void renderPocket(int material, Texture top, Texture bottom, float opacity) {
        if (card == null) {
            card = new Mesh(false, CARD_COLUMNS * CARD_STRIPS * 6, 0,
                    new VertexAttribute(Usage.Position, 3, "a_position"),
                    new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                    new VertexAttribute(Usage.Generic, 1, "a_light"));
        }
        card.setVertices(cardVertices.items, 0, cardVertices.size);
        begin(material, top, bottom, opacity);
        card.render(shader, GL20.GL_TRIANGLES);
    }

    private void begin(int material, Texture top, Texture bottom, float opacity) {
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        bottom.bind(1);
        top.bind(0);
        shader.bind();
        shader.setUniformMatrix("u_projection", view);
        shader.setUniformi("u_texture", 0);
        shader.setUniformi("u_reverse", 1);
        shader.setUniformi("u_material", material);
        shader.setUniformf("u_opacity", opacity);
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
    }
}
