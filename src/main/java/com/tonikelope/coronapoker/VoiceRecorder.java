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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
     * How the capture ended. The manager turns it into the on-screen warning
     * when a note the user committed to comes back empty.
     */
    public enum Outcome {
        // start()
        RECORDING, ABORTED, NO_LINE, BUSY,
        // stop()
        OK, EMPTY, SILENT, BROKEN, ENCODE_ERROR
    }

    public static final int MAX_SECONDS = 15;
    // Tail grace after releasing the key: the last syllable is still in the
    // air (and in the capture buffer) at that instant.
    public static final int TAIL_MILLIS = 250;
    // Safety floor only (empty/dead captures): intentional-tap filtering is
    // done by the manager on the key HOLD time, so short notes survive.
    public static final int MIN_MILLIS = 100;
    public static final float SAMPLE_RATE = 16000f;

    public static final AudioFormat PCM_FORMAT = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, 16, 1, 2, SAMPLE_RATE, false);
    private static final AudioFormat ULAW_FORMAT = new AudioFormat(AudioFormat.Encoding.ULAW, SAMPLE_RATE, 8, 1, 1, SAMPLE_RATE, false);
    private static final int MAX_PCM_BYTES = Math.round(SAMPLE_RATE) * 2 * MAX_SECONDS;
    private static final int MIN_PCM_BYTES = Math.round(SAMPLE_RATE * 2 * MIN_MILLIS / 1000f);

    // A capture line that dies in the middle of a note (a Bluetooth headset
    // switching profile, a USB mic re-enumerating, the device being grabbed in
    // exclusive mode) makes read() return 0 forever. That is recoverable on
    // most drivers: reopen and keep appending to the SAME note, so a hiccup
    // costs a small gap instead of the whole message.
    private static final int RESUME_ATTEMPTS = 3;
    private static final int RESUME_BACKOFF_MILLIS = 100;

    // Digital-silence floor: any live mic sits well above its own noise floor,
    // so only a muted or dead device stays under this peak amplitude.
    private static final int SILENCE_PEAK = 8;

    // There is a single microphone: a new note must not open the line while the
    // previous one is still closing it (that close would kill the fresh
    // capture). The slot is held from the open to the final close.
    private static final Semaphore CAPTURE_SLOT = new Semaphore(1, true);
    private static final int CAPTURE_SLOT_WAIT_MILLIS = 1500;

    private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
    private final CountDownLatch finished = new CountDownLatch(1);
    private volatile TargetDataLine line = null;
    private volatile boolean recording = false;
    private volatile boolean stop_requested = false;
    private volatile boolean slot_held = false;
    private volatile Outcome outcome = Outcome.ABORTED;

    /**
     * Opens the microphone and captures in a pool thread until stop() or the
     * MAX_SECONDS cap. Blocking (the device open takes 100-400ms, plus the
     * wait for the previous note to release the mic): call it off the EDT.
     *
     * on_live runs ONCE (on the capture thread) when the first real audio
     * arrives from the device: line.start() returns before the driver is
     * actually delivering samples, so this is the only honest talk-now
     * signal. The line is fully closed after every note (an open mic is
     * audible as background noise on some setups).
     *
     * on_no_data covers a line that opened but never delivered a sample, and
     * on_broken a capture that died mid-note and could not be resumed: both
     * mean nobody is recording any more, so the manager must tear the dialog
     * down instead of letting the user talk into a dead mic.
     */
    public Outcome start(Runnable on_live, Runnable on_no_data, Runnable on_broken) {

        try {
            if (!CAPTURE_SLOT.tryAcquire(CAPTURE_SLOT_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Capture line still busy with the previous voice note");
                finished.countDown();
                return finish(Outcome.BUSY);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            finished.countDown();
            return finish(Outcome.BUSY);
        }

        slot_held = true;

        try {

            line = AudioDeviceManager.getTargetDataLine(PCM_FORMAT);

            line.open(PCM_FORMAT);

            line.start();

        } catch (Exception ex) {
            Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Cannot open capture line: {0}", ex.getMessage());
            closeLine();
            releaseSlot();
            finished.countDown();
            return finish(Outcome.NO_LINE);
        }

        if (stop_requested) {
            // Released before the line was ready: nothing worth keeping
            closeLine();
            releaseSlot();
            finished.countDown();
            return finish(Outcome.ABORTED);
        }

        recording = true;

        outcome = Outcome.RECORDING;

        try {

            Helpers.threadRun(() -> capture(on_live, on_no_data, on_broken));

        } catch (Exception ex) {
            Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Cannot start the capture thread: {0}", ex.getMessage());
            recording = false;
            closeLine();
            releaseSlot();
            finished.countDown();
            return finish(Outcome.NO_LINE);
        }

        return Outcome.RECORDING;
    }

    private void capture(Runnable on_live, Runnable on_no_data, Runnable on_broken) {

        // 50ms chunks: quick first-data signal and a short stop latency
        byte[] buffer = new byte[1600];

        boolean live = false;
        boolean broken = false;
        int resumes = 0;

        try {

            while (recording && pcm.size() < MAX_PCM_BYTES) {

                TargetDataLine l = line;

                int n = l != null ? l.read(buffer, 0, Math.min(buffer.length, MAX_PCM_BYTES - pcm.size())) : 0;

                if (n <= 0) {

                    // read() only returns short when the line has been stopped,
                    // flushed or closed underneath us. If we did not ask for it,
                    // the device died: reopening is worth a try before giving up
                    // on a note the user believes is being recorded.
                    if (stop_requested || !recording) {
                        break;
                    }

                    if (resumes >= RESUME_ATTEMPTS || !reopenLine()) {
                        // Only a mic lost while the user is still talking is a
                        // broken note: if they released during the reopen, what
                        // was captured before the hiccup is theirs to send.
                        broken = recording && !stop_requested;
                        break;
                    }

                    resumes++;

                    Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Capture line died mid-note, resumed ({0}/{1}) after {2} ms of audio",
                            new Object[]{resumes, RESUME_ATTEMPTS, capturedMillis()});

                    continue;
                }

                if (!live) {
                    live = true;
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

            if (!broken) {

                TargetDataLine l = line;

                // Tail flush: stop capturing and pull what the hardware had
                // already buffered. Closing right away discarded it and
                // clipped the end of the recording.
                try {
                    if (l != null) {
                        l.stop();
                    }
                } catch (Exception ex) {
                }

                while (l != null && pcm.size() < MAX_PCM_BYTES && l.available() > 0) {

                    int n = l.read(buffer, 0, Math.min(Math.min(buffer.length, l.available()), MAX_PCM_BYTES - pcm.size()));

                    if (n <= 0) {
                        break;
                    }

                    pcm.write(buffer, 0, n);
                }
            }

        } catch (Exception ex) {
            Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Capture error: {0}", ex.getMessage());
            broken = true;
        } finally {

            recording = false;
            closeLine();
            releaseSlot();

            if (broken) {
                outcome = Outcome.BROKEN;
            }

            finished.countDown();

            // Nobody is capturing any more and nobody asked to stop: the
            // manager still shows the talk-now dialog and the global recording
            // silence is up, so it has to be told. Either the device never
            // delivered a single sample (catatonic mic, no on_live ever fired)
            // or it died mid-note and would not come back.
            Runnable dead = broken ? on_broken : (!live ? on_no_data : null);

            if (dead != null && !stop_requested) {

                if (broken) {
                    Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Capture line lost after {0} ms of audio, note discarded", capturedMillis());
                }

                try {
                    dead.run();
                } catch (Exception ex) {
                    Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Dead capture callback error: {0}", ex.getMessage());
                }
            }
        }
    }

    /**
     * Reopens the capture line after a driver-side death. Called only from the
     * capture thread, which already owns the mic slot.
     */
    private boolean reopenLine() {

        closeLine();

        line = null;

        Helpers.parkThreadMillis(RESUME_BACKOFF_MILLIS);

        if (stop_requested || !recording) {
            return false;
        }

        try {

            TargetDataLine l = AudioDeviceManager.getTargetDataLine(PCM_FORMAT);

            l.open(PCM_FORMAT);

            l.start();

            line = l;

            return true;

        } catch (Exception ex) {
            Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Cannot reopen the capture line: {0}", ex.getMessage());
            return false;
        }
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

        // A capture that died mid-note is never shipped: what survives is the
        // fragment recorded before the device went away (typically a burst of
        // noise), and the user has already been told the mic was lost. The
        // same goes for a mic that was never captured with: keep the original
        // outcome, there is nothing to encode.
        if (outcome == Outcome.BROKEN || outcome == Outcome.BUSY || outcome == Outcome.NO_LINE) {
            return null;
        }

        if (pcm.size() < MIN_PCM_BYTES) {
            finish(Outcome.EMPTY);
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

    // The mic slot is released exactly once, whichever path ends the capture
    private synchronized void releaseSlot() {

        if (slot_held) {
            slot_held = false;
            CAPTURE_SLOT.release();
        }
    }

}
