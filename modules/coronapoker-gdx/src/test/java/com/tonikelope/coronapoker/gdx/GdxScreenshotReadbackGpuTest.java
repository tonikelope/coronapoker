/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.utils.ScreenUtils;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;

@Tag("certification")
final class GdxScreenshotReadbackGpuTest {
    private static final Path FIRST = Path.of("first.png");
    private static final Path SECOND = Path.of("second.png");

    @Test
    void keepsTheSubmittedFrameAndSupportsChangingCaptureSize() {
        var config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.setWindowedMode(96, 64);
        config.setBackBufferConfig(8, 8, 8, 8, 0, 0, 4);
        config.disableAudio(true);
        config.useVsync(false);
        var check = new ReadbackCheck();
        new Lwjgl3Application(check, config);
        assumeTrue(check.supported, "OpenGL 3.2 is required for asynchronous readback");
        assertTrue(check.checked);
    }

    private static final class ReadbackCheck extends ApplicationAdapter {
        private final GdxScreenshotReadback readback = new GdxScreenshotReadback();
        private boolean supported;
        private boolean checked;
        private byte[] expected;
        private int previousBuffer;
        private int frames;

        @Override public void create() {
            supported = GL.getCapabilities().OpenGL32;
            if (!supported) {
                assertFalse(readback.submit(FIRST, 63, 31));
                Gdx.app.exit();
                return;
            }
            ScreenUtils.clear(1f, 0f, 0f, 1f);
            expected = ScreenUtils.getFrameBufferPixels(0, 0, 63, 31, false);
            previousBuffer = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, previousBuffer);
            GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, 8);
            assertTrue(readback.submit(FIRST, 63, 31));
            assertPackState();
            ScreenUtils.clear(0f, 0f, 1f, 1f);
        }

        @Override public void render() {
            if (!supported || checked) return;
            assertTrue(++frames < 240, "Screenshot fence never signaled");
            var captured = readback.poll(false);
            if (captured == null) return;
            assertEquals(FIRST, captured.file());
            assertEquals(63, captured.width());
            assertEquals(31, captured.height());
            assertArrayEquals(expected, captured.rgba());
            assertFalse(readback.hasPending());
            assertPackState();

            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
            ScreenUtils.clear(0f, 1f, 0f, 1f);
            expected = ScreenUtils.getFrameBufferPixels(0, 0, 15, 7, false);
            assertTrue(readback.submit(SECOND, 15, 7));
            captured = readback.poll(true);
            assertEquals(SECOND, captured.file());
            assertEquals(15, captured.width());
            assertEquals(7, captured.height());
            assertArrayEquals(expected, captured.rgba());
            assertFalse(readback.hasPending());
            checked = true;
            Gdx.app.exit();
        }

        private void assertPackState() {
            assertEquals(previousBuffer, GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING));
            assertEquals(8, GL11C.glGetInteger(GL11C.GL_PACK_ALIGNMENT));
        }

        @Override public void dispose() {
            readback.dispose();
            if (previousBuffer != 0) GL15C.glDeleteBuffers(previousBuffer);
        }
    }
}
