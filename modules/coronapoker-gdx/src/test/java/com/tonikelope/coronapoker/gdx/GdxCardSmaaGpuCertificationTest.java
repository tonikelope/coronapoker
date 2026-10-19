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
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.utils.ScreenUtils;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
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
            byte[] direct = renderNative(renderer, batch, card, projection);
            byte[] antialiased = renderSmaa(renderer, batch, card, projection);
            int weightedPixels = countNonZeroWeightPixels(renderer);
            assertTrue(weightedPixels >= 12,
                    () -> "SMAA generated only " + weightedPixels
                    + " non-zero blend-weight pixels");
            int changed = 0;
            for (int pixel = 0; pixel < WIDTH * HEIGHT; pixel++) {
                int offset = pixel * 4;
                int delta = Math.abs(unsigned(direct[offset])
                        - unsigned(antialiased[offset]))
                        + Math.abs(unsigned(direct[offset + 1])
                        - unsigned(antialiased[offset + 1]))
                        + Math.abs(unsigned(direct[offset + 2])
                        - unsigned(antialiased[offset + 2]))
                        + Math.abs(unsigned(direct[offset + 3])
                        - unsigned(antialiased[offset + 3]));
                if (delta >= 4) changed++;
            }
            System.out.println("Card SMAA GPU proof: " + weightedPixels
                    + " weighted pixels, " + changed
                    + " final framebuffer pixels changed");
            return changed;
        } finally {
            renderer.dispose();
            card.dispose();
            batch.dispose();
        }
    }

    private static void certifyProductCardShaders() {
        ShaderProgram regular = new ShaderProgram(
                CoronaPokerGdxTable.CARD_VERTEX_SHADER,
                CoronaPokerGdxTable.CARD_FRAGMENT_SHADER);
        ShaderProgram rabbit = new ShaderProgram(
                CoronaPokerGdxTable.CARD_VERTEX_SHADER,
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
            SpriteBatch batch, Texture card, Matrix4 projection) {
        clearBackBuffer();
        batch.setProjectionMatrix(projection);
        assertTrue(renderer.begin(WIDTH, HEIGHT,
                GdxCardSmaaRenderer.RENDERING_SMOOTH, batch));
        drawCard(batch, card);
        renderer.endAndComposite(batch, projection, () -> {
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0);
            Gdx.gl.glViewport(0, 0, WIDTH, HEIGHT);
        });
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
        for (int offset = -1; offset <= 1; offset++) {
            pixmap.drawLine(8, 68 + offset, 61, 13 + offset);
        }
        Texture texture = new Texture(pixmap, false);
        texture.setFilter(Texture.TextureFilter.Nearest,
                Texture.TextureFilter.Nearest);
        pixmap.dispose();
        return texture;
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
