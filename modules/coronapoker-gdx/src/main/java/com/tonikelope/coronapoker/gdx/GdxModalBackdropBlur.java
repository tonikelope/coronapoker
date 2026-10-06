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
 * Shared two-pass backdrop blur for every native GDX modal.
 *
 * <p>The scene is deliberately captured at half resolution: the result is
 * blurred and darkened by the modal chrome, so processing every physical 4K
 * pixel only adds fill-rate without visible detail.</p>
 */
final class GdxModalBackdropBlur implements Disposable {

    private static final int DOWNSAMPLE = 2;
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
            + "    vec4 c = texture2D(u_texture, v_texCoords) * 0.227027;\n"
            + "    c += texture2D(u_texture, v_texCoords + step * 1.384615) * 0.316216;\n"
            + "    c += texture2D(u_texture, v_texCoords - step * 1.384615) * 0.316216;\n"
            + "    c += texture2D(u_texture, v_texCoords + step * 3.230769) * 0.070270;\n"
            + "    c += texture2D(u_texture, v_texCoords - step * 3.230769) * 0.070270;\n"
            + "    gl_FragColor = c * v_color;\n"
            + "}\n";

    private final ShaderProgram shader;
    private FrameBuffer backdrop;
    private FrameBuffer scratch;

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
        backdrop.begin();
        Gdx.gl.glViewport(0, 0, backdrop.getWidth(), backdrop.getHeight());
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
        ScreenUtils.clear(clearColor, true);
        Gdx.gl.glViewport(0, 0, scratch.getWidth(), scratch.getHeight());
        batch.setProjectionMatrix(projection);
        drawPass(batch, backdrop.getColorBufferTexture(),
                viewport.getWorldWidth(), viewport.getWorldHeight(), 1f, 0f);
        scratch.end();

        ScreenUtils.clear(clearColor, true);
        viewport.apply();
        batch.setProjectionMatrix(projection);
        drawPass(batch, scratch.getColorBufferTexture(),
                viewport.getWorldWidth(), viewport.getWorldHeight(), 0f, 1f);
    }

    int captureWidth() {
        return backdrop == null ? 1 : backdrop.getWidth();
    }

    int captureHeight() {
        return backdrop == null ? 1 : backdrop.getHeight();
    }

    static int downsampledDimension(int backBufferDimension) {
        return Math.max(1, backBufferDimension / DOWNSAMPLE);
    }

    private void ensureBuffers(int backBufferWidth, int backBufferHeight) {
        int width = downsampledDimension(backBufferWidth);
        int height = downsampledDimension(backBufferHeight);
        if (backdrop != null && scratch != null
                && backdrop.getWidth() == width
                && backdrop.getHeight() == height
                && scratch.getWidth() == width
                && scratch.getHeight() == height) {
            return;
        }
        disposeBuffers();
        backdrop = new FrameBuffer(Pixmap.Format.RGBA8888,
                width, height, false);
        scratch = new FrameBuffer(Pixmap.Format.RGBA8888,
                width, height, false);
        backdrop.getColorBufferTexture().setFilter(
                TextureFilter.Linear, TextureFilter.Linear);
        scratch.getColorBufferTexture().setFilter(
                TextureFilter.Linear, TextureFilter.Linear);
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
    }
}
