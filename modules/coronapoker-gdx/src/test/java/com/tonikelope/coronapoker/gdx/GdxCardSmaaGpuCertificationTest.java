/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Field;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in real-GPU proof that the card SMAA pipeline is not a silent no-op.
 * Excluded from normal CI because it needs a desktop OpenGL context.
 */
@Tag("certification")
final class GdxCardSmaaGpuCertificationTest {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 128;

    @Test
    void smoothRenderingChangesActualEdgePixelsOnTheGpu() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Integer> changedPixels = new AtomicReference<>(0);

        Lwjgl3ApplicationConfiguration config
                = new Lwjgl3ApplicationConfiguration();
        config.setTitle("CoronaPoker card SMAA certification");
        config.setInitialVisible(false);
        var monitors = Lwjgl3ApplicationConfiguration.getMonitors();
        var monitor = monitors[Math.min(1, monitors.length - 1)];
        config.setWindowPosition(monitor.virtualX + 8, monitor.virtualY + 8);
        config.disableAudio(true);
        config.setWindowedMode(WIDTH, HEIGHT);
        config.setBackBufferConfig(8, 8, 8, 8, 0, 0, 0);
        config.useVsync(false);
        config.setForegroundFPS(60);

        new Lwjgl3Application(new ApplicationAdapter() {
            @Override
            public void create() {
                try {
                    changedPixels.set(renderAndCompare());
                } catch (Throwable problem) {
                    failure.set(problem);
                } finally {
                    Gdx.app.exit();
                }
            }
        }, config);

        assertNull(failure.get(), () -> "GPU certification failed: "
                + failure.get());
        assertTrue(changedPixels.get() >= 12,
                () -> "SMAA was effectively a no-op: only "
                + changedPixels.get() + " framebuffer pixels changed");
    }

    private static int renderAndCompare() {
        certifyProductCardShaders();
        Matrix4 projection = new Matrix4().setToOrtho2D(0f, 0f,
                WIDTH, HEIGHT);
        SpriteBatch batch = new SpriteBatch();
        Texture card = diagnosticCard();
        GdxCardSmaaRenderer renderer = new GdxCardSmaaRenderer();
        try {
            int artworkChanged = countChangedPixels(
                    renderProductCard(batch, card, projection, 0f, 13f, false),
                    renderProductCard(batch, card, projection, 1f, 13f, false));
            assertTrue(artworkChanged >= 12,
                    () -> "Smooth card artwork prefilter was effectively a "
                    + "no-op: only " + artworkChanged
                    + " framebuffer pixels changed");
            verifyRemoteSilhouette(batch, card, projection);
            verifyMeshSilhouette(batch, card, projection);
            byte[] direct = renderNative(renderer, batch, card, projection);
            byte[] antialiased = renderSmaa(renderer, batch, card, projection, null);
            assertArrayEquals(antialiased, renderSmaa(renderer, batch, card,
                    projection, new Rectangle(28f, 8f, 104f, 112f)),
                    "Bounding the GPU work must preserve the full framebuffer");
            int weightedPixels = countNonZeroWeightPixels(renderer);
            assertTrue(weightedPixels >= 12,
                    () -> "SMAA generated only " + weightedPixels
                    + " non-zero blend-weight pixels");
            int changed = countChangedPixels(direct, antialiased);
            System.out.println("Card artwork GPU proof: " + artworkChanged
                    + " final framebuffer pixels changed");
            System.out.println("Card SMAA GPU proof: " + weightedPixels
                    + " weighted pixels, " + changed
                    + " final framebuffer pixels changed");
            verifyCardTextureModes(card);
            return changed;
        } finally {
            renderer.dispose();
            card.dispose();
            batch.dispose();
        }
    }

    private static void verifyRemoteSilhouette(SpriteBatch batch, Texture card, Matrix4 projection) {
        for (float angle : new float[]{7f, -7f}) {
            byte[] regular = renderProductCard(batch, card, projection, 1f, angle, false);
            byte[] remote = renderProductCard(batch, card, projection, 1f, angle, true);
            // The white border of the diagnostic card meets a black background.
            assertTrue(maxRightEdgeStep(regular) > 200);
            assertCompactRightEdge(regular, remote, angle);
            for (int y = 44; y < 84; y++) {
                int start = (y * WIDTH + 60) * 4;
                int end = start + 40 * 4;
                assertTrue(java.util.Arrays.equals(regular, start, end, remote, start, end),
                        "The opaque artwork must retain its original samples");
            }
        }
    }

    private static void verifyMeshSilhouette(SpriteBatch batch, Texture card, Matrix4 projection) {
        byte[] regular = renderShell(batch, card, projection, GdxCardShell.Resolve.SUPERSAMPLED);
        byte[] softened = renderShell(batch, card, projection, GdxCardShell.Resolve.CARD_SILHOUETTE);
        assertCompactRightEdge(regular, softened, 13f);
        for (int y = 44; y < 84; y++) {
            int start = (y * WIDTH + 60) * 4;
            int end = start + 40 * 4;
            assertTrue(java.util.Arrays.equals(regular, start, end, softened, start, end),
                    "The mesh composite must preserve opaque printed detail");
        }
    }

    private static byte[] renderShell(SpriteBatch batch, Texture card, Matrix4 projection,
            GdxCardShell.Resolve resolve) {
        GdxCardShell shell = new GdxCardShell(4, resolve);
        try {
            clearBackBuffer();
            batch.setProjectionMatrix(projection);
            shell.beginPair(20, 6, 122, 116);
            shell.renderLayer(() -> drawCard(batch, card), WIDTH, HEIGHT);
            shell.endPair();
            return ScreenUtils.getFrameBufferPixels(0, 0, WIDTH, HEIGHT, false);
        } finally {
            shell.dispose();
        }
    }

    private static void assertCompactRightEdge(byte[] regular, byte[] filtered, float angle) {
        assertTrue(maxRightEdgeStep(filtered) < 0.85f * maxRightEdgeStep(regular),
                "The card must still smooth the step across its white edge");

        // Both fixtures draw a 70 x 80 card centred in the framebuffer. Measure
        // only the black background just beyond its straight right side.
        double cos = Math.cos(Math.toRadians(angle));
        double sin = Math.sin(Math.toRadians(angle));
        int sum = 0;
        int count = 0;
        for (int y = 44; y < 84; y++) {
            for (int x = WIDTH / 2; x < WIDTH; x++) {
                double outside = (x + 0.5 - WIDTH / 2.0) * cos
                        + (y + 0.5 - HEIGHT / 2.0) * sin - 35.0;
                if (outside >= 0.85 && outside <= 1.5) {
                    sum += unsigned(filtered[(y * WIDTH + x) * 4]);
                    count++;
                }
            }
        }
        assertTrue(count >= 20, "The exterior stripe must contain enough background pixels");
        assertTrue(sum <= 8 * count, "Mean exterior brightness: " + sum / (float) count);
    }

    private static int maxRightEdgeStep(byte[] image) {
        int maximum = 0;
        for (int y = 44; y < 84; y++) {
            int previous = 0;
            for (int x = WIDTH - 1; x >= WIDTH / 2; x--) {
                int red = unsigned(image[(y * WIDTH + x) * 4]);
                maximum = Math.max(maximum, red - previous);
                previous = red;
                if (red >= 250) break;
            }
            assertTrue(previous >= 250, "The scan must reach the opaque white border");
        }
        return maximum;
    }

    private static void verifyCardTextureModes(Texture card) {
        float maximum = Texture.getMaxAnisotropicFilterLevel();
        for (int mode : new int[]{GdxCardSmaaRenderer.RENDERING_BALANCED,
                GdxCardSmaaRenderer.RENDERING_SMOOTH_AF, GdxCardSmaaRenderer.RENDERING_SMOOTH}) {
            GdxCardSampling.bind(card, 0, mode);
            boolean smoothOnly = mode == GdxCardSmaaRenderer.RENDERING_SMOOTH;
            assertEquals(smoothOnly ? GL11.GL_LINEAR_MIPMAP_NEAREST : GL11.GL_LINEAR_MIPMAP_LINEAR,
                    GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER));
            if (maximum > 1f) {
                assertEquals(smoothOnly ? 1f : Math.min(16f, maximum),
                        GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D,
                                EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT), .001f);
            }
        }
    }

    private static byte[] renderProductCard(SpriteBatch batch, Texture card,
            Matrix4 projection, float artworkSmooth, float angle, boolean remoteSeat) {
        clearBackBuffer();
        ShaderProgram shader = new ShaderProgram(
                CoronaPokerGdxTable.CARD_VERTEX_SHADER,
                CoronaPokerGdxTable.CARD_FRAGMENT_SHADER);
        assertTrue(shader.isCompiled(), shader::getLog);
        try {
            batch.setProjectionMatrix(projection);
            batch.setShader(shader);
            batch.begin();
            card.bind(1);
            card.bind(0);
            shader.setUniformi("u_texture", 0);
            shader.setUniformi("u_frontTexture", 1);
            shader.setUniformf("u_cornerRadius", CoronaPokerGdxTable.cardCornerRadius());
            shader.setUniformf("u_perspective", 0f);
            shader.setUniformf("u_flipAngle", 0f);
            shader.setUniformf("u_cardAspect", 80f / 70f);
            shader.setUniformf("u_canvasScale",
                    CoronaPokerGdxTable.cardShaderCanvasScale());
            shader.setUniformf("u_artworkSmooth", artworkSmooth);
            shader.setUniformf("u_remoteSeat", remoteSeat ? 1f : 0f);
            shader.setUniformf("u_overlay", 1f, 1f, 1f, 0f);
            batch.setColor(Color.WHITE);
            batch.draw(card, 27.5f, 4f, 52.5f, 60f, 105f, 120f,
                    1f, 1f, angle, 0, 0, card.getWidth(), card.getHeight(),
                    false, false);
            batch.end();
            Gdx.gl.glFinish();
            return ScreenUtils.getFrameBufferPixels(0, 0,
                    WIDTH, HEIGHT, false);
        } finally {
            if (batch.isDrawing()) batch.end();
            batch.setShader(null);
            shader.dispose();
        }
    }

    private static void certifyProductCardShaders() {
        ShaderProgram regular = new ShaderProgram(
                CoronaPokerGdxTable.CARD_VERTEX_SHADER,
                CoronaPokerGdxTable.CARD_FRAGMENT_SHADER);
        ShaderProgram rabbit = new ShaderProgram(
                GdxRabbitPeel.VERTEX_SHADER,
                CoronaPokerGdxTable.RABBIT_PEEL_FRAGMENT_SHADER);
        try {
            assertTrue(regular.isCompiled(), () ->
                    "Card shader failed on the real GPU: "
                    + regular.getLog());
            assertTrue(rabbit.isCompiled(), () ->
                    "Rabbit card shader failed on the real GPU: "
                    + rabbit.getLog());
        } finally {
            regular.dispose();
            rabbit.dispose();
        }
    }

    private static byte[] renderNative(GdxCardSmaaRenderer renderer,
            SpriteBatch batch, Texture card, Matrix4 projection) {
        clearBackBuffer();
        batch.setProjectionMatrix(projection);
        assertFalse(renderer.begin(WIDTH, HEIGHT,
                GdxCardSmaaRenderer.RENDERING_NATIVE, batch),
                "native rendering must bypass every SMAA framebuffer");
        GdxCardSmaaRenderer.useStandardAlpha(batch);
        drawCard(batch, card);
        Gdx.gl.glFinish();
        return ScreenUtils.getFrameBufferPixels(0, 0, WIDTH, HEIGHT, false);
    }

    private static byte[] renderSmaa(GdxCardSmaaRenderer renderer,
            SpriteBatch batch, Texture card, Matrix4 projection, Rectangle pixels) {
        clearBackBuffer();
        batch.setProjectionMatrix(projection);
        assertTrue(renderer.begin(WIDTH, HEIGHT,
                GdxCardSmaaRenderer.RENDERING_SMOOTH, batch));
        drawCard(batch, card);
        renderer.endAndComposite(batch, projection, () -> {
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0);
            Gdx.gl.glViewport(0, 0, WIDTH, HEIGHT);
        }, pixels);
        Gdx.gl.glFinish();
        return ScreenUtils.getFrameBufferPixels(0, 0, WIDTH, HEIGHT, false);
    }

    private static void drawCard(SpriteBatch batch, Texture card) {
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(card, 45f, 24f, 35f, 40f, 70f, 80f,
                1f, 1f, 13f, 0, 0, card.getWidth(), card.getHeight(),
                false, false);
        batch.end();
    }

    private static Texture diagnosticCard() {
        Pixmap pixmap = new Pixmap(70, 80, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        pixmap.setColor(0.08f, 0.10f, 0.72f, 1f);
        pixmap.drawRectangle(8, 12, 54, 57);
        pixmap.drawLine(8, 68, 61, 13);
        Texture texture = new Texture(pixmap, true);
        texture.setFilter(CoronaPokerGdxTable.cardMinificationFilter(),
                Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    private static int countChangedPixels(byte[] first, byte[] second) {
        int changed = 0;
        for (int pixel = 0; pixel < WIDTH * HEIGHT; pixel++) {
            int offset = pixel * 4;
            int delta = Math.abs(unsigned(first[offset])
                    - unsigned(second[offset]))
                    + Math.abs(unsigned(first[offset + 1])
                    - unsigned(second[offset + 1]))
                    + Math.abs(unsigned(first[offset + 2])
                    - unsigned(second[offset + 2]))
                    + Math.abs(unsigned(first[offset + 3])
                    - unsigned(second[offset + 3]));
            if (delta >= 4) changed++;
        }
        return changed;
    }

    private static int countNonZeroWeightPixels(GdxCardSmaaRenderer renderer) {
        try {
            Field field = GdxCardSmaaRenderer.class
                    .getDeclaredField("blendLayer");
            field.setAccessible(true);
            FrameBuffer weights = (FrameBuffer) field.get(renderer);
            weights.bind();
            Gdx.gl.glViewport(0, 0, weights.getWidth(), weights.getHeight());
            byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0,
                    weights.getWidth(), weights.getHeight(), false);
            int weighted = 0;
            for (int pixel = 0; pixel < WIDTH * HEIGHT; pixel++) {
                int offset = pixel * 4;
                if (unsigned(pixels[offset]) > 1
                        || unsigned(pixels[offset + 1]) > 1
                        || unsigned(pixels[offset + 2]) > 1
                        || unsigned(pixels[offset + 3]) > 1) {
                    weighted++;
                }
            }
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0);
            Gdx.gl.glViewport(0, 0, WIDTH, HEIGHT);
            return weighted;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect SMAA blend target",
                    failure);
        }
    }

    private static void clearBackBuffer() {
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0);
        Gdx.gl.glViewport(0, 0, WIDTH, HEIGHT);
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }
}
