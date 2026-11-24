package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("certification")
final class GdxModalBackdropPixelGpuTest {

    @Test
    void rootCachedAndNestedBlurKeepPhysicalCoordinatesInsideALetterboxedViewport() {
        var config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.setWindowedMode(1100, 760);
        config.setBackBufferConfig(8, 8, 8, 8, 24, 8, 4);
        config.disableAudio(true);
        var failure = new AtomicReference<Throwable>();
        new Lwjgl3Application(new ApplicationAdapter() {
            private GdxModalBackdropBlur liveBlur;
            private FitViewport liveViewport;
            private SpriteBatch liveBatch;
            private ShapeRenderer liveShapes;
            private Vector2 first;

            @Override public void render() {
                try {
                    long window = ((Lwjgl3Graphics) Gdx.graphics).getWindow().getWindowHandle();
                    assertEquals(GLFW.GLFW_FALSE, GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE));
                    if (liveBlur == null) {
                        verifyPixelPlacement();
                        liveViewport = new FitViewport(1920f, 1080f);
                        liveViewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
                        liveBlur = new GdxModalBackdropBlur();
                        liveBatch = new SpriteBatch();
                        liveShapes = new ShapeRenderer();
                        drawMarker(liveViewport, liveShapes, 350f);
                        first = redCenter();
                        while (!liveBlur.prepareNextPass(liveViewport, liveBatch)) {
                            assertSamePosition(first, redCenter());
                        }
                        assertFalse(liveBlur.hasCachedFrame(liveViewport));
                        liveBlur.blurPresentedScene(liveViewport, liveBatch, true);
                        assertSamePosition(first, redCenter());
                        return;
                    }

                    assertFalse(liveBlur.hasCachedFrame(liveViewport),
                            "the previous frame must never be reused as the glass backdrop");
                    drawMarker(liveViewport, liveShapes, 580f);
                    Vector2 moved = redCenter();
                    assertTrue(moved.x > first.x + 100f);
                    liveBlur.blurPresentedScene(liveViewport, liveBatch, true);
                    assertSamePosition(moved, redCenter());
                    Gdx.app.exit();
                } catch (Throwable problem) {
                    failure.set(problem);
                    Gdx.app.exit();
                }
            }

            @Override public void dispose() {
                if (liveShapes != null) liveShapes.dispose();
                if (liveBatch != null) liveBatch.dispose();
                if (liveBlur != null) liveBlur.dispose();
            }
        }, config);
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static void verifyPixelPlacement() {
        var viewport = new FitViewport(1920f, 1080f);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        assertTrue(viewport.getScreenY() > 0);
        var root = new GdxModalBackdropBlur();
        var nested = new GdxModalBackdropBlur();
        var batch = new SpriteBatch();
        var shapes = new ShapeRenderer();
        try {
            drawMarker(viewport, shapes, 350f);
            Vector2 before = redCenter();
            root.blurPresentedScene(viewport, batch, false);
            assertSamePosition(before, redCenter());
            root.blurPresentedScene(viewport, batch, true);
            assertTrue(root.hasCachedFrame(viewport));
            ScreenUtils.clear(Color.BLUE);
            root.drawCached(viewport, batch, Color.BLACK);
            assertSamePosition(before, redCenter());
            nested.blurPresentedScene(viewport, batch, false);
            assertSamePosition(before, redCenter());
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            viewport.setScreenY(viewport.getScreenY() + 1);
            assertFalse(root.hasCachedFrame(viewport));
        } finally {
            shapes.dispose();
            batch.dispose();
            nested.dispose();
            root.dispose();
        }
    }

    private static void drawMarker(FitViewport viewport, ShapeRenderer shapes, float x) {
        ScreenUtils.clear(Color.BLACK);
        viewport.apply();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Color.RED);
        shapes.circle(x, 180f, 36f);
        shapes.end();
    }

    private static Vector2 redCenter() {
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, false);
        double xSum = 0d, ySum = 0d, total = 0d;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int weight = pixels[4 * (y * width + x)] & 255;
                xSum += x * weight;
                ySum += y * weight;
                total += weight;
            }
        }
        assertTrue(total > 1000d, "red marker must survive the blur");
        return new Vector2((float) (xSum / total), (float) (ySum / total));
    }

    private static void assertSamePosition(Vector2 before, Vector2 after) {
        assertEquals(before.x, after.x, 0.5f);
        assertEquals(before.y, after.y, 0.5f);
    }
}
