/*
 * Copyright (C) 2026 tonikelope
 _              _ _        _
| |_ ___  _ __ (_) | _____| | ___  _ __   ___
| __/ _ \| '_ \| | |/ / _ \ |/ _ \| '_ \ / _ \
| || (_) | | | | |   <  __/ | (_) | |_) |  __/
 \__\___/|_| |_|_|_|\_\___|_|\___/| .__/ \___|
 ____    ___  ____    ___
|___ \  / _ \|___ \  / _ \
  __) || | | | __) || | | |
 / __/ | |_| |/ __/ | |_| |
|_____| \___/|_____| \___/

https://github.com/tonikelope/coronapoker
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;

/**
 * One-shot microphone recorder for voice messages: captures PCM 16 kHz mono
 * from the capture device selected in the audio settings and encodes the
 * result as a u-law WAV held in memory (no external codec dependencies).
 *
 * @author tonikelope
 */
public class VoiceRecorder {

    public static final int MAX_SECONDS = 15;
    // Tail grace after releasing the key: the last syllable is still in the
    // air (and in the capture buffer) at that instant.
    public static final int TAIL_MILLIS = 250;
    // Accidental-tap threshold. It includes TAIL_MILLIS of grace audio, so a
    // quick tap (~tap + tail) still falls below it.
    public static final int MIN_MILLIS = 600;
    public static final float SAMPLE_RATE = 16000f;

    private static final AudioFormat PCM_FORMAT = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, 16, 1, 2, SAMPLE_RATE, false);
    private static final AudioFormat ULAW_FORMAT = new AudioFormat(AudioFormat.Encoding.ULAW, SAMPLE_RATE, 8, 1, 1, SAMPLE_RATE, false);
    private static final int MAX_PCM_BYTES = Math.round(SAMPLE_RATE) * 2 * MAX_SECONDS;
    private static final int MIN_PCM_BYTES = Math.round(SAMPLE_RATE * 2 * MIN_MILLIS / 1000f);

    private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
    private final CountDownLatch finished = new CountDownLatch(1);
    private volatile TargetDataLine line = null;
    private volatile boolean recording = false;

    public boolean isRecording() {
        return recording;
    }

    /**
     * Opens the microphone and captures in a pool thread until stop() or the
     * MAX_SECONDS cap. Returns false if the capture line cannot be opened.
     */
    public boolean start() {

        try {

            line = AudioDeviceManager.getTargetDataLine(PCM_FORMAT);

            line.open(PCM_FORMAT);

            line.start();

        } catch (Exception ex) {
            Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Cannot open capture line: {0}", ex.getMessage());
            closeLine();
            finished.countDown();
            return false;
        }

        recording = true;

        Helpers.threadRun(() -> {

            byte[] buffer = new byte[3200];

            try {

                while (recording && pcm.size() < MAX_PCM_BYTES) {

                    int n = line.read(buffer, 0, Math.min(buffer.length, MAX_PCM_BYTES - pcm.size()));

                    if (n <= 0) {
                        break;
                    }

                    pcm.write(buffer, 0, n);
                }

                // Tail flush: stop capturing and pull what the hardware had
                // already buffered. Closing right away discarded it and
                // clipped the end of the recording.
                try {
                    line.stop();
                } catch (Exception ex) {
                }

                while (pcm.size() < MAX_PCM_BYTES && line.available() > 0) {

                    int n = line.read(buffer, 0, Math.min(Math.min(buffer.length, line.available()), MAX_PCM_BYTES - pcm.size()));

                    if (n <= 0) {
                        break;
                    }

                    pcm.write(buffer, 0, n);
                }

            } catch (Exception ex) {
                Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Capture error: {0}", ex.getMessage());
            } finally {
                recording = false;
                closeLine();
                finished.countDown();
            }
        });

        return true;
    }

    /**
     * Stops the capture and returns the recording as a u-law WAV, or null if
     * it is shorter than MIN_MILLIS (accidental key tap) or empty.
     */
    public byte[] stop() {

        // Tail grace: keep capturing briefly so the last word survives the
        // key release. The recording dialog is already gone at this point.
        Helpers.parkThreadMillis(TAIL_MILLIS);

        recording = false;

        try {
            // The reader wakes from its pending read() in <= 100ms of audio,
            // flushes the line tail and counts down. The timeout is a safety
            // net against a capture line gone catatonic.
            if (!finished.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
                closeLine();
                finished.await(1, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        if (pcm.size() < MIN_PCM_BYTES) {
            return null;
        }

        try {

            byte[] pcm_bytes = pcm.toByteArray();

            AudioInputStream pcm_stream = new AudioInputStream(new ByteArrayInputStream(pcm_bytes), PCM_FORMAT, pcm_bytes.length / PCM_FORMAT.getFrameSize());

            try (AudioInputStream ulaw_stream = AudioSystem.getAudioInputStream(ULAW_FORMAT, pcm_stream)) {

                ByteArrayOutputStream wav = new ByteArrayOutputStream();

                AudioSystem.write(ulaw_stream, AudioFileFormat.Type.WAVE, wav);

                return wav.toByteArray();
            }

        } catch (Exception ex) {
            Logger.getLogger(VoiceRecorder.class.getName()).log(Level.SEVERE, "Voice message encoding failed: {0}", ex.getMessage());
            return null;
        }
    }

    private void closeLine() {

        TargetDataLine l = line;

        if (l != null) {
            try {
                l.stop();
            } catch (Exception ex) {
            }
            try {
                l.close();
            } catch (Exception ex) {
            }
        }
    }

}
