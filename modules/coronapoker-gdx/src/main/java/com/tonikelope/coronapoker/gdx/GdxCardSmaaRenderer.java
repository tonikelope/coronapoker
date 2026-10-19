/*
 * Copyright (C) 2020-2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import java.nio.IntBuffer;

/**
 * Applies genuine SMAA 1x exclusively to complete table-card layers.
 *
 * <p>The card layer is rasterized once at the physical backbuffer resolution,
 * after every fan rotation, perspective flip and Rabbit peel. The official
 * three-pass SMAA pipeline then detects colour edges, calculates area/search
 * weights and performs neighbourhood blending. Unlike the former supersample
 * experiment, this keeps the deck's original mip level and does not soften
 * the whole card through a box downsample.</p>
 *
 * <p>Every intermediate target stores premultiplied colour. This is essential
 * for transparent rounded corners and for fold/showdown fades: blending RGB
 * and alpha with the same weights prevents bright fringes, gamma shifts and
 * transient colour corruption.</p>
 */
final class GdxCardSmaaRenderer implements Disposable {

    /** Draw the cards directly, preserving the deck's natural texture. */
    static final int RENDERING_NATIVE = 0;
    /** Apply selective SMAA while preserving the native card detail. */
    static final int RENDERING_SMOOTH = 1;

    /**
     * Keeps most of the unprocessed artwork at opaque internal edges while
     * retaining enough of the SMAA resolve to remove visible stair steps.
     * Alpha-changing silhouette pixels still receive the complete resolve.
     */
    private static final float INTERIOR_EDGE_BLEND_STRENGTH = 0.72f;

    private static final String RESOURCE_ROOT = "smaa/";
    private static final String PRESET_ULTRA =
            "#define SMAA_PRESET_ULTRA\n";
    private static final float[] FULLSCREEN_TRIANGLE = {
        -1f, -1f,
        3f, -1f,
        -1f, 3f
    };

    private FrameBuffer colorLayer;
    private FrameBuffer edgesLayer;
    private FrameBuffer blendLayer;
    private ShaderProgram edgesShader;
    private ShaderProgram weightsShader;
    private ShaderProgram blendShader;
    private Texture areaTexture;
    private Texture searchTexture;
    private Mesh fullscreenTriangle;
    private boolean active;
    private boolean resourceInitializationFailed;
    private int activeRenderingMode;
    private int effectiveRenderingMode;
    private int lastAttemptedRenderingMode = -1;
    private int rejectedBackBufferWidth = -1;
    private int rejectedBackBufferHeight = -1;

    boolean begin(int backBufferWidth, int backBufferHeight,
            int requestedRenderingMode, SpriteBatch batch) {
        if (active) {
            throw new IllegalStateException("Card SMAA pass is already active");
        }
        lastAttemptedRenderingMode = requestedRenderingMode;
        if (requestedRenderingMode != RENDERING_SMOOTH) {
            disposeBuffers();
            effectiveRenderingMode = RENDERING_NATIVE;
            return false;
        }
        if (!ensureResources()
                || !ensureBuffers(backBufferWidth, backBufferHeight)) {
            effectiveRenderingMode = RENDERING_NATIVE;
            return false;
        }

        colorLayer.begin();
        active = true;
        activeRenderingMode = requestedRenderingMode;
        clearTarget(colorLayer);
        useStraightAlphaCapture(batch);
        return true;
    }

    void endAndComposite(SpriteBatch batch, Matrix4 projection,
            Runnable restoreParentTarget) {
        if (!active) {
            throw new IllegalStateException("Card SMAA pass is not active");
        }
        colorLayer.end();
        active = false;

        int width = colorLayer.getWidth();
        int height = colorLayer.getHeight();
        float texelX = 1f / width;
        float texelY = 1f / height;

        Gdx.gl.glDisable(GL20.GL_BLEND);
        edgesLayer.begin();
        clearTarget(edgesLayer);
        edgesShader.begin();
        colorLayer.getColorBufferTexture().bind(0);
        edgesShader.setUniformi("uColorTexture", 0);
        edgesShader.setUniformf("uTexelSize", texelX, texelY);
        fullscreenTriangle.render(edgesShader, GL20.GL_TRIANGLES);
        edgesShader.end();
        edgesLayer.end();

        blendLayer.begin();
        clearTarget(blendLayer);
        weightsShader.begin();
        edgesLayer.getColorBufferTexture().bind(0);
        areaTexture.bind(1);
        searchTexture.bind(2);
        weightsShader.setUniformi("uEdgesTexture", 0);
        weightsShader.setUniformi("uAreaTexture", 1);
        weightsShader.setUniformi("uSearchTexture", 2);
        weightsShader.setUniformf("uViewportSize", width, height);
        weightsShader.setUniformf("uTexelSize", texelX, texelY);
        weightsShader.setUniformf("uSubsampleIndices", 0f, 0f, 0f, 0f);
        fullscreenTriangle.render(weightsShader, GL20.GL_TRIANGLES);
        weightsShader.end();
        blendLayer.end();

        restoreParentTarget.run();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        usePremultipliedComposite(batch);
        blendShader.begin();
        colorLayer.getColorBufferTexture().bind(0);
        blendLayer.getColorBufferTexture().bind(1);
        blendShader.setUniformi("uColorTexture", 0);
        blendShader.setUniformi("uBlendTexture", 1);
        blendShader.setUniformf("uTexelSize", texelX, texelY);
        blendShader.setUniformf("uInteriorEdgeBlendStrength",
                INTERIOR_EDGE_BLEND_STRENGTH);
        fullscreenTriangle.render(blendShader, GL20.GL_TRIANGLES);
        blendShader.end();

        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        batch.setProjectionMatrix(projection);
        batch.setShader(null);
        batch.setColor(Color.WHITE);
        useStandardAlpha(batch);
        // The setting is reported as effective only after all three GPU passes
        // have completed and the result is in the actual parent target.
        effectiveRenderingMode = activeRenderingMode;
        activeRenderingMode = RENDERING_NATIVE;
    }

    /** Rebinds this card target after an inner fold/showdown framebuffer. */
    void resumeAfterNestedPass() {
        if (!active || colorLayer == null) {
            throw new IllegalStateException("Card SMAA layer is not active");
        }
        colorLayer.bind();
        Gdx.gl.glViewport(0, 0, colorLayer.getWidth(),
                colorLayer.getHeight());
    }

    boolean isActive() {
        return active;
    }

    boolean isUnavailable(int requestedRenderingMode) {
        return requestedRenderingMode == RENDERING_SMOOTH
                && lastAttemptedRenderingMode == requestedRenderingMode
                && effectiveRenderingMode != requestedRenderingMode;
    }

    int activeWidth() {
        return active ? colorLayer.getWidth() : 0;
    }

    int activeHeight() {
        return active ? colorLayer.getHeight() : 0;
    }

    static float interiorEdgeBlendStrength() {
        return INTERIOR_EDGE_BLEND_STRENGTH;
    }

    static boolean fitsMaximumTextureSize(int width, int height,
            int maximumTextureSize) {
        return width > 0 && height > 0 && maximumTextureSize > 0
                && width <= maximumTextureSize
                && height <= maximumTextureSize;
    }

    static void useStraightAlphaCapture(SpriteBatch batch) {
        // RGB is stored premultiplied while alpha follows source-over.
        applyBlendFunction(batch, GL20.GL_SRC_ALPHA,
                GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE,
                GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    static void usePremultipliedComposite(SpriteBatch batch) {
        applyBlendFunction(batch, GL20.GL_ONE,
                GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE,
                GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    static void useStandardAlpha(SpriteBatch batch) {
        applyBlendFunction(batch, GL20.GL_SRC_ALPHA,
                GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_SRC_ALPHA,
                GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void applyBlendFunction(SpriteBatch batch,
            int sourceColor, int destinationColor,
            int sourceAlpha, int destinationAlpha) {
        batch.setBlendFunctionSeparate(sourceColor, destinationColor,
                sourceAlpha, destinationAlpha);
        // SpriteBatch keeps the requested factors but does not update OpenGL
        // while idle; ShapeRenderer and the HUD share this state.
        if (Gdx.gl != null) {
            Gdx.gl.glBlendFuncSeparate(sourceColor, destinationColor,
                    sourceAlpha, destinationAlpha);
        }
    }

    private boolean ensureResources() {
        if (edgesShader != null && weightsShader != null
                && blendShader != null && areaTexture != null
                && searchTexture != null && fullscreenTriangle != null) {
            return true;
        }
        if (resourceInitializationFailed) return false;
        try {
            String presets = source("presets.glsl");
            edgesShader = compile("edges", source("smaa-edges.vert"),
                    PRESET_ULTRA + "#define SMAA_EDGES_COLOR\n"
                    + presets + source("smaa-edges.frag"));
            weightsShader = compile("weights",
                    PRESET_ULTRA + source("smaa-weights.vert"),
                    PRESET_ULTRA + presets + source("smaa-weights.frag"));
            blendShader = compile("blend", source("smaa-blend.vert"),
                    source("smaa-blend.frag"));

            areaTexture = lookupTexture("area.png");
            searchTexture = lookupTexture("search.png");
            fullscreenTriangle = new Mesh(true, 3, 0,
                    new VertexAttribute(VertexAttributes.Usage.Position,
                            2, "aPosition"));
            fullscreenTriangle.setVertices(FULLSCREEN_TRIANGLE);
            return true;
        } catch (RuntimeException failure) {
            resourceInitializationFailed = true;
            if (Gdx.app != null) {
                Gdx.app.error("CardSMAA",
                        "GPU card SMAA disabled: " + failure.getMessage(),
                        failure);
            }
            disposeResources();
            return false;
        }
    }

    private boolean ensureBuffers(int width, int height) {
        if (colorLayer != null && colorLayer.getWidth() == width
                && colorLayer.getHeight() == height) {
            return true;
        }
        if (width == rejectedBackBufferWidth
                && height == rejectedBackBufferHeight) {
            return false;
        }
        int maximumTextureSize = maximumTextureSize();
        if (!fitsMaximumTextureSize(width, height, maximumTextureSize)) {
            reject(width, height, "GL_MAX_TEXTURE_SIZE is "
                    + maximumTextureSize);
            return false;
        }

        disposeBuffers();
        try {
            colorLayer = renderTarget(width, height);
            edgesLayer = renderTarget(width, height);
            blendLayer = renderTarget(width, height);
            rejectedBackBufferWidth = -1;
            rejectedBackBufferHeight = -1;
            return true;
        } catch (RuntimeException allocationFailure) {
            disposeBuffers();
            reject(width, height,
                    allocationFailure.getClass().getSimpleName());
            return false;
        }
    }

    private static FrameBuffer renderTarget(int width, int height) {
        FrameBuffer target = new FrameBuffer(Pixmap.Format.RGBA8888,
                width, height, false);
        Texture texture = target.getColorBufferTexture();
        texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        texture.setWrap(TextureWrap.ClampToEdge, TextureWrap.ClampToEdge);
        return target;
    }

    private static Texture lookupTexture(String name) {
        Texture texture = new Texture(Gdx.files.internal(
                RESOURCE_ROOT + name), false);
        texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        texture.setWrap(TextureWrap.ClampToEdge, TextureWrap.ClampToEdge);
        return texture;
    }

    private static ShaderProgram compile(String pass, String vertex,
            String fragment) {
        ShaderProgram shader = new ShaderProgram(vertex, fragment);
        if (shader.isCompiled()) return shader;
        String log = shader.getLog();
        shader.dispose();
        throw new IllegalStateException("SMAA " + pass
                + " shader compilation failed: " + log);
    }

    private static String source(String name) {
        return Gdx.files.internal(RESOURCE_ROOT + name)
                .readString("UTF-8") + "\n";
    }

    private static void clearTarget(FrameBuffer target) {
        Gdx.gl.glViewport(0, 0, target.getWidth(), target.getHeight());
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    private static int maximumTextureSize() {
        IntBuffer value = BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, value);
        return value.get(0);
    }

    private void reject(int width, int height, String reason) {
        rejectedBackBufferWidth = width;
        rejectedBackBufferHeight = height;
        if (Gdx.app != null) {
            Gdx.app.error("CardSMAA", "GPU card SMAA disabled for "
                    + width + "x" + height + ": " + reason);
        }
    }

    @Override
    public void dispose() {
        disposeBuffers();
        disposeResources();
        active = false;
        activeRenderingMode = RENDERING_NATIVE;
        effectiveRenderingMode = RENDERING_NATIVE;
        lastAttemptedRenderingMode = -1;
    }

    private void disposeBuffers() {
        if (colorLayer != null) colorLayer.dispose();
        if (edgesLayer != null) edgesLayer.dispose();
        if (blendLayer != null) blendLayer.dispose();
        colorLayer = null;
        edgesLayer = null;
        blendLayer = null;
    }

    private void disposeResources() {
        if (edgesShader != null) edgesShader.dispose();
        if (weightsShader != null) weightsShader.dispose();
        if (blendShader != null) blendShader.dispose();
        if (areaTexture != null) areaTexture.dispose();
        if (searchTexture != null) searchTexture.dispose();
        if (fullscreenTriangle != null) fullscreenTriangle.dispose();
        edgesShader = null;
        weightsShader = null;
        blendShader = null;
        areaTexture = null;
        searchTexture = null;
        fullscreenTriangle = null;
    }
}
