/*
 * Copyright (C) 2020-2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class GdxShuffleAudioSynchronizationTest {

    @Test
    void wavMayStartOnlyAtAnOnTimeVisualCycleEdge() {
        assertTrue(CoronaPokerGdxTable.shuffleAudioCycleStartInSync(0f));
        assertTrue(CoronaPokerGdxTable.shuffleAudioCycleStartInSync(0.049f));
        assertFalse(CoronaPokerGdxTable.shuffleAudioCycleStartInSync(0.051f));
        assertFalse(CoronaPokerGdxTable.shuffleAudioCycleStartInSync(-0.001f));
    }

    @Test
    void firstFrameUploadArmsTheSharedPresentationClockBeforeAudio()
            throws Exception {
        String table = source();
        String update = method(table, "private void updateLiveShuffle()",
                "private void startLiveShuffleSound(");
        int upload = update.indexOf("active.animation.frameAt(0f)");
        int clock = update.indexOf("active.startPresentation(System.nanoTime())");
        int audio = update.indexOf("startLiveShuffleSound(active, 0)");
        assertTrue(upload >= 0 && clock > upload && audio > clock,
                "frame zero, shared epoch and WAV must arm in that order");
        assertFalse(method(table, "private void acceptLiveShuffle(",
                "private void updateLiveShuffle()").contains(
                        "startLiveShuffleSound("),
                "accepting START must not race audio against GIF decoding");
    }

    @Test
    void reEnableDefersShuffleAndNeverBackfillsADealSound()
            throws Exception {
        String table = source();
        String toggle = method(table, "private void toggleMasterSound()",
                "private void adjustMasterVolume(");
        assertTrue(toggle.contains("deferLiveShuffleSoundUntilNextCycle()"));
        assertFalse(toggle.contains("play(dealSound"),
                "a card already in flight must never receive a late sound");

        String defer = method(table,
                "private void deferLiveShuffleSoundUntilNextCycle()",
                "private void acceptLiveShuffle(");
        assertTrue(defer.contains("currentCycle + 1"));
        assertTrue(defer.contains("stopShuffleSound()"));
    }

    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/tonikelope/"
                + "coronapoker/gdx/CoronaPokerGdxTable.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String method(String source, String startMarker,
            String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0 && end > start, startMarker);
        return source.substring(start, end);
    }
}
