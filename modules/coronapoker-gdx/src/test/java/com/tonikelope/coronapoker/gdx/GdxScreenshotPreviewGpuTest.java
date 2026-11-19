/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.utils.viewport.FitViewport;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("certification")
final class GdxScreenshotPreviewGpuTest {
    @TempDir Path directory;

    @Test
    void usesPhysicalViewerSizeAndReusesTheAdjacentTexture() throws Exception {
        var shots = List.of(
                png("first.png", 1920, 1080),
                png("second.png", 1600, 900),
                new GdxScreenshotStore.Shot(
                        Files.writeString(directory.resolve("broken.png"), "invalid PNG"), 0L));
        var config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.setWindowedMode(960, 540);
        config.disableAudio(true);
        config.useVsync(false);
        var check = new ViewerCheck(shots);
        new Lwjgl3Application(check, config);
        assertTrue(check.finished);
    }

    private GdxScreenshotStore.Shot png(String name, int width, int height) throws Exception {
        Path file = directory.resolve(name);
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        assertTrue(ImageIO.write(image, "png", file.toFile()));
        image.flush();
        return new GdxScreenshotStore.Shot(file, 0L);
    }

    private static final class ViewerCheck extends ApplicationAdapter {
        private final List<GdxScreenshotStore.Shot> shots;
        private final GdxScreenshotPreview preview = new GdxScreenshotPreview();
        private final FitViewport viewport = new FitViewport(1920f, 1080f);
        private GdxScreenshotPreview.Image first;
        private long deadline;
        private int index;
        private boolean finished;

        ViewerCheck(List<GdxScreenshotStore.Shot> shots) {
            this.shots = shots;
        }

        @Override public void create() {
            viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
            deadline = System.nanoTime() + 5_000_000_000L;
            preview.request(shots, 0);
        }

        @Override public void render() {
            if (finished) return;
            assertTrue(System.nanoTime() < deadline, "Screenshot preview timed out");
            GdxScreenshotPreview.Image visible = preview.poll(viewport);
            if (index == 2) {
                if (!preview.failed()) return;
                assertFalse(preview.loading());
                finished = true;
                Gdx.app.exit();
                return;
            }
            if (visible == null) return;
            if (index == 0) {
                first = visible;
                assertEquals(1920, visible.originalWidth());
                assertEquals(1080, visible.originalHeight());
                float density = Math.max(
                        viewport.getScreenWidth() * (float) Gdx.graphics.getBackBufferWidth()
                                / Gdx.graphics.getWidth() / viewport.getWorldWidth(),
                        viewport.getScreenHeight() * (float) Gdx.graphics.getBackBufferHeight()
                                / Gdx.graphics.getHeight() / viewport.getWorldHeight());
                assertEquals((int) Math.ceil(Math.min(1080f, 869f * density)),
                        visible.texture().getHeight());
                index = 1;
                preview.request(shots, index);
            } else {
                assertEquals(1600, visible.originalWidth());
                assertSame(first, preview.request(shots, 0));
                assertSame(visible, preview.request(shots, 1));
                index = 2;
                preview.request(shots, index);
            }
        }

        @Override public void dispose() {
            preview.close();
        }
    }
}
