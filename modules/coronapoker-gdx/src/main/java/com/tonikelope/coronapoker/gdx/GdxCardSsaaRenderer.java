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
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.nio.IntBuffer;

/**
 * Resolves complete card layers with true screen-space SSAA.
 *
 * <p>Cards are rasterized at the selected 2x, 3x or 4x physical dimensions,
 * after all fan rotation and perspective transforms have been applied. A
 * single box resolve therefore integrates 4, 9 or 16 physical subpixels
 * for silhouettes and artwork alike without modifying the deck pixels or
 * relying on colour-aware sharpening heuristics.</p>
 *
 * <p>The layer stores premultiplied colour. This avoids dark fringes around
 * transparent rounded cards when the high-resolution result is composited
 * back over the table. Unsupported framebuffer sizes fail closed to the
 * table's ordinary direct rendering path.</p>
 */
final class GdxCardSsaaRenderer implements Disposable {

    private static final String RESOLVE_VERTEX_SHADER =
            "attribute vec4 a_position;\n"
            + "attribute vec4 a_color;\n"
            + "attribute vec2 a_texCoord0;\n"
            + "uniform mat4 u_projTrans;\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "void main() {\n"
            + "    v_color = a_color;\n"
            + "    v_color.a = v_color.a * (255.0 / 254.0);\n"
            + "    v_texCoords = a_texCoord0;\n"
            + "    gl_Position = u_projTrans * a_position;\n"
            + "}\n";
    private static final String RESOLVE_FRAGMENT_SHADER =
            "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "uniform sampler2D u_texture;\n"
            + "uniform vec2 u_texelSize;\n"
            + "uniform float u_linearScale;\n"
            + "vec4 tap(float x, float y) {\n"
            + "    return texture2D(u_texture, v_texCoords"
            + " + vec2(x, y) * u_texelSize);\n"
            + "}\n"
            + "void main() {\n"
            + "    vec4 resolved;\n"
            + "    if (u_linearScale < 2.5) {\n"
            + "        resolved = (tap(-0.5, -0.5) + tap(0.5, -0.5)"
            + " + tap(-0.5, 0.5) + tap(0.5, 0.5)) * 0.25;\n"
            + "    } else if (u_linearScale < 3.5) {\n"
            + "        resolved = (tap(-1.0, -1.0) + tap(0.0, -1.0)"
            + " + tap(1.0, -1.0) + tap(-1.0, 0.0) + tap(0.0, 0.0)"
            + " + tap(1.0, 0.0) + tap(-1.0, 1.0) + tap(0.0, 1.0)"
            + " + tap(1.0, 1.0)) / 9.0;\n"
            + "    } else {\n"
            + "        resolved = (tap(-1.5, -1.5) + tap(-0.5, -1.5)"
            + " + tap(0.5, -1.5) + tap(1.5, -1.5)"
            + " + tap(-1.5, -0.5) + tap(-0.5, -0.5)"
            + " + tap(0.5, -0.5) + tap(1.5, -0.5)"
            + " + tap(-1.5, 0.5) + tap(-0.5, 0.5)"
            + " + tap(0.5, 0.5) + tap(1.5, 0.5)"
            + " + tap(-1.5, 1.5) + tap(-0.5, 1.5)"
            + " + tap(0.5, 1.5) + tap(1.5, 1.5)) * 0.0625;\n"
            + "    }\n"
            + "    gl_FragColor = resolved * v_color;\n"
            + "}\n";

    private FrameBuffer layer;
    private ShaderProgram resolveShader;
    private boolean active;
    private int activeLinearScale;
    private int rejectedBackBufferWidth = -1;
    private int rejectedBackBufferHeight = -1;
    private int rejectedSamples = -1;

    boolean begin(int backBufferWidth, int backBufferHeight,
            int requestedSamples, SpriteBatch batch) {
        if (active) {
            throw new IllegalStateException("Card SSAA pass is already active");
        }
        if (linearScaleForSamples(requestedSamples) <= 1) {
            // OFF is a real resource release, not merely a bypass. Large
            // 9X/16X targets must not keep consuming VRAM after disabling it.
            disposeLayer();
            return false;
        }
        if (!ensureResolveShader()) return false;
        if (!ensureLayer(backBufferWidth, backBufferHeight,
                requestedSamples)) return false;

        layer.begin();
        active = true;
        activeLinearScale = linearScaleForSamples(requestedSamples);
        Gdx.gl.glViewport(0, 0, layer.getWidth(), layer.getHeight());
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        useStraightAlphaCapture(batch);
        return true;
    }

    void endAndComposite(Viewport viewport, SpriteBatch batch,
            Matrix4 projection, Runnable restoreParentTarget) {
        if (!active) {
            throw new IllegalStateException("Card SSAA pass is not active");
        }
        layer.end();
        active = false;
        restoreParentTarget.run();

        batch.setProjectionMatrix(projection);
        usePremultipliedComposite(batch);
        Texture resolved = layer.getColorBufferTexture();
        batch.setShader(resolveShader);
        batch.begin();
        resolveShader.setUniformf("u_texelSize", 1f / layer.getWidth(),
                1f / layer.getHeight());
        resolveShader.setUniformf("u_linearScale", activeLinearScale);
        batch.setColor(Color.WHITE);
        batch.draw(resolved, 0f, 0f, viewport.getWorldWidth(),
                viewport.getWorldHeight(), 0, 0, resolved.getWidth(),
                resolved.getHeight(), false, true);
        batch.end();
        batch.setShader(null);
        useStandardAlpha(batch);
    }

    /** Rebinds this layer after an inner disabled-card framebuffer pass. */
    void resumeAfterNestedPass() {
        if (!active || layer == null) {
            throw new IllegalStateException("Card SSAA layer is not active");
        }
        layer.bind();
        Gdx.gl.glViewport(0, 0, layer.getWidth(), layer.getHeight());
    }

    boolean isActive() {
        return active;
    }

    int activeWidth() {
        return active ? layer.getWidth() : 0;
    }

    int activeHeight() {
        return active ? layer.getHeight() : 0;
    }

    static int linearScaleForSamples(int samples) {
        if (samples <= 0) return 0;
        int scale = (int) Math.sqrt(samples);
        return scale * scale == samples ? scale : 0;
    }

    static int supersampledDimension(int backBufferDimension, int samples) {
        int linearScale = linearScaleForSamples(samples);
        if (backBufferDimension <= 0 || linearScale <= 1
                || backBufferDimension > Integer.MAX_VALUE / linearScale) {
            return -1;
        }
        return backBufferDimension * linearScale;
    }

    static boolean fitsMaximumTextureSize(int backBufferWidth,
            int backBufferHeight, int samples, int maximumTextureSize) {
        int width = supersampledDimension(backBufferWidth, samples);
        int height = supersampledDimension(backBufferHeight, samples);
        return maximumTextureSize > 0 && width > 0 && height > 0
                && width <= maximumTextureSize
                && height <= maximumTextureSize;
    }

    static void useStraightAlphaCapture(SpriteBatch batch) {
        // RGB is stored premultiplied, while alpha uses the mathematically
        // correct source-over equation rather than being multiplied twice.
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
        // SpriteBatch stores the requested factors but does not update OpenGL
        // while it is idle. ShapeRenderer and the HUD share the same GL state,
        // so leaving the premultiplied factors live after batch.end() tints and
        // over-brightens every translucent surface drawn afterwards.
        if (Gdx.gl != null) {
            Gdx.gl.glBlendFuncSeparate(sourceColor, destinationColor,
                    sourceAlpha, destinationAlpha);
        }
    }

    private boolean ensureLayer(int backBufferWidth, int backBufferHeight,
            int requestedSamples) {
        int width = supersampledDimension(backBufferWidth, requestedSamples);
        int height = supersampledDimension(backBufferHeight, requestedSamples);
        if (width < 1 || height < 1) return false;
        if (layer != null && layer.getWidth() == width
                && layer.getHeight() == height) {
            return true;
        }
        if (backBufferWidth == rejectedBackBufferWidth
                && backBufferHeight == rejectedBackBufferHeight
                && requestedSamples == rejectedSamples) {
            return false;
        }

        int maximumTextureSize = maximumTextureSize();
        if (!fitsMaximumTextureSize(backBufferWidth, backBufferHeight,
                requestedSamples, maximumTextureSize)) {
            reject(backBufferWidth, backBufferHeight, requestedSamples,
                    "requires " + width + "x" + height
                    + " but GL_MAX_TEXTURE_SIZE is " + maximumTextureSize);
            return false;
        }

        disposeLayer();
        try {
            layer = new FrameBuffer(Pixmap.Format.RGBA8888,
                    width, height, false);
            layer.getColorBufferTexture().setFilter(
                    TextureFilter.Nearest, TextureFilter.Nearest);
            rejectedBackBufferWidth = -1;
            rejectedBackBufferHeight = -1;
            rejectedSamples = -1;
            return true;
        } catch (RuntimeException allocationFailure) {
            disposeLayer();
            reject(backBufferWidth, backBufferHeight, requestedSamples,
                    allocationFailure.getClass().getSimpleName());
            return false;
        }
    }

    private static int maximumTextureSize() {
        IntBuffer value = BufferUtils.newIntBuffer(1);
        Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, value);
        return value.get(0);
    }

    private boolean ensureResolveShader() {
        if (resolveShader != null) return true;
        resolveShader = new ShaderProgram(RESOLVE_VERTEX_SHADER,
                RESOLVE_FRAGMENT_SHADER);
        if (resolveShader.isCompiled()) return true;
        if (Gdx.app != null) {
            Gdx.app.error("CardSSAA", "GPU card SSAA shader disabled: "
                    + resolveShader.getLog());
        }
        resolveShader.dispose();
        resolveShader = null;
        return false;
    }

    private void reject(int backBufferWidth, int backBufferHeight,
            int requestedSamples, String reason) {
        rejectedBackBufferWidth = backBufferWidth;
        rejectedBackBufferHeight = backBufferHeight;
        rejectedSamples = requestedSamples;
        if (Gdx.app != null) {
            Gdx.app.error("CardSSAA", "GPU card SSAA disabled for "
                    + backBufferWidth + "x" + backBufferHeight + " at "
                    + requestedSamples + "X: " + reason);
        }
    }

    @Override
    public void dispose() {
        disposeLayer();
        if (resolveShader != null) resolveShader.dispose();
        resolveShader = null;
        active = false;
    }

    private void disposeLayer() {
        if (layer != null) layer.dispose();
        layer = null;
    }
}
