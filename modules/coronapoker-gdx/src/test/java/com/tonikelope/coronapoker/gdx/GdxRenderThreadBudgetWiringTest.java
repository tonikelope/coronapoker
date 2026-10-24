package com.tonikelope.coronapoker.gdx;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GdxRenderThreadBudgetWiringTest {

    @Test
    void tableHasNoDeadFrameStatisticsOrDuplicateMonitorPolling()
            throws Exception {
        String table = source("CoronaPokerGdxTable.java");
        assertFalse(table.contains("recordFrame("));
        assertFalse(table.contains("frameSamples"));
        assertFalse(table.contains("getDisplayMode(monitor)"));
        assertTrue(table.contains("networkBlockCache.update(delta"));
    }

    @Test
    void frontendAndTableDrawFromTheLowFrequencyNetworkCache()
            throws Exception {
        String frontend = source("GdxFrontendScreen.java");
        String table = source("CoronaPokerGdxTable.java");
        assertTrue(frontend.contains("networkBlockCache.update(frameDelta"));
        assertTrue(frontend.contains("networkBlockCache.current()"));
        assertTrue(table.contains("networkBlockCache.current()"));
    }

    @Test
    void tablePreparationDoesNotBurstChipTexturesOrCompeteOnCommonPool()
            throws Exception {
        String table = source("CoronaPokerGdxTable.java");
        assertFalse(table.contains("createChipTexturePair("));
        assertTrue(table.contains("createNextChipTexture(chipStep)"));
        assertTrue(table.contains("CHIP_TEXTURES_PER_COLOR = 1"));
        assertTrue(table.contains("worker.setPriority(Thread.MIN_PRIORITY)"));
        assertTrue(table.contains("}, creationPreparationExecutor);"));
        assertFalse(table.contains("case 9, 10 ->"));
        assertTrue(table.contains("case 9 ->"));
        assertTrue(table.contains("if (!modalBackdropBlur.prepareNextBuffer("));
        assertTrue(table.contains("modalBackdropBlur.prepareNextBuffer("));
        assertTrue(table.contains("createNextTableFinalizationStep()"));
        assertTrue(table.contains("Recheck immediately before handoff"));
        assertTrue(table.contains("private boolean createNextGraphicsCore() {\n"
                + "        switch (creationResourceIndex) {"));
        assertTrue(table.contains("case 1 -> shapes = new ShapeRenderer();"));
        assertTrue(table.contains("case 2 -> batch = new SpriteBatch(2000);"));
        assertTrue(table.contains("case 11 -> {\n"
                + "                if (!modalBackdropBlur.prepareNextBuffer("));
        assertTrue(table.contains(
                "private boolean createNextTableFinalizationStep() {\n"
                + "        switch (creationResourceIndex) {"));

        String blur = source("GdxModalBackdropBlur.java");
        assertTrue(blur.contains("boolean prepareNextBuffer("));
        assertTrue(blur.contains("backdrop = createBuffer(width, height)"));
        assertTrue(blur.contains("scratch = createBuffer(width, height)"));
        assertTrue(blur.contains("void endCaptureAndCache("));
        assertTrue(blur.contains("boolean hasCachedFrame("));

        String frontend = source("GdxFrontendScreen.java");
        assertTrue(frontend.contains("renderCachedLobbyLoadingFrame()"));
        assertTrue(frontend.contains("modalBackdropBlur.endCaptureAndCache("));
        assertTrue(frontend.contains("modalBackdropBlur.hasCachedFrame("));

        String shell = source("GdxApplicationShell.java");
        assertTrue(shell.contains("switch (pending.handoffStep++)"));
        assertTrue(shell.contains("if (!pending.creationComplete)"));
    }

    @Test
    void chatMediaDecodesOffThreadAndKeepsOneStreamingGifTexture()
            throws Exception {
        String gallery = source("GdxChatGalleryMedia.java");
        String frontend = source("GdxFrontendScreen.java");
        String prepared = source("GdxPreparedChatMedia.java");
        String streaming = source("StreamingGifTextureAnimation.java");

        assertTrue(gallery.contains("GdxPreparedChatMedia.prepareAsync("));
        assertTrue(frontend.contains("GdxPreparedChatMedia.prepareAsync("));
        assertFalse(gallery.contains("GifTextureAnimation.load("));
        assertFalse(frontend.contains("GifTextureAnimation.load("));
        assertTrue(prepared.contains(
                "StreamingGifTextureAnimation.prepareLooping("));
        assertTrue(prepared.contains("new Pixmap(data, 0, data.length)"));
        assertTrue(prepared.contains("}, PREPARATION_EXECUTOR);"));
        assertTrue(streaming.contains(
                "texture = new Texture(uploadPixmap, mipMaps);"));
        assertTrue(streaming.contains(
                "Gdx.gl.glGenerateMipmap(GL20.GL_TEXTURE_2D);"));
    }

    private static String source(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/" + file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
