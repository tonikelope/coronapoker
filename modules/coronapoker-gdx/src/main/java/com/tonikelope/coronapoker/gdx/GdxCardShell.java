/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import java.nio.IntBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL30C;

/** Thin two-sided plastic shell with depth and a premultiplied resolve. */
final class GdxCardShell implements Disposable {
    private record Grid(int columns, int rows, float aspect) { }
    private final Map<Grid, GdxCardContour> contours = new LinkedHashMap<>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Grid, GdxCardContour> eldest) {
            return size() > 12;
        }
    };
    private GdxCardContour contour;
    private final IntBuffer parent = BufferUtils.newIntBuffer(1);
    private final IntBuffer viewport = BufferUtils.newIntBuffer(4);
    private final Matrix4 projection = new Matrix4();
    private final FloatArray vertices = new FloatArray();
    private final FloatArray surfaceSamples = new FloatArray();
    private final Vector3 tangent = new Vector3();
    private final Vector3 normal = new Vector3();
    private final Vector3 point = new Vector3();
    private float[] grid = new float[0];
    private float[] normals = new float[0];
    private int columns;
    private int rows;
    private float halfThickness;
    private Mesh mesh;
    private int capacity;
    private FrameBuffer layer;
    private SpriteBatch composite;
    private int multisampleTarget;
    private int multisampleColour;
    private int multisampleDepth;
    private boolean previousMultisample;
    private boolean grouping;
    private boolean groupHasCards;
    private float groupWidth;
    private float groupHeight;
    private final int requestedSamples;
    private int nativeWidth;
    private int nativeHeight;
    private int regionX, regionY, regionWidth, regionHeight;
    private int resolutionScale = 1;
    GdxCardPickMesh pickTarget;

    GdxCardShell(int requestedSamples) {
        this.requestedSamples = Math.max(0, Math.min(8, requestedSamples));
    }

    void prepare(int cols, int strips, float aspect) {
        Grid key = new Grid(cols, strips, aspect);
        if (contours.containsKey(key)) return;
        GdxCardContour prepared = new GdxCardContour(cols, strips, aspect);
        contours.put(key, prepared);
        int length = (cols + 1) * (strips + 1) * 3;
        if (grid.length < length) {
            grid = new float[length]; normals = new float[length];
        }
        int count = Math.max(prepared.faces.size / 2, prepared.rim.size * 3);
        vertices.ensureCapacity(count * 9);
        ensureMesh(count);
    }

    void beginPair() {
        grouping = true;
        groupHasCards = false;
    }

    void beginPair(int x, int y, int width, int height) {
        regionX = x;
        regionY = y;
        regionWidth = width;
        regionHeight = height;
        beginPair();
    }

    void endPair() {
        grouping = false;
        flushPair();
    }

    void flushPair() {
        if (groupHasCards) composite(groupWidth, groupHeight);
        groupHasCards = false;
    }

    void render(ShaderProgram shader, FloatArray source, int cols, int strips,
            int material, Texture top, Texture bottom, float cardWidth, float aspect, float worldWidth, float worldHeight) {
        columns = cols;
        rows = strips;
        halfThickness = GdxPlasticCard.thickness(cardWidth) * 0.5f;
        captureSurface(source);
        contour = contours.computeIfAbsent(new Grid(cols, strips, aspect),
                key -> new GdxCardContour(key.columns(), key.rows(), key.aspect()));
        sampleSurface();
        renderLayer(() -> {
            GL20 gl = Gdx.gl;
            bottom.bind(1);
            top.bind(0);
            shader.bind();
            gl.glEnable(GL20.GL_CULL_FACE);
            gl.glCullFace(GL20.GL_BACK);
            // Resolve the geometry of each opacity group before blending it
            // with the table and any cards already drawn beneath it.
            if (pickTarget != null) pickTarget.clear();
            for (int side = 0; side < 2; side++) {
                vertices.clear();
                for (int vertex = 0; vertex < contour.faces.size / 2; vertex++) {
                    int at = vertex;
                    if (side == 1) at += vertex % 3 == 1 ? 1 : vertex % 3 == 2 ? -1 : 0;
                    appendSurfaceVertex(contour.faceSamples[at], side);
                }
                shader.setUniformi("u_cardSide", side);
                if (pickTarget != null) pickTarget.append(vertices);
                draw(shader);
            }
            gl.glDisable(GL20.GL_CULL_FACE);
            vertices.clear();
            for (int edge = 0; edge < contour.rim.size / 2; edge++) {
                rimVertex(edge, 0);
                rimVertex(edge, 1);
                rimVertex(edge + 1, 0);
                rimVertex(edge + 1, 0);
                rimVertex(edge, 1);
                rimVertex(edge + 1, 1);
            }
            shader.setUniformi("u_material", 5);
            draw(shader);
            shader.setUniformi("u_material", material);
            shader.setUniformi("u_cardSide", 0);
        }, worldWidth, worldHeight);
    }

    void renderSolid(ShaderProgram shader, FloatArray geometry, float worldWidth, float worldHeight) {
        renderLayer(() -> {
            Gdx.gl.glDisable(GL20.GL_CULL_FACE);
            shader.bind();
            draw(shader, geometry);
        }, worldWidth, worldHeight);
    }

    /** One depth target and one MSAA resolve for the whole pot, not per chip. */
    void renderSolids(ShaderProgram shader, FloatArray[] geometry, Texture[] textures,
            float worldWidth, float worldHeight) {
        renderLayer(() -> drawSolids(shader, geometry, textures), worldWidth, worldHeight);
    }

    void drawSolids(ShaderProgram shader, FloatArray[] geometry, Texture[] textures) {
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        shader.bind();
        for (int i = 0; i < geometry.length; i++) {
            if (geometry[i].isEmpty()) continue;
            textures[i].bind(0);
            draw(shader, geometry[i]);
        }
    }

    void renderLayer(Runnable drawing, float worldWidth, float worldHeight) {
        GL20 gl = Gdx.gl;
        gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, parent);
        gl.glGetIntegerv(GL20.GL_VIEWPORT, viewport);
        try {
            int w = Gdx.graphics.getBackBufferWidth();
            int h = Gdx.graphics.getBackBufferHeight();
            ensureLayer(w, h);
            layer.begin();
            if (regionWidth > 0) {
                // Offset the original screen projection into the small target
                // while retaining the exact screen pixel grid.
                gl.glViewport(-regionX * resolutionScale, -regionY * resolutionScale,
                        w * resolutionScale, h * resolutionScale);
            }
            previousMultisample = GL11C.glIsEnabled(GL13C.GL_MULTISAMPLE);
            if (multisampleTarget != 0) {
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, multisampleTarget);
                GL11C.glEnable(GL13C.GL_MULTISAMPLE);
            }
            gl.glDepthMask(true);
            gl.glClearColor(0f, 0f, 0f, 0f);
            if (!grouping || !groupHasCards) {
                gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
            }
            gl.glEnable(GL20.GL_DEPTH_TEST);
            gl.glDepthFunc(GL20.GL_LEQUAL);
            gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ZERO, GL20.GL_ONE, GL20.GL_ZERO);
            drawing.run();
            if (multisampleTarget != 0) {
                GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, multisampleTarget);
                GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, layer.getFramebufferHandle());
                int rw = layer.getWidth(), rh = layer.getHeight();
                GL30C.glBlitFramebuffer(0, 0, rw, rh, 0, 0, rw, rh,
                        GL20.GL_COLOR_BUFFER_BIT, GL20.GL_NEAREST);
            }
        } finally {
            if (!previousMultisample) GL11C.glDisable(GL13C.GL_MULTISAMPLE);
            gl.glDisable(GL20.GL_DEPTH_TEST);
            gl.glDisable(GL20.GL_CULL_FACE);
            gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
                    GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, parent.get(0));
            gl.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
        }
        if (grouping) {
            groupWidth = worldWidth;
            groupHeight = worldHeight;
            groupHasCards = true;
        } else {
            composite(worldWidth, worldHeight);
        }
    }

    private void ensureLayer(int w, int h) {
        boolean cropped = regionWidth > 0;
        int neededWidth = cropped ? regionWidth : w;
        int neededHeight = cropped ? regionHeight : h;
        if (layer != null && nativeWidth == w && nativeHeight == h
                && neededWidth * resolutionScale <= layer.getWidth()
                && neededHeight * resolutionScale <= layer.getHeight()) return;

        int tw = neededWidth, th = neededHeight;
        if (cropped) {
            // Grow in blocks so bending or spreading a few pixels doesn't
            // recreate the buffers in the middle of an animation.
            tw = Math.min(w, (neededWidth + 127) / 128 * 128);
            th = Math.min(h, (neededHeight + 127) / 128 * 128);
            if (layer != null && nativeWidth == w && nativeHeight == h) {
                tw = Math.max(tw, layer.getWidth() / resolutionScale);
                th = Math.max(th, layer.getHeight() / resolutionScale);
            }
        }
        if (layer != null) layer.dispose();
        int maximum = GL11C.glGetInteger(GL20.GL_MAX_TEXTURE_SIZE);
        resolutionScale = 1;
        if (cropped && tw <= maximum / 2 && th <= maximum / 2) {
            var limits = BufferUtils.newIntBuffer(2);
            GL11C.glGetIntegerv(GL20.GL_MAX_VIEWPORT_DIMS, limits);
            if (w <= limits.get(0) / 2 && h <= limits.get(1) / 2) resolutionScale = 2;
        }
        createMultisample(tw * resolutionScale, th * resolutionScale);
        if (!cropped && requestedSamples > 0 && multisampleTarget == 0
                && w <= maximum / 2 && h <= maximum / 2) {
            resolutionScale = 2;
        }
        // 24-bit depth keeps the two thin sheets distinct even with MSAA off.
        var builder = new com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferBuilder(
                tw * resolutionScale, th * resolutionScale);
        builder.addBasicColorTextureAttachment(Pixmap.Format.RGBA8888);
        if (multisampleTarget == 0) builder.addDepthRenderBuffer(GL30C.GL_DEPTH_COMPONENT24);
        layer = builder.build();
        Texture.TextureFilter filter = resolutionScale > 1
                ? Texture.TextureFilter.Linear : Texture.TextureFilter.Nearest;
        layer.getColorBufferTexture().setFilter(filter, filter);
        nativeWidth = w;
        nativeHeight = h;
    }

    private void composite(float worldWidth, float worldHeight) {
        if (composite == null) {
            composite = new SpriteBatch();
            composite.setBlendFunctionSeparate(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA,
                    GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        composite.setProjectionMatrix(projection.setToOrtho2D(0f, 0f, worldWidth, worldHeight));
        composite.begin();
        if (regionWidth > 0) {
            // At 2x, linear sampling averages the four subpixels on the
            // original pixel grid. The resolve texture is premultiplied.
            composite.draw(layer.getColorBufferTexture(),
                    regionX * worldWidth / nativeWidth, regionY * worldHeight / nativeHeight,
                    regionWidth * worldWidth / nativeWidth, regionHeight * worldHeight / nativeHeight,
                    0f, 0f, regionWidth * resolutionScale / (float) layer.getWidth(),
                    regionHeight * resolutionScale / (float) layer.getHeight());
        } else {
            composite.draw(layer.getColorBufferTexture(), 0f, 0f, worldWidth, worldHeight,
                    0f, 0f, 1f, 1f);
        }
        composite.end();
        // The resolved layer uses premultiplied alpha. ShapeRenderer does not
        // set its own blend function, so return to the table's straight-alpha
        // contract before drawing translucent glass and button highlights.
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
                GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void createMultisample(int width, int height) {
        disposeMultisample();
        if (requestedSamples < 2 || !GL.getCapabilities().OpenGL30) return;
        int samples = Math.min(requestedSamples, GL11C.glGetInteger(GL30C.GL_MAX_SAMPLES));
        if (samples < 2) return;
        multisampleTarget = GL30C.glGenFramebuffers();
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, multisampleTarget);
        multisampleColour = GL30C.glGenRenderbuffers();
        GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, multisampleColour);
        GL30C.glRenderbufferStorageMultisample(GL30C.GL_RENDERBUFFER, samples,
                GL11C.GL_RGBA8, width, height);
        GL30C.glFramebufferRenderbuffer(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                GL30C.GL_RENDERBUFFER, multisampleColour);
        multisampleDepth = GL30C.glGenRenderbuffers();
        GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, multisampleDepth);
        GL30C.glRenderbufferStorageMultisample(GL30C.GL_RENDERBUFFER, samples,
                GL30C.GL_DEPTH_COMPONENT24, width, height);
        GL30C.glFramebufferRenderbuffer(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT,
                GL30C.GL_RENDERBUFFER, multisampleDepth);
        if (GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            disposeMultisample();
            Gdx.app.log("3D cards", "MSAA unavailable, using supersampled geometry");
        }
        GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, 0);
    }

    private void disposeMultisample() {
        if (multisampleTarget != 0) GL30C.glDeleteFramebuffers(multisampleTarget);
        if (multisampleColour != 0) GL30C.glDeleteRenderbuffers(multisampleColour);
        if (multisampleDepth != 0) GL30C.glDeleteRenderbuffers(multisampleDepth);
        multisampleTarget = multisampleColour = multisampleDepth = 0;
    }

    private void captureSurface(FloatArray source) {
        int length = (columns + 1) * (rows + 1) * 3;
        if (grid.length < length) {
            grid = new float[length];
            normals = new float[length];
        }
        for (int offset = 0; offset < source.size; offset += 6) {
            int cell = (Math.round(source.items[offset + 4] * rows) * (columns + 1)
                    + Math.round(source.items[offset + 3] * columns)) * 3;
            System.arraycopy(source.items, offset, grid, cell, 3);
        }
        for (int row = 0; row <= rows; row++) {
            for (int column = 0; column <= columns; column++) {
                int at = (row * (columns + 1) + column) * 3;
                int left = (row * (columns + 1) + Math.max(0, column - 1)) * 3;
                int right = (row * (columns + 1) + Math.min(columns, column + 1)) * 3;
                int far = (Math.max(0, row - 1) * (columns + 1) + column) * 3;
                int near = (Math.min(rows, row + 1) * (columns + 1) + column) * 3;
                tangent.set(grid[right] - grid[left], grid[right + 1] - grid[left + 1],
                        grid[right + 2] - grid[left + 2]);
                normal.set(grid[near] - grid[far], grid[near + 1] - grid[far + 1],
                        grid[near + 2] - grid[far + 2]).crs(tangent).nor();
                normals[at] = normal.x;
                normals[at + 1] = normal.y;
                normals[at + 2] = normal.z;
            }
        }
    }

    private void rimVertex(int edge, int side) {
        int index = contour.rimSamples[edge % contour.rimSamples.length];
        appendSurfaceVertex(index, side);
        vertices.items[vertices.size - 4] = .94f;
    }

    private void appendSurfaceVertex(int index, int side) {
        vertices.addAll(surfaceSamples.items, (2 * index + side) * 9, 9);
    }

    private void sampleSurface() {
        surfaceSamples.clear();
        surfaceSamples.ensureCapacity(contour.sampleUvs.size * 9);
        for (int i = 0; i < contour.sampleUvs.size; i += 2) {
            float u = contour.sampleUvs.items[i], v = contour.sampleUvs.items[i + 1];
            interpolate(point, grid, u, v);
            interpolate(normal, normals, u, v);
            sampleSide(u, v, 1f);
            sampleSide(u, v, -1f);
        }
    }

    private void sampleSide(float u, float v, float side) {
        // Do not renormalize the offset: the face triangles interpolate the
        // vertex offsets linearly. The rim must meet that very same surface.
        surfaceSamples.add(point.x + side * halfThickness * normal.x,
                point.y - halfThickness + side * halfThickness * normal.y,
                point.z + side * halfThickness * normal.z, u);
        surfaceSamples.add(v, 1f);
        surfaceSamples.add(normal.x * side, normal.y * side, normal.z * side);
    }

    private void interpolate(Vector3 out, float[] values, float u, float v) {
        float x = u * columns;
        float y = v * rows;
        int column = Math.min(columns - 1, (int) x);
        int row = Math.min(rows - 1, (int) y);
        float fx = x - column;
        float fy = y - row;
        int a = (row * (columns + 1) + column) * 3;
        int b = a + (columns + 1) * 3;
        out.set(0f, 0f, 0f);
        // This is the actual diagonal used by the grid triangles. Bilinear
        // interpolation would place the rim off the bent face between cells.
        if (fx + fy <= 1f) {
            out.mulAdd(pointAt(tangent, values, a), 1f - fx - fy);
            out.mulAdd(pointAt(tangent, values, a + 3), fx);
            out.mulAdd(pointAt(tangent, values, b), fy);
        } else {
            out.mulAdd(pointAt(tangent, values, a + 3), 1f - fy);
            out.mulAdd(pointAt(tangent, values, b), 1f - fx);
            out.mulAdd(pointAt(tangent, values, b + 3), fx + fy - 1f);
        }
    }

    private static Vector3 pointAt(Vector3 out, float[] values, int at) {
        return out.set(values[at], values[at + 1], values[at + 2]);
    }

    private void ensureMesh(int count) {
        if (mesh == null || capacity < count) {
            if (mesh != null) mesh.dispose();
            capacity = Math.max(count, Math.max(4096, capacity * 3 / 2));
            mesh = new Mesh(false, capacity, 0,
                    new VertexAttribute(Usage.Position, 3, "a_position"),
                    new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                    new VertexAttribute(Usage.Generic, 1, "a_light"),
                    new VertexAttribute(Usage.Normal, 3, "a_normal"));
        }
    }

    private void draw(ShaderProgram shader) {
        draw(shader, vertices);
    }

    private void draw(ShaderProgram shader, FloatArray geometry) {
        ensureMesh(geometry.size / 9);
        mesh.setVertices(geometry.items, 0, geometry.size);
        mesh.render(shader, GL20.GL_TRIANGLES);
    }

    static Mesh uploadSolid(Mesh mesh, FloatArray geometry) {
        int count = geometry.size / 9;
        if (mesh == null || count > mesh.getMaxVertices()) {
            int capacity = count;
            if (mesh != null) {
                capacity = Math.max(count, mesh.getMaxVertices() * 3 / 2);
                mesh.dispose();
            }
            mesh = new Mesh(Mesh.VertexDataType.VertexBufferObject, true, capacity, 0,
                    new VertexAttribute(Usage.Position, 3, "a_position"),
                    new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                    new VertexAttribute(Usage.Generic, 1, "a_light"),
                    new VertexAttribute(Usage.Normal, 3, "a_normal"));
        }
        mesh.setVertices(geometry.items, 0, geometry.size);
        return mesh;
    }

    @Override public void dispose() {
        disposeMultisample();
        if (layer != null) layer.dispose();
        if (mesh != null) mesh.dispose();
        if (composite != null) composite.dispose();
    }
}
