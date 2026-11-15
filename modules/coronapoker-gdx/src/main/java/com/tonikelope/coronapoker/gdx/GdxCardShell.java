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
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL30C;

/** Thin two-sided plastic shell, resolved at physical resolution with depth. */
final class GdxCardShell implements Disposable {
    private static final int CORNER_STEPS = 12;
    private final IntBuffer parent = BufferUtils.newIntBuffer(1);
    private final IntBuffer viewport = BufferUtils.newIntBuffer(4);
    private final Matrix4 projection = new Matrix4();
    private final FloatArray vertices = new FloatArray();
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

    void render(ShaderProgram shader, FloatArray source, int cols, int strips,
            int material, Texture top, Texture bottom, float cardWidth, float aspect, float worldWidth, float worldHeight) {
        columns = cols;
        rows = strips;
        halfThickness = GdxPlasticCard.thickness(cardWidth) * 0.5f;
        captureSurface(source);
        GL20 gl = Gdx.gl;
        gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, parent);
        gl.glGetIntegerv(GL20.GL_VIEWPORT, viewport);
        try {
            int w = Gdx.graphics.getBackBufferWidth();
            int h = Gdx.graphics.getBackBufferHeight();
            if (layer == null || layer.getWidth() != w || layer.getHeight() != h) {
                if (layer != null) layer.dispose();
                createMultisample(w, h);
                layer = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, multisampleTarget == 0);
                layer.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest,
                        Texture.TextureFilter.Nearest);
            }
            layer.begin();
            previousMultisample = GL11C.glIsEnabled(GL13C.GL_MULTISAMPLE);
            if (multisampleTarget != 0) {
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, multisampleTarget);
                GL11C.glEnable(GL13C.GL_MULTISAMPLE);
            }
            bottom.bind(1);
            top.bind(0);
            shader.bind();
            gl.glDepthMask(true);
            gl.glClearColor(0f, 0f, 0f, 0f);
            gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
            gl.glEnable(GL20.GL_DEPTH_TEST);
            gl.glDepthFunc(GL20.GL_LEQUAL);
            gl.glEnable(GL20.GL_CULL_FACE);
            gl.glCullFace(GL20.GL_BACK);
            for (int side = 0; side < 2; side++) {
                vertices.clear();
                for (int vertex = 0; vertex < source.size / 6; vertex++) {
                    int at = vertex;
                    if (side == 1) at += vertex % 3 == 1 ? 1 : vertex % 3 == 2 ? -1 : 0;
                    int offset = at * 6;
                    float u = source.items[offset + 3];
                    float v = source.items[offset + 4];
                    int cell = (Math.round(v * rows) * (columns + 1) + Math.round(u * columns)) * 3;
                    float sign = side == 0 ? 1f : -1f;
                    vertices.add(source.items[offset] + normals[cell] * halfThickness * sign,
                            source.items[offset + 1] - halfThickness + normals[cell + 1] * halfThickness * sign,
                            source.items[offset + 2] + normals[cell + 2] * halfThickness * sign, u);
                    vertices.add(v, source.items[offset + 5]);
                }
                shader.setUniformi("u_cardSide", side);
                draw(shader);
            }
            gl.glDisable(GL20.GL_CULL_FACE);
            vertices.clear();
            for (int edge = 0; edge < 4 * CORNER_STEPS; edge++) {
                rimVertex(edge, 1f, aspect);
                rimVertex(edge, -1f, aspect);
                rimVertex(edge + 1, 1f, aspect);
                rimVertex(edge + 1, 1f, aspect);
                rimVertex(edge, -1f, aspect);
                rimVertex(edge + 1, -1f, aspect);
            }
            shader.setUniformi("u_material", 5);
            draw(shader);
            shader.setUniformi("u_material", material);
            shader.setUniformi("u_cardSide", 0);
            if (multisampleTarget != 0) {
                GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, multisampleTarget);
                GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, layer.getFramebufferHandle());
                GL30C.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL20.GL_COLOR_BUFFER_BIT, GL20.GL_NEAREST);
            }
        } finally {
            if (!previousMultisample) GL11C.glDisable(GL13C.GL_MULTISAMPLE);
            gl.glDisable(GL20.GL_DEPTH_TEST);
            gl.glDisable(GL20.GL_CULL_FACE);
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, parent.get(0));
            gl.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
        }
        if (composite == null) {
            composite = new SpriteBatch();
            composite.setBlendFunctionSeparate(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA,
                    GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        composite.setProjectionMatrix(projection.setToOrtho2D(0f, 0f, worldWidth, worldHeight));
        composite.begin();
        composite.draw(layer.getColorBufferTexture(), 0f, 0f, worldWidth, worldHeight,
                0f, 0f, 1f, 1f);
        composite.end();
    }

    private void createMultisample(int width, int height) {
        disposeMultisample();
        if (!GL.getCapabilities().OpenGL30) return;
        int samples = Math.min(4, GL11C.glGetInteger(GL30C.GL_MAX_SAMPLES));
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
            Gdx.app.log("3D cards", "MSAA unavailable, retaining analytical edge antialiasing");
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

    private void rimVertex(int edge, float side, float aspect) {
        int corner = edge / CORNER_STEPS % 4;
        double angle = -Math.PI * 0.5 + (corner + edge % CORNER_STEPS / (double) CORNER_STEPS) * Math.PI * 0.5;
        float radius = 0.075f;
        float radiusV = radius / aspect;
        float u = (corner < 2 ? 1f - radius : radius) + radius * (float) Math.cos(angle);
        float v = (corner == 0 || corner == 3 ? radiusV : 1f - radiusV) + radiusV * (float) Math.sin(angle);
        // The surface includes a small transparent border for analytical AA.
        u = (u - 0.5f) / 1.065f + 0.5f;
        v = (v - 0.5f) / 1.065f + 0.5f;
        interpolate(point, grid, u, v);
        interpolate(normal, normals, u, v);
        normal.nor();
        vertices.add(point.x + side * halfThickness * normal.x,
                point.y - halfThickness + side * halfThickness * normal.y,
                point.z + side * halfThickness * normal.z, u);
        vertices.add(v, 0.88f + 0.12f * Math.abs(normal.y));
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
        out.mulAdd(pointAt(tangent, values, a), (1f - fx) * (1f - fy));
        out.mulAdd(pointAt(tangent, values, a + 3), fx * (1f - fy));
        out.mulAdd(pointAt(tangent, values, b), (1f - fx) * fy);
        out.mulAdd(pointAt(tangent, values, b + 3), fx * fy);
    }

    private static Vector3 pointAt(Vector3 out, float[] values, int at) {
        return out.set(values[at], values[at + 1], values[at + 2]);
    }

    private void draw(ShaderProgram shader) {
        int count = vertices.size / 6;
        if (mesh == null || capacity < count) {
            if (mesh != null) mesh.dispose();
            capacity = Math.max(count, 4096);
            mesh = new Mesh(false, capacity, 0,
                    new VertexAttribute(Usage.Position, 3, "a_position"),
                    new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                    new VertexAttribute(Usage.Generic, 1, "a_light"));
        }
        mesh.setVertices(vertices.items, 0, vertices.size);
        mesh.render(shader, GL20.GL_TRIANGLES);
    }

    @Override public void dispose() {
        disposeMultisample();
        if (layer != null) layer.dispose();
        if (mesh != null) mesh.dispose();
        if (composite != null) composite.dispose();
    }
}
