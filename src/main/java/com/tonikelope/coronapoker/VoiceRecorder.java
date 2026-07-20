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

    /**
     * How the recording ended. The manager turns it into the on-screen
     * warning, so that a note that produces nothing never fails in silence.
     */
    public enum Outcome {
        // start()
        RECORDING, ABORTED, NO_LINE, BUSY,
        // stop()
        OK, EMPTY, SILENT, NO_DATA, LOST, ENCODE_ERROR
    }

    public static final int MAX_SECONDS = 15;
    // Tail grace after releasing the key: the last syllable is still in the
    // air (and in the capture buffer) at that instant.
    public static final int TAIL_MILLIS = 250;
    // Safety floor only (empty/dead captures): intentional-tap filtering is
    // done by the manager, so short notes survive.
    public static final int MIN_MILLIS = 100;
    public static final float SAMPLE_RATE = 16000f;

    public static final AudioFormat PCM_FORMAT = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, 16, 1, 2, SAMPLE_RATE, false);
    private static final AudioFormat ULAW_FORMAT = new AudioFormat(AudioFormat.Encoding.ULAW, SAMPLE_RATE, 8, 1, 1, SAMPLE_RATE, false);
    private static final int MAX_PCM_BYTES = Math.round(SAMPLE_RATE) * 2 * MAX_SECONDS;
    private static final int MIN_PCM_BYTES = Math.round(SAMPLE_RATE * 2 * MIN_MILLIS / 1000f);

    // Digital-silence floor: any live mic sits well above its own noise floor,
    // so only a muted or dead device stays under this peak amplitude. It is
    // -72 dBFS measured on the PCM before the u-law encoding, which is already
    // inaudible on its own and lands on the first quantization steps of the
    // codec, so a note rejected here had nothing to hear in it.
    private static final int SILENCE_PEAK = 8;

    // Only used to tell apart the two reasons an open can fail: a note started
    // right after the previous one finds the mic still held for the tail grace,
    // and blaming the settings for that is a lie. It gates nothing.
    private static final java.util.concurrent.atomic.AtomicInteger OPEN_LINES = new java.util.concurrent.atomic.AtomicInteger(0);

    private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
    private final CountDownLatch finished = new CountDownLatch(1);
    private volatile boolean line_counted = false;
    private volatile TargetDataLine line = null;
    private volatile boolean recording = false;
    private volatile boolean stop_requested = false;
    private volatile boolean got_audio = false;
    private volatile boolean device_ended = false;
    private volatile Outcome outcome = Outcome.ABORTED;

    /**
     * Opens the microphone and captures in a pool thread until stop() or the
     * MAX_SECONDS cap. Blocking (the device open takes 100-400ms): call it
     * off the EDT.
     *
     * on_live runs ONCE (on the capture thread) when the first real audio
     * arrives from the device: line.start() returns before the driver is
     * actually delivering samples, so this is the only honest talk-now
     * signal. The line is fully closed after every note (an open mic is
     * audible as background noise on some setups).
     *
     * on_ended runs when the capture stops on its own while nobody asked it
     * to: the manager has to tear the dialog down instead of leaving the user
     * talking into a mic that is no longer recording.
     */
    public Outcome start(Runnable on_live, Runnable on_ended) {

        try {

            line = AudioDeviceManager.getTargetDataLine(PCM_FORMAT);

            line.open(PCM_FORMAT);

            line.start();

            countLineOpen();

        } catch (Exception ex) {

            // The previous note still holds the device for its tail grace: that
            // is a mic to wait for, not a mic to configure.
            boolean busy = OPEN_LINES.get() > 0;

            Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Cannot open capture line ({0}): {1}",
                    new Object[]{busy ? "still busy with the previous note" : "no usable device", ex.getMessage()});

            closeLine();
            finished.countDown();
            return finish(busy ? Outcome.BUSY : Outcome.NO_LINE);
        }

        if (stop_requested) {
            // Released before the line was ready: nothing worth keeping
            closeLine();
            finished.countDown();
            return finish(Outcome.ABORTED);
        }

        recording = true;

        outcome = Outcome.RECORDING;

        try {

            Helpers.threadRun(() -> capture(on_live, on_ended));

        } catch (Exception ex) {
            Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Cannot start the capture thread: {0}", ex.getMessage());
            recording = false;
            closeLine();
            finished.countDown();
            return finish(Outcome.NO_LINE);
        }

        return Outcome.RECORDING;
    }

    private void capture(Runnable on_live, Runnable on_ended) {

        // 50ms chunks: quick first-data signal and a short stop latency
        byte[] buffer = new byte[1600];

        try {

            try {

                while (recording && pcm.size() < MAX_PCM_BYTES) {

                    int len = alignFrames(Math.min(buffer.length, MAX_PCM_BYTES - pcm.size()));

                    if (len == 0) {
                        // Cap reached down to the last frame: a full note, not a
                        // device that went away
                        break;
                    }

                    int n = line.read(buffer, 0, len);

                    if (n <= 0) {

                        // read() only returns short when the line has been
                        // stopped, flushed or closed underneath us. If we did
                        // not ask for it, the device took the capture away and
                        // this note is over, whatever the dialog still says.
                        if (!stop_requested && recording) {
                            device_ended = true;
                            Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Capture line stopped delivering after {0} ms of audio", capturedMillis());
                        }

                        break;
                    }

                    if (!got_audio) {
                        got_audio = true;
                        if (on_live != null) {
                            try {
                                on_live.run();
                            } catch (Exception ex) {
                                Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "on_live callback error: {0}", ex.getMessage());
                            }
                        }
                    }

                    pcm.write(buffer, 0, n);
                }

            } catch (Exception ex) {
                Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Capture error: {0}", ex.getMessage());
                device_ended = !stop_requested && recording;
            }

            // Tail flush in its own guard: it runs AFTER the audio is already
            // in the buffer, so a driver that throws here must never cost the
            // note that was captured perfectly.
            try {
                tailFlush(buffer);
            } catch (Exception ex) {
                Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Tail flush error: {0}", ex.getMessage());
            }

        } finally {

            recording = false;
            closeLine();

            finished.countDown();

            // Nobody is capturing any more and nobody asked to stop: the
            // manager still shows the talk-now dialog and the global recording
            // silence is up, so it has to be told. Whatever was captured is
            // still its to send; stop() reports why the note ended.
            if (on_ended != null && !stop_requested) {

                try {
                    on_ended.run();
                } catch (Exception ex) {
                    Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Capture ended callback error: {0}", ex.getMessage());
                }
            }
        }
    }

    /**
     * Stops the line and pulls whatever the hardware had already buffered.
     * Most of the tail is actually delivered by the grace stop() waits before
     * lowering the flag, but a provider that does keep buffered frames after
     * stop() gets them picked up here instead of closed away.
     */
    private void tailFlush(byte[] buffer) {

        TargetDataLine l = line;

        if (l == null) {
            return;
        }

        try {
            l.stop();
        } catch (Exception ex) {
        }

        int available;

        while (pcm.size() < MAX_PCM_BYTES && (available = alignFrames(Math.min(l.available(), buffer.length))) > 0) {

            int n = l.read(buffer, 0, alignFrames(Math.min(available, MAX_PCM_BYTES - pcm.size())));

            if (n <= 0) {
                break;
            }

            pcm.write(buffer, 0, n);
        }
    }

    /**
     * read() demands a whole number of frames and throws otherwise, while
     * available() is free to report an odd byte count.
     */
    private static int alignFrames(int bytes) {
        return bytes - (bytes % PCM_FORMAT.getFrameSize());
    }

    /**
     * Why the last note ended. Meaningful once start() has returned something
     * other than RECORDING, or once stop() has returned.
     */
    public Outcome getOutcome() {
        return outcome;
    }

    private Outcome finish(Outcome result) {
        outcome = result;
        return result;
    }

    /**
     * Audio actually captured so far, in milliseconds.
     */
    public long getCapturedMillis() {
        return capturedMillis();
    }

    private long capturedMillis() {
        return pcm.size() * 1000L / (long) (SAMPLE_RATE * 2);
    }

    /**
     * Stops the capture and returns the recording as a u-law WAV, or null if
     * nothing usable was captured (see getOutcome()).
     */
    public byte[] stop() {

        stop_requested = true;

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

        // The mic was never captured with: keep the original outcome, there is
        // nothing to encode.
        if (outcome == Outcome.NO_LINE || outcome == Outcome.BUSY) {
            return null;
        }

        // A line that opened but never delivered a single sample is a mic that
        // is not working, not a note that failed
        if (!got_audio) {
            finish(device_ended ? Outcome.NO_DATA : Outcome.EMPTY);
            return null;
        }

        if (pcm.size() < MIN_PCM_BYTES) {
            // The device went away before there was anything worth keeping
            finish(device_ended ? Outcome.LOST : Outcome.EMPTY);
            return null;
        }

        byte[] pcm_bytes = pcm.toByteArray();

        // A device that keeps delivering after being muted at OS level (or one
        // whose stream is dead but still clocking) hands over a full-length
        // note of digital zeros. Shipping that is worse than saying nothing
        // was captured.
        if (peakAmplitude(pcm_bytes) <= SILENCE_PEAK) {
            Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Silent capture ({0} ms): microphone muted or dead", capturedMillis());
            finish(Outcome.SILENT);
            return null;
        }

        try {

            AudioInputStream pcm_stream = new AudioInputStream(new ByteArrayInputStream(pcm_bytes), PCM_FORMAT, pcm_bytes.length / PCM_FORMAT.getFrameSize());

            try (AudioInputStream ulaw_stream = AudioSystem.getAudioInputStream(ULAW_FORMAT, pcm_stream)) {

                ByteArrayOutputStream wav = new ByteArrayOutputStream();

                AudioSystem.write(ulaw_stream, AudioFileFormat.Type.WAVE, wav);

                if (device_ended) {
                    // Sent, but cut short by the device: worth knowing when the
                    // author reports a note shorter than what they said
                    Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Note cut short by the capture device: {0} ms of audio", capturedMillis());
                }

                finish(Outcome.OK);

                return wav.toByteArray();
            }

        } catch (Exception ex) {
            Logger.getLogger(VoiceRecorder.class.getName()).log(Level.SEVERE, "Voice message encoding failed: {0}", ex.getMessage());
            finish(Outcome.ENCODE_ERROR);
            return null;
        }
    }

    // Loudest sample of the note (16 bit little endian mono)
    private static int peakAmplitude(byte[] pcm_bytes) {

        int peak = 0;

        for (int i = 0; i + 1 < pcm_bytes.length; i += 2) {

            int sample = Math.abs((short) ((pcm_bytes[i] & 0xFF) | (pcm_bytes[i + 1] << 8)));

            if (sample > peak) {
                peak = sample;
            }
        }

        return peak;
    }

    private synchronized void countLineOpen() {

        if (!line_counted) {
            line_counted = true;
            OPEN_LINES.incrementAndGet();
        }
    }

    private synchronized void countLineClosed() {

        if (line_counted) {
            line_counted = false;
            OPEN_LINES.decrementAndGet();
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

        countLineClosed();
    }

}
