/*
 * Copyright (C) 2020-2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class StreamingGifTextureAnimationTest {

    @Test
    void loopingTimelineAdvancesByExactlyOneCycleWithoutFrozenGaps() {
        long cycle = 1_720L;
        long offset = 0L;
        for (int repetition = 1; repetition <= 8; repetition++) {
            offset = StreamingGifTextureAnimation.nextLoopStart(offset,
                    cycle);
            assertEquals(repetition * cycle, offset);
        }
        assertThrows(ArithmeticException.class,
                () -> StreamingGifTextureAnimation.nextLoopStart(
                        Long.MAX_VALUE - 2L, cycle));
    }

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
        assertTrue(frames.stream().allMatch(frame ->
                frame.endMs() > frame.startMs()),
                "every streaming frame must carry its real end time");
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

        StreamingGifTextureAnimation.PreparedLooping prepared =
                StreamingGifTextureAnimation.prepareLooping(
                        data, "chat-fixture.gif", 360);
        assertEquals(360, prepared.timing().width());
        assertTrue(prepared.primedFrames().isEmpty(),
                "ordinary streaming GIFs must retain no decoded frame cache");
        StreamingGifTextureAnimation animation =
                StreamingGifTextureAnimation.loadLooping(prepared);
        try {
            assertEquals(360, animation.width());
            assertTrue(animation.height() > 0);
            assertTrue(animation.durationSeconds() > 0f);
        } finally {
            animation.dispose();
        }
    }

    @Test
    void shuffleCanPrimeExactlyOneBoundedStreamingWindow()
            throws Exception {
        byte[] data;
        try (InputStream input = getClass().getResourceAsStream(
                "/images/decks/goliat/gif/shuffle.gif")) {
            assertNotNull(input, "packaged Goliat shuffle GIF");
            data = input.readAllBytes();
        }

        StreamingGifTextureAnimation.PreparedLooping prepared =
                StreamingGifTextureAnimation.prepareLoopingPrimed(data,
                        "shuffle.gif", 360,
                        StreamingGifTextureAnimation.DECODED_FRAME_BUFFER);

        assertEquals(StreamingGifTextureAnimation.DECODED_FRAME_BUFFER,
                prepared.primedFrames().size());
        assertEquals(0L, prepared.primedFrames().get(0).startMs());
        assertEquals(prepared.timing().frameEndMs()[5],
                prepared.primedFrames().get(5).endMs());
        assertThrows(IllegalArgumentException.class,
                () -> StreamingGifTextureAnimation.prepareLoopingPrimed(data,
                        "shuffle.gif", 360,
                        StreamingGifTextureAnimation.DECODED_FRAME_BUFFER + 1));

        StreamingGifTextureAnimation animation =
                StreamingGifTextureAnimation.loadLooping(prepared);
        try {
            assertNotNull(animation.decodedFrameAt(0f),
                    "frame zero must be ready before live playback starts");
        } finally {
            animation.dispose();
        }
    }

    @Test
    void streamingAfterPrimedPrefixPreservesPixelsAndTiming()
            throws Exception {
        byte[] data;
        try (InputStream input = getClass().getResourceAsStream(
                "/images/decks/goliat/gif/shuffle.gif")) {
            assertNotNull(input, "packaged Goliat shuffle GIF");
            data = input.readAllBytes();
        }

        int primed = StreamingGifTextureAnimation.DECODED_FRAME_BUFFER;
        int compared = 4;
        List<StreamingGifTextureAnimation.CpuFrame> canonical
                = new ArrayList<>();
        StreamingGifTextureAnimation.decode(data, "shuffle.gif", 360,
                86, 0, frame -> canonical.add(frame),
                () -> canonical.size() >= primed + compared);

        List<StreamingGifTextureAnimation.CpuFrame> afterPrefix
                = new ArrayList<>();
        StreamingGifTextureAnimation.decode(data, "shuffle.gif", 360,
                86, primed, frame -> afterPrefix.add(frame),
                () -> afterPrefix.size() >= compared);

        assertEquals(compared, afterPrefix.size());
        for (int index = 0; index < compared; index++) {
            StreamingGifTextureAnimation.CpuFrame expected
                    = canonical.get(primed + index);
            StreamingGifTextureAnimation.CpuFrame actual
                    = afterPrefix.get(index);
            assertEquals(expected.width(), actual.width());
            assertEquals(expected.height(), actual.height());
            assertEquals(expected.startMs(), actual.startMs());
            assertEquals(expected.endMs(), actual.endMs());
            assertArrayEquals(expected.rgba(), actual.rgba(),
                    "primed prefix must preserve GIF composition");
        }

        assertThrows(IllegalArgumentException.class,
                () -> StreamingGifTextureAnimation.decode(data,
                        "shuffle.gif", 360, 86, -1,
                        frame -> { }, () -> false));
    }

    @Test
    void iwtsthStreamingGifCannotFinishBeforeItsFirstVisibleFrame()
            throws Exception {
        byte[] gif;
        try (InputStream input = getClass().getResourceAsStream(
                "/cinematics/misc/iwtsth.gif")) {
            assertNotNull(input, "packaged IWTSTH GIF");
            gif = input.readAllBytes();
        }
        StreamingGifTextureAnimation animation =
                StreamingGifTextureAnimation.load(gif, "iwtsth.gif", 563);
        try {
            StreamingGifTextureAnimation.CpuFrame first = null;
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (first == null && System.nanoTime() < deadline) {
                first = animation.decodedFrameAt(0f);
                if (first == null) Thread.sleep(5L);
            }

            assertNotNull(first, "the packaged IWTSTH GIF must decode frame zero");
            assertTrue(animation.durationSeconds() > 0f,
                    "streaming decode must publish discovered frame timing");
            assertFalse(animation.playbackComplete(0.05f),
                    "IWTSTH must not be disposed at the former 50 ms fallback");
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
