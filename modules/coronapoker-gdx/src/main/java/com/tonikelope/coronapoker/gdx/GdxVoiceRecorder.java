/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.audio.VoiceWavContract;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;

/**
 * Desktop microphone capture for the GDX frontend. It deliberately emits the
 * same 16 kHz mono G.711 mu-law WAV contract used by the Swing frontend.
 */
final class GdxVoiceRecorder {

    enum Outcome { RECORDING, OK, ABORTED, NO_LINE, EMPTY, SILENT, LOST, ENCODE_ERROR }

    static final int MAX_SECONDS = 15;
    static final int MIN_MILLIS = 100;
    static final int TAIL_MILLIS = 250;
    static final float SAMPLE_RATE = 16_000f;
    static final AudioFormat PCM_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, 16, 1, 2,
            SAMPLE_RATE, false);
    private static final AudioFormat ULAW_FORMAT = new AudioFormat(
            AudioFormat.Encoding.ULAW, SAMPLE_RATE, 8, 1, 1,
            SAMPLE_RATE, false);
    private static final int MAX_PCM_BYTES = (int) SAMPLE_RATE * 2 * MAX_SECONDS;
    private static final int MIN_PCM_BYTES = (int) SAMPLE_RATE * 2 * MIN_MILLIS / 1000;
    private static final int SILENCE_PEAK = 8;

    @FunctionalInterface
    interface LineProvider {
        TargetDataLine open(AudioFormat format) throws Exception;
    }

    private final LineProvider lineProvider;
    private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
    private final CountDownLatch finished = new CountDownLatch(1);
    private volatile TargetDataLine line;
    private volatile boolean recording;
    private volatile boolean stopRequested;
    private volatile boolean gotAudio;
    private volatile Outcome outcome = Outcome.ABORTED;

    GdxVoiceRecorder() {
        this(format -> AudioSystem.getTargetDataLine(format));
    }

    GdxVoiceRecorder(Properties properties) {
        this(format -> GdxAudioDevices.openCapture(properties, format));
    }

    GdxVoiceRecorder(LineProvider lineProvider) {
        this.lineProvider = Objects.requireNonNull(lineProvider, "lineProvider");
    }

    /** Opens the device synchronously; callers must invoke it off the render thread. */
    Outcome start(Runnable onLive, Runnable onEnded) {
        try {
            line = lineProvider.open(PCM_FORMAT);
            line.open(PCM_FORMAT);
            line.start();
        } catch (Exception unavailable) {
            closeLine();
            finished.countDown();
            return finish(Outcome.NO_LINE);
        }
        if (stopRequested) {
            closeLine();
            finished.countDown();
            return finish(Outcome.ABORTED);
        }
        recording = true;
        outcome = Outcome.RECORDING;
        Thread capture = new Thread(() -> capture(onLive, onEnded),
                "coronapoker-gdx-voice-capture");
        capture.setDaemon(true);
        capture.start();
        return Outcome.RECORDING;
    }

    private void capture(Runnable onLive, Runnable onEnded) {
        byte[] buffer = new byte[1600];
        try {
            while (recording && pcm.size() < MAX_PCM_BYTES) {
                int wanted = alignFrames(Math.min(buffer.length,
                        MAX_PCM_BYTES - pcm.size()));
                if (wanted <= 0) break;
                int count = line.read(buffer, 0, wanted);
                if (count <= 0) break;
                if (!gotAudio) {
                    gotAudio = true;
                    if (onLive != null) onLive.run();
                }
                pcm.write(buffer, 0, count);
            }
            flushTail(buffer);
        } catch (RuntimeException ignored) {
            // stopAndEncode reports LOST/EMPTY from the captured state.
        } finally {
            recording = false;
            closeLine();
            finished.countDown();
            if (!stopRequested && onEnded != null) onEnded.run();
        }
    }

    byte[] stopAndEncode() {
        stopRequested = true;
        try {
            Thread.sleep(TAIL_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            abort();
            return null;
        }
        recording = false;
        try {
            if (!finished.await(2, TimeUnit.SECONDS)) {
                closeLine();
                if (!finished.await(1, TimeUnit.SECONDS)) {
                    return failed(Outcome.LOST);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            abort();
            return null;
        }
        if (!gotAudio || pcm.size() < MIN_PCM_BYTES) {
            return failed(Outcome.EMPTY);
        }
        byte[] bytes = pcm.toByteArray();
        if (peakAmplitude(bytes) <= SILENCE_PEAK) {
            return failed(Outcome.SILENT);
        }
        try {
            return encodePcm(bytes);
        } catch (Exception encodingFailure) {
            return failed(Outcome.ENCODE_ERROR);
        }
    }

    static byte[] encodePcm(byte[] pcmBytes) throws Exception {
        Objects.requireNonNull(pcmBytes, "pcmBytes");
        if (pcmBytes.length < MIN_PCM_BYTES || pcmBytes.length > MAX_PCM_BYTES
                || pcmBytes.length % PCM_FORMAT.getFrameSize() != 0) {
            throw new IllegalArgumentException("PCM voice payload outside contract");
        }
        try (AudioInputStream pcmStream = new AudioInputStream(
                new ByteArrayInputStream(pcmBytes), PCM_FORMAT,
                pcmBytes.length / PCM_FORMAT.getFrameSize());
                AudioInputStream ulaw = AudioSystem.getAudioInputStream(
                        ULAW_FORMAT, pcmStream);
                ByteArrayOutputStream wav = new ByteArrayOutputStream()) {
            AudioSystem.write(ulaw, AudioFileFormat.Type.WAVE, wav);
            byte[] encoded = wav.toByteArray();
            String invalid = VoiceWavContract.validationError(encoded);
            if (invalid != null) {
                throw new IllegalStateException("Invalid encoded voice note: "
                        + invalid);
            }
            return encoded;
        }
    }

    long capturedMillis() {
        return pcm.size() * 1000L / (long) (SAMPLE_RATE * 2);
    }

    Outcome outcome() {
        return outcome;
    }

    void abort() {
        stopRequested = true;
        recording = false;
        finish(Outcome.ABORTED);
        closeLine();
        finished.countDown();
    }

    private byte[] failed(Outcome result) {
        finish(result);
        return null;
    }

    private Outcome finish(Outcome result) {
        outcome = result;
        return result;
    }

    private void flushTail(byte[] buffer) {
        TargetDataLine current = line;
        if (current == null) return;
        try {
            current.stop();
            int available;
            while (pcm.size() < MAX_PCM_BYTES
                    && (available = alignFrames(Math.min(current.available(),
                            buffer.length))) > 0) {
                int count = current.read(buffer, 0, alignFrames(Math.min(
                        available, MAX_PCM_BYTES - pcm.size())));
                if (count <= 0) break;
                pcm.write(buffer, 0, count);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void closeLine() {
        TargetDataLine current = line;
        line = null;
        if (current == null) return;
        try { current.stop(); } catch (RuntimeException ignored) { }
        try { current.flush(); } catch (RuntimeException ignored) { }
        try { current.close(); } catch (RuntimeException ignored) { }
    }

    private static int alignFrames(int bytes) {
        return bytes - bytes % PCM_FORMAT.getFrameSize();
    }

    private static int peakAmplitude(byte[] bytes) {
        int peak = 0;
        for (int index = 0; index + 1 < bytes.length; index += 2) {
            int sample = Math.abs((short) ((bytes[index] & 0xff)
                    | (bytes[index + 1] << 8)));
            peak = Math.max(peak, sample);
        }
        return peak;
    }
}
