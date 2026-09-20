/*
 * Copyright (C) 2020-2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class StreamingGifTextureAnimationTest {

    @Test
    void gameOverFramesDecodeIncrementallyWithOriginalTiming()
            throws Exception {
        byte[] data;
        try (InputStream input = getClass().getResourceAsStream(
                "/cinematics/misc/game_over.gif")) {
            assertTrue(input != null, "packaged GAME OVER GIF");
            data = input.readAllBytes();
        }
        AtomicBoolean cancelled = new AtomicBoolean();
        List<StreamingGifTextureAnimation.CpuFrame> frames =
                new ArrayList<>();
        StreamingGifTextureAnimation.decode(data, "game_over.gif", 782,
                frame -> {
                    frames.add(frame);
                    if (frames.size() == 16) cancelled.set(true);
                }, cancelled::get);

        assertEquals(16, frames.size());
        assertEquals(760, frames.get(0).width());
        assertEquals(326, frames.get(0).height());
        assertEquals(0L, frames.get(0).startMs());
        for (int i = 1; i < frames.size(); i++) {
            assertTrue(frames.get(i).startMs()
                    > frames.get(i - 1).startMs());
        }
        assertFalse(hasVisiblePixel(frames.get(0)),
                "the authored opening frame is black");
        assertTrue(frames.stream().skip(1)
                .anyMatch(StreamingGifTextureAnimationTest::hasVisiblePixel),
                "the incremental decoder must composite visible GIF frames");
    }

    @Test
    void shuffleMetadataPreservesCanonicalLoopAndAudioCutoff()
            throws Exception {
        byte[] data;
        try (InputStream input = getClass().getResourceAsStream(
                "/images/decks/goliat/gif/shuffle.gif")) {
            assertTrue(input != null, "packaged Goliat shuffle GIF");
            data = input.readAllBytes();
        }

        StreamingGifTextureAnimation.GifTiming timing =
                StreamingGifTextureAnimation.inspect(data,
                        "shuffle.gif", 960);

        assertEquals(960, timing.width());
        assertEquals(540, timing.height());
        assertEquals(86, timing.frameEndMs().length);
        assertEquals(1_720L, timing.durationMs());
        assertEquals(1_040L, timing.frameEndMs()[51],
                "frame 53 starts after frame 52");
    }

    @Test
    void downloadedGifCanPrepareItsLoopAwayFromTheRenderThread()
            throws Exception {
        byte[] data;
        try (InputStream input = getClass().getResourceAsStream(
                "/cinematics/misc/game_over.gif")) {
            assertTrue(input != null, "packaged GIF fixture");
            data = input.readAllBytes();
        }

        StreamingGifTextureAnimation animation =
                StreamingGifTextureAnimation.loadLooping(
                        data, "chat-fixture.gif", 360);
        try {
            assertEquals(360, animation.width());
            assertTrue(animation.height() > 0);
            assertTrue(animation.durationSeconds() > 0f);
        } finally {
            animation.dispose();
        }
    }

    private static boolean hasVisiblePixel(
            StreamingGifTextureAnimation.CpuFrame frame) {
        int pixelStride = Math.max(1,
                (frame.rgba().length / 4) / 2048);
        for (int i = 0; i < frame.rgba().length; i += pixelStride * 4) {
            int red = frame.rgba()[i] & 0xff;
            int green = frame.rgba()[i + 1] & 0xff;
            int blue = frame.rgba()[i + 2] & 0xff;
            int alpha = frame.rgba()[i + 3] & 0xff;
            if (alpha > 0 && (red > 12 || green > 12 || blue > 12)) {
                return true;
            }
        }
        return false;
    }
}
