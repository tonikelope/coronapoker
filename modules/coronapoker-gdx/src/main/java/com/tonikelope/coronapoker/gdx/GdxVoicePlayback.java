/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.io.ByteArrayInputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/** Non-blocking Java Sound playback for the shared voice-note WAV format. */
final class GdxVoicePlayback {

    static CompletableFuture<Void> play(byte[] wav) {
        return play(wav, () -> { });
    }

    static CompletableFuture<Void> play(byte[] wav, Runnable playbackStarted) {
        Runnable started = playbackStarted == null ? () -> { }
                : playbackStarted;
        return CompletableFuture.runAsync(() -> playBlocking(wav, started));
    }

    private static void playBlocking(byte[] wav, Runnable playbackStarted) {
        if (wav == null || wav.length == 0) return;
        try (AudioInputStream encoded = AudioSystem.getAudioInputStream(
                new ByteArrayInputStream(wav))) {
            AudioFormat source = encoded.getFormat();
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    source.getSampleRate(), 16, source.getChannels(),
                    source.getChannels() * 2, source.getSampleRate(), false);
            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(pcm,
                    encoded)) {
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(
                        new DataLine.Info(SourceDataLine.class, pcm));
                try {
                    line.open(pcm);
                    line.start();
                    playbackStarted.run();
                    byte[] buffer = new byte[4096];
                    for (int count; (count = decoded.read(buffer)) >= 0;) {
                        if (count > 0) line.write(buffer, 0, count);
                    }
                    line.drain();
                } finally {
                    line.stop();
                    line.close();
                }
            }
        } catch (Exception failure) {
            // Playback stays off the render thread, but callers still need to
            // distinguish a completed note from an unavailable output device.
            throw new CompletionException(failure);
        }
    }

    private GdxVoicePlayback() {
    }
}
