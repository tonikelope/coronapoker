package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;

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
    private final Matrix4 pixelProjection = new Matrix4();
    private final Matrix4 savedProjection = new Matrix4();
    private final Matrix4 savedTransform = new Matrix4();
    private final Matrix4 identity = new Matrix4();
    private FrameBuffer backdrop;
    private FrameBuffer scratch;
    private CaptureArea cachedArea;

    private record CaptureArea(int x, int y, int width, int height) {
        static CaptureArea of(Viewport viewport) {
            return new CaptureArea(HdpiUtils.toBackBufferX(viewport.getScreenX()),
                    HdpiUtils.toBackBufferY(viewport.getScreenY()),
                    captureDimension(HdpiUtils.toBackBufferX(viewport.getScreenWidth())),
                    captureDimension(HdpiUtils.toBackBufferY(viewport.getScreenHeight())));
        }
    }

    GdxModalBackdropBlur() {
        shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!shader.isCompiled()) {
            String log = shader.getLog();
            shader.dispose();
            throw new IllegalStateException("Modal backdrop blur shader: "
                    + log);
        }
    }

    /**
     * Allocates at most one framebuffer for the requested size. Table startup
     * calls this once per render-loop turn so the first modal frame never has
     * to allocate both native-resolution surfaces in one burst.
     */
    boolean prepareNextBuffer(Viewport viewport) {
        CaptureArea area = CaptureArea.of(viewport);
        int width = area.width();
        int height = area.height();
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

    /** Filters the pixels already composed by the live renderer, in place. */
    void blurPresentedScene(Viewport viewport, SpriteBatch batch, boolean cache) {
        while (!prepareNextBuffer(viewport)) {
            // Startup preallocates one surface per turn. Runtime resize needs
            // both surfaces before we can present this frame.
        }
        CaptureArea area = CaptureArea.of(viewport);
        cachedArea = null;
        boolean clipped = GL11C.glIsEnabled(GL20.GL_SCISSOR_TEST);
        boolean blending = beginPixelPass(batch, area);
        try {
            if (clipped) Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            capturePresentedPixels(area);
            scratch.begin();
            try {
                drawPass(batch, backdrop.getColorBufferTexture(), area.width(), area.height(), 1f, 0f);
            } finally {
                scratch.end();
            }
            if (cache) {
                backdrop.begin();
                try {
                    drawPass(batch, scratch.getColorBufferTexture(), area.width(), area.height(), 0f, 1f);
                } finally {
                    backdrop.end();
                }
                cachedArea = area;
                viewport.apply();
                drawTexture(batch, backdrop.getColorBufferTexture(), area.width(), area.height());
            } else {
                viewport.apply();
                drawPass(batch, scratch.getColorBufferTexture(), area.width(), area.height(), 0f, 1f);
            }
        } finally {
            endPixelPass(viewport, batch, blending, clipped);
        }
    }

    private void capturePresentedPixels(CaptureArea area) {
        if (GL.getCapabilities().OpenGL30) {
            int drawn = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
            int read = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
            try {
                // Equal source and destination extents also resolve window MSAA.
                GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, drawn);
                GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, backdrop.getFramebufferHandle());
                GL30C.glBlitFramebuffer(area.x(), area.y(), area.x() + area.width(),
                        area.y() + area.height(), 0, 0, area.width(), area.height(),
                        GL20.GL_COLOR_BUFFER_BIT, GL20.GL_NEAREST);
            } finally {
                GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, drawn);
                GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, read);
            }
        } else {
            backdrop.getColorBufferTexture().bind(0);
            Gdx.gl.glCopyTexSubImage2D(GL20.GL_TEXTURE_2D, 0, 0, 0,
                    area.x(), area.y(), area.width(), area.height());
        }
    }

    boolean hasCachedFrame(Viewport viewport) {
        return cachedArea != null && cachedArea.equals(CaptureArea.of(viewport));
    }

    void drawCached(Viewport viewport, SpriteBatch batch, Color clearColor) {
        if (!hasCachedFrame(viewport)) {
            throw new IllegalStateException("Modal backdrop cache does not match the viewport");
        }
        boolean clipped = GL11C.glIsEnabled(GL20.GL_SCISSOR_TEST);
        boolean blending = beginPixelPass(batch, cachedArea);
        try {
            if (clipped) Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            ScreenUtils.clear(clearColor, false);
            viewport.apply();
            drawTexture(batch, backdrop.getColorBufferTexture(), cachedArea.width(), cachedArea.height());
        } finally {
            endPixelPass(viewport, batch, blending, clipped);
        }
    }

    private boolean beginPixelPass(SpriteBatch batch, CaptureArea area) {
        savedProjection.set(batch.getProjectionMatrix());
        savedTransform.set(batch.getTransformMatrix());
        batch.setProjectionMatrix(pixelProjection.setToOrtho2D(0, 0, area.width(), area.height()));
        batch.setTransformMatrix(identity);
        boolean blending = batch.isBlendingEnabled();
        batch.disableBlending();
        return blending;
    }

    private void endPixelPass(Viewport viewport, SpriteBatch batch, boolean blending, boolean clipped) {
        batch.setShader(null);
        batch.setProjectionMatrix(savedProjection);
        batch.setTransformMatrix(savedTransform);
        if (blending) batch.enableBlending();
        if (clipped) Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        viewport.apply();
    }

    private void drawTexture(SpriteBatch batch, Texture texture, float width, float height) {
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(texture, 0f, 0f, width, height, 0, 0,
                texture.getWidth(), texture.getHeight(), false, true);
        batch.end();
    }

    static int captureDimension(int backBufferDimension) {
        return Math.max(1, backBufferDimension);
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
        cachedArea = null;
    }
}
