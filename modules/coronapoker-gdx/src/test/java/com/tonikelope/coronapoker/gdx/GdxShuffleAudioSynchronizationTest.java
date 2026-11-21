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
    void bothReleasesStartAtSampleZeroOfTheSameRecording() {
        var format = new javax.sound.sampled.AudioFormat(1000f, 16, 1, true, false);
        byte[] recording = new byte[4000];
        new java.util.Random(17).nextBytes(recording);
        byte[] wave = GdxShuffleAudio.arrange(recording, format, GdxShuffleGeometry.Motion.MID_BRIDGE);
        org.junit.jupiter.api.Assertions.assertEquals(4460 * 2, wave.length);
        for (int frame = 0; frame < 4460; frame++) {
            int sample = frame >= 920 && frame < 1960 ? frame - 920
                    : frame >= 2600 && frame < 3960 ? frame - 2600 : -1;
            for (int b = 0; b < 2; b++) {
                org.junit.jupiter.api.Assertions.assertEquals(sample < 0 ? 0 : recording[sample*2+b], wave[frame*2+b]);
            }
        }
    }

    @Test
    void shorterMotionsPlayOnlyTheFirstReleaseAndEndAtTheMeshBoundary() {
        var format = new javax.sound.sampled.AudioFormat(1000f, 16, 1, true, false);
        byte[] source = new byte[4000];
        java.util.Arrays.fill(source, (byte) 79);
        var settings = new java.util.Properties();
        var choice = GdxAppearanceOptions.SHUFFLE_DURATION;
        org.junit.jupiter.api.Assertions.assertEquals(GdxShuffleGeometry.Motion.FULL_DECK,
                GdxAppearanceOptions.shuffleMotion(settings));
        settings.setProperty(choice.key(), "full");
        org.junit.jupiter.api.Assertions.assertEquals(GdxShuffleGeometry.Motion.MID_BRIDGE,
                GdxAppearanceOptions.shuffleMotion(settings));
        for (int i = 1; i <= 2; i++) {
            GdxAppearanceOptions.select(choice, settings, i);
            var motion = GdxAppearanceOptions.shuffleMotion(settings);
            var wave = GdxShuffleAudio.arrange(source, format, motion);
            int start = i == 1 ? 920 : 0;
            org.junit.jupiter.api.Assertions.assertEquals((i == 1 ? 2640 : 1720) * 2, wave.length);
            for (int frame = 0; frame < wave.length / 2; frame++) {
                org.junit.jupiter.api.Assertions.assertEquals(frame >= start && frame < start + 1040 ? 79 : 0,
                        wave[frame * 2]);
            }
        }
    }

    @Test
    void readyArtworkArmsTheSharedClockForBothRecordedReleases()
            throws Exception {
        String table = source();
        String update = method(table, "private void updateLiveShuffle()",
                "private static float wavDurationSeconds(");
        int upload = update.indexOf("active.faces(0)");
        int clock = update.indexOf("active.startPresentation(System.nanoTime())");
        int audio = update.indexOf("startShuffleSound(active.animationEnabled ? riffleTracks.get(active.motion) : shuffleSound, phase)");
        assertTrue(upload >= 0 && clock > upload && audio > clock,
                "frame zero, shared epoch and WAV must arm in that order");
        assertTrue(update.contains("active.elapsedSeconds(renderFrameNanos)"));
        assertTrue(method(table, "private void drawLiveShuffleOverlay()",
                "private void drawTextOnlyShuffleOverlay(").contains(
                        "active.elapsedSeconds(renderFrameNanos)"));
        assertFalse(method(table, "private void acceptLiveShuffle(",
                "private void updateLiveShuffle()").contains(
                        "startShuffleSound("),
                "accepting START must not race audio against resource preparation");
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
