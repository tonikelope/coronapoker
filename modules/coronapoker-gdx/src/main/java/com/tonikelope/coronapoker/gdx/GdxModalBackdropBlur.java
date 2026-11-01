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
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Shared separable Gaussian backdrop blur for every native GDX modal.
 *
 * <p>The scene is captured at native backbuffer resolution. Modal chrome still
 * darkens and blurs it, but retaining every physical pixel avoids a visibly
 * lower-resolution backdrop on high-DPI displays.</p>
 */
final class GdxModalBackdropBlur implements Disposable {

    private static final String VERTEX_SHADER =
            "attribute vec4 a_position;\n"
            + "attribute vec4 a_color;\n"
            + "attribute vec2 a_texCoord0;\n"
            + "uniform mat4 u_projTrans;\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "void main() {\n"
            + "    v_color = a_color;\n"
            + "    v_texCoords = a_texCoord0;\n"
            + "    gl_Position = u_projTrans * a_position;\n"
            + "}\n";
    private static final String FRAGMENT_SHADER =
            "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "uniform sampler2D u_texture;\n"
            + "uniform vec2 u_texelSize;\n"
            + "uniform vec2 u_direction;\n"
            + "void main() {\n"
            + "    vec2 step = u_texelSize * u_direction;\n"
            // A normalized 33-tap Gaussian kernel (sigma 6.5) combined into
            // seventeen linear-filtered lookups. The wider contiguous kernel
            // keeps the native-resolution backdrop unobtrusive without the
            // directional echoes caused by widening sparse samples.
            + "    vec4 c = texture2D(u_texture, v_texCoords) * 0.06206181;\n"
            + "    c += texture2D(u_texture, v_texCoords + step * 1.49112519) * 0.12052410;\n"
            + "    c += texture2D(u_texture, v_texCoords - step * 1.49112519) * 0.12052410;\n"
            + "    c += texture2D(u_texture, v_texCoords + step * 3.47930178) * 0.10714751;\n"
            + "    c += texture2D(u_texture, v_texCoords - step * 3.47930178) * 0.10714751;\n"
            + "    c += texture2D(u_texture, v_texCoords + step * 5.46750150) * 0.08669949;\n"
            + "    c += texture2D(u_texture, v_texCoords - step * 5.46750150) * 0.08669949;\n"
            + "    c += texture2D(u_texture, v_texCoords + step * 7.45573747) * 0.06385230;\n"
            + "    c += texture2D(u_texture, v_texCoords - step * 7.45573747) * 0.06385230;\n"
            + "    c += texture2D(u_texture, v_texCoords + step * 9.44402263) * 0.04280173;\n"
            + "    c += texture2D(u_texture, v_texCoords - step * 9.44402263) * 0.04280173;\n"
            + "    c += texture2D(u_texture, v_texCoords + step * 11.43236969) * 0.02611378;\n"
            + "    c += texture2D(u_texture, v_texCoords - step * 11.43236969) * 0.02611378;\n"
            + "    c += texture2D(u_texture, v_texCoords + step * 13.42079112) * 0.01450108;\n"
            + "    c += texture2D(u_texture, v_texCoords - step * 13.42079112) * 0.01450108;\n"
            + "    c += texture2D(u_texture, v_texCoords + step * 15.40929903) * 0.00732911;\n"
            + "    c += texture2D(u_texture, v_texCoords - step * 15.40929903) * 0.00732911;\n"
            + "    gl_FragColor = c * v_color;\n"
            + "}\n";

    private final ShaderProgram shader;
    private FrameBuffer backdrop;
    private FrameBuffer scratch;
    private boolean cachedReady;

    GdxModalBackdropBlur() {
        shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!shader.isCompiled()) {
            String log = shader.getLog();
            shader.dispose();
            throw new IllegalStateException("Modal backdrop blur shader: "
                    + log);
        }
    }

    void beginCapture(int backBufferWidth, int backBufferHeight) {
        ensureBuffers(backBufferWidth, backBufferHeight);
        cachedReady = false;
        backdrop.begin();
        Gdx.gl.glViewport(0, 0, backdrop.getWidth(), backdrop.getHeight());
    }

    /**
     * Allocates at most one framebuffer for the requested size. Table startup
     * calls this once per render-loop turn so the first modal frame never has
     * to allocate both native-resolution surfaces in one burst.
     */
    boolean prepareNextBuffer(int backBufferWidth, int backBufferHeight) {
        int width = captureDimension(backBufferWidth);
        int height = captureDimension(backBufferHeight);
        if (bufferHasDifferentSize(backdrop, width, height)
                || bufferHasDifferentSize(scratch, width, height)) {
            disposeBuffers();
        }
        if (backdrop == null) {
            backdrop = createBuffer(width, height);
            return false;
        }
        if (scratch == null) {
            scratch = createBuffer(width, height);
        }
        return true;
    }

    /**
     * Rebinds the backdrop after a nested framebuffer pass.
     *
     * <p>LibGDX framebuffers are not a stack: ending an inner framebuffer
     * binds the default backbuffer. Any table effect that renders through an
     * auxiliary framebuffer must therefore restore this capture explicitly,
     * otherwise the remainder of the table is painted outside the modal
     * backdrop and disappears from the blurred result.</p>
     */
    void resumeCaptureAfterNestedPass() {
        if (backdrop == null) {
            throw new IllegalStateException("Modal backdrop is not active");
        }
        backdrop.bind();
        Gdx.gl.glViewport(0, 0, backdrop.getWidth(), backdrop.getHeight());
    }

    void endCaptureAndDraw(Viewport viewport, SpriteBatch batch,
            Matrix4 projection, Color clearColor) {
        backdrop.end();

        scratch.begin();
        ScreenUtils.clear(clearColor, false);
        Gdx.gl.glViewport(0, 0, scratch.getWidth(), scratch.getHeight());
        batch.setProjectionMatrix(projection);
        drawPass(batch, backdrop.getColorBufferTexture(),
                viewport.getWorldWidth(), viewport.getWorldHeight(), 1f, 0f);
        scratch.end();

        ScreenUtils.clear(clearColor, false);
        viewport.apply();
        batch.setProjectionMatrix(projection);
        drawPass(batch, scratch.getColorBufferTexture(),
                viewport.getWorldWidth(), viewport.getWorldHeight(), 0f, 1f);
    }

    /**
     * Completes the blur into the reusable backdrop itself and presents it.
     * The lobby loading dialog uses this once, then redraws only its animated
     * foreground while table resources are created on later render turns.
     */
    void endCaptureAndCache(Viewport viewport, SpriteBatch batch,
            Matrix4 projection, Color clearColor) {
        backdrop.end();

        scratch.begin();
        ScreenUtils.clear(clearColor, false);
        Gdx.gl.glViewport(0, 0, scratch.getWidth(), scratch.getHeight());
        batch.setProjectionMatrix(projection);
        drawPass(batch, backdrop.getColorBufferTexture(),
                viewport.getWorldWidth(), viewport.getWorldHeight(), 1f, 0f);
        scratch.end();

        backdrop.begin();
        ScreenUtils.clear(clearColor, false);
        Gdx.gl.glViewport(0, 0, backdrop.getWidth(), backdrop.getHeight());
        batch.setProjectionMatrix(projection);
        drawPass(batch, scratch.getColorBufferTexture(),
                viewport.getWorldWidth(), viewport.getWorldHeight(), 0f, 1f);
        backdrop.end();

        cachedReady = true;
        drawCached(viewport, batch, projection, clearColor);
    }

    boolean hasCachedFrame(int backBufferWidth, int backBufferHeight) {
        return cachedReady
                && !bufferHasDifferentSize(backdrop,
                        captureDimension(backBufferWidth),
                        captureDimension(backBufferHeight));
    }

    void drawCached(Viewport viewport, SpriteBatch batch,
            Matrix4 projection, Color clearColor) {
        if (!cachedReady || backdrop == null) {
            throw new IllegalStateException("Modal backdrop cache is empty");
        }
        ScreenUtils.clear(clearColor, false);
        viewport.apply();
        batch.setProjectionMatrix(projection);
        batch.setShader(null);
        batch.begin();
        batch.setColor(Color.WHITE);
        Texture texture = backdrop.getColorBufferTexture();
        batch.draw(texture, 0f, 0f, viewport.getWorldWidth(),
                viewport.getWorldHeight(), 0, 0, texture.getWidth(),
                texture.getHeight(), false, true);
        batch.end();
    }

    int captureWidth() {
        return backdrop == null ? 1 : backdrop.getWidth();
    }

    int captureHeight() {
        return backdrop == null ? 1 : backdrop.getHeight();
    }

    static int captureDimension(int backBufferDimension) {
        return Math.max(1, backBufferDimension);
    }

    private void ensureBuffers(int backBufferWidth, int backBufferHeight) {
        while (!prepareNextBuffer(backBufferWidth, backBufferHeight)) {
            // Runtime resizes still complete synchronously before capture.
            // Phased table startup calls prepareNextBuffer() directly.
        }
    }

    private static boolean bufferHasDifferentSize(FrameBuffer buffer,
            int width, int height) {
        return buffer != null && (buffer.getWidth() != width
                || buffer.getHeight() != height);
    }

    private static FrameBuffer createBuffer(int width, int height) {
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888,
                width, height, false);
        buffer.getColorBufferTexture().setFilter(
                TextureFilter.Linear, TextureFilter.Linear);
        return buffer;
    }

    private void drawPass(SpriteBatch batch, Texture texture,
            float worldWidth, float worldHeight, float directionX,
            float directionY) {
        batch.setShader(shader);
        batch.begin();
        shader.setUniformf("u_texelSize", 1f / backdrop.getWidth(),
                1f / backdrop.getHeight());
        shader.setUniformf("u_direction", directionX, directionY);
        batch.setColor(Color.WHITE);
        batch.draw(texture, 0f, 0f, worldWidth, worldHeight,
                0, 0, texture.getWidth(), texture.getHeight(), false, true);
        batch.end();
        batch.setShader(null);
    }

    @Override
    public void dispose() {
        disposeBuffers();
        shader.dispose();
    }

    private void disposeBuffers() {
        if (backdrop != null) backdrop.dispose();
        if (scratch != null) scratch.dispose();
        backdrop = null;
        scratch = null;
        cachedReady = false;
    }
}
