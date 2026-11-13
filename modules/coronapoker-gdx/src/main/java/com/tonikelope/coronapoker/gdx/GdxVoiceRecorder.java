/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.tonikelope.coronapoker.core.audio.VoiceWavContract;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
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
    private static final int ONSET_RMS = 48;
    private static final int ONSET_BLOCKS = 3;
    private static final int LEADING_CONTEXT_BLOCKS = 10;
    private static final int CAPTURE_TARGET_PEAK = 29_490;
    private static final float MAX_CAPTURE_GAIN = 4f;

    @FunctionalInterface
    interface LineProvider {
        TargetDataLine open(AudioFormat format) throws Exception;
    }

    private final LineProvider lineProvider;
    private final boolean automaticGain;
    private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
    private final CountDownLatch finished = new CountDownLatch(1);
    private volatile TargetDataLine line;
    private volatile boolean recording;
    private volatile boolean stopRequested;
    private volatile boolean gotAudio;
    private volatile Outcome outcome = Outcome.ABORTED;

    GdxVoiceRecorder() {
        this(format -> AudioSystem.getTargetDataLine(format), true);
    }

    GdxVoiceRecorder(Properties properties) {
        this(format -> GdxAudioDevices.openCapture(properties, format),
                automaticGainEnabled(properties));
    }

    GdxVoiceRecorder(LineProvider lineProvider) {
        this(lineProvider, true);
    }

    GdxVoiceRecorder(LineProvider lineProvider, boolean automaticGain) {
        this.lineProvider = Objects.requireNonNull(lineProvider, "lineProvider");
        this.automaticGain = automaticGain;
    }

    /** Opens the device synchronously; callers must invoke it off the render thread. */
    Outcome start(Runnable onLive, Runnable onEnded) {
        if (stopRequested) {
            finished.countDown();
            return Outcome.ABORTED;
        }
        TargetDataLine opened = null;
        try {
            opened = lineProvider.open(PCM_FORMAT);
            opened.open(PCM_FORMAT);
            opened.start();
        } catch (Exception unavailable) {
            closeDevice(opened);
            finished.countDown();
            return finish(Outcome.NO_LINE);
        }
        boolean accepted;
        synchronized (this) {
            accepted = !stopRequested;
            if (accepted) {
                line = opened;
                recording = true;
                outcome = Outcome.RECORDING;
            }
        }
        if (!accepted) {
            closeDevice(opened);
            finished.countDown();
            return finish(Outcome.ABORTED);
        }
        TargetDataLine device = opened;
        Thread capture = new Thread(() -> capture(device, onLive, onEnded),
                "coronapoker-gdx-voice-capture");
        capture.setDaemon(true);
        capture.start();
        return Outcome.RECORDING;
    }

    private void capture(TargetDataLine device, Runnable onLive, Runnable onEnded) {
        byte[] buffer = new byte[1600];
        try {
            while (recording && pcm.size() < MAX_PCM_BYTES) {
                int wanted = alignFrames(Math.min(buffer.length,
                        MAX_PCM_BYTES - pcm.size()));
                if (wanted <= 0) break;
                int count = device.read(buffer, 0, wanted);
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
        closeLine();
        try {
            if (!finished.await(500, TimeUnit.MILLISECONDS)) {
                return failed(Outcome.LOST);
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
        bytes = trimLeadingSilence(bytes);
        try {
            byte[] encoded = encodePcm(automaticGain
                    ? applySafeCaptureGain(bytes) : bytes);
            finish(Outcome.OK);
            return encoded;
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
        TargetDataLine current = markAborted();
        closeDevice(current);
        finished.countDown();
    }

    void requestAbort() {
        TargetDataLine current = markAborted();
        CompletableFuture.runAsync(() -> {
            closeDevice(current);
            finished.countDown();
        });
    }

    private synchronized TargetDataLine markAborted() {
        stopRequested = true;
        recording = false;
        finish(Outcome.ABORTED);
        return takeLine();
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
        closeDevice(takeLine());
    }

    private synchronized TargetDataLine takeLine() {
        TargetDataLine current = line;
        line = null;
        return current;
    }

    private static void closeDevice(TargetDataLine current) {
        if (current == null) return;
        try { current.stop(); } catch (RuntimeException ignored) { }
        try { current.flush(); } catch (RuntimeException ignored) { }
        try { current.close(); } catch (RuntimeException ignored) { }
    }

    private static int alignFrames(int bytes) {
        return bytes - bytes % PCM_FORMAT.getFrameSize();
    }

    /**
     * Skip long stretches of near-silence while retaining the start of speech.
     * An isolated device click does not count as an onset.
     */
    static byte[] trimLeadingSilence(byte[] pcm) {
        // Ten milliseconds per block, with 100 ms of audio before the onset.
        int blockBytes = (int) SAMPLE_RATE * PCM_FORMAT.getFrameSize() / 100;
        long onsetEnergy = (long) ONSET_RMS * ONSET_RMS * (blockBytes / 2);
        int consecutive = 0;
        int trim = 0;
        for (int offset = 0; offset + blockBytes <= pcm.length; offset += blockBytes) {
            long energy = 0;
            for (int i = offset; i < offset + blockBytes; i += 2) {
                int sample = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
                energy += (long) sample * sample;
            }
            if (energy >= onsetEnergy) {
                if (++consecutive == ONSET_BLOCKS) {
                    int precedingBlocks = LEADING_CONTEXT_BLOCKS + ONSET_BLOCKS - 1;
                    trim = Math.max(0, offset - precedingBlocks * blockBytes);
                    break;
                }
            } else {
                consecutive = 0;
            }
        }
        if (trim < LEADING_CONTEXT_BLOCKS * blockBytes
                || pcm.length - trim < MIN_PCM_BYTES) return pcm;
        return Arrays.copyOfRange(pcm, trim, pcm.length);
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

    /**
     * Raises quiet captures without touching the operating-system microphone
     * level. The peak target leaves ten percent headroom and the +12 dB cap
     * avoids turning normal microphone noise into full-scale audio.
     */
    static byte[] applySafeCaptureGain(byte[] pcmBytes) {
        Objects.requireNonNull(pcmBytes, "pcmBytes");
        if (pcmBytes.length % PCM_FORMAT.getFrameSize() != 0) {
            throw new IllegalArgumentException("Unaligned PCM voice payload");
        }
        int peak = peakAmplitude(pcmBytes);
        if (peak <= 0 || peak >= CAPTURE_TARGET_PEAK) {
            return Arrays.copyOf(pcmBytes, pcmBytes.length);
        }
        float gain = Math.min(MAX_CAPTURE_GAIN,
                CAPTURE_TARGET_PEAK / (float) peak);
        byte[] boosted = new byte[pcmBytes.length];
        for (int index = 0; index + 1 < pcmBytes.length; index += 2) {
            short source = (short) ((pcmBytes[index] & 0xff)
                    | (pcmBytes[index + 1] << 8));
            int sample = Math.round(source * gain);
            sample = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE,
                    sample));
            boosted[index] = (byte) sample;
            boosted[index + 1] = (byte) (sample >>> 8);
        }
        return boosted;
    }

    static boolean automaticGainEnabled(Properties properties) {
        return properties == null || Boolean.parseBoolean(properties.getProperty(
                GdxSettingsContract.AUDIO_MIC_AUTO_GAIN_KEY, "true"));
    }
}
