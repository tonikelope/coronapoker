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
        OK, EMPTY, SILENT, NO_DATA, BROKEN, ENCODE_ERROR
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
    // costs a gap instead of the whole message. The endpoint stays down for a
    // second or more on a Bluetooth profile switch, hence the growing waits:
    // the budget is spent on FAILED opens, which is what actually happens
    // while the device is away.
    private static final int[] RESUME_BACKOFF_MILLIS = {100, 300, 700, 1500};
    // Cap for the other shape of a sick device: the line reopens fine over and
    // over but never delivers a sample again.
    private static final int MAX_RESUMES = 3;

    // Below this, a note salvaged from a capture that died is not worth
    // sending: it is the burst of noise the device produced on its way out.
    private static final int RESCUE_MIN_MILLIS = 1000;

    // Digital-silence floor: any live mic sits well above its own noise floor,
    // so only a muted or dead device stays under this peak amplitude.
    private static final int SILENCE_PEAK = 8;

    // There is a single microphone: a new note must not open the line while the
    // previous one is still closing it (that close would kill the fresh
    // capture). The slot is held from the open to the final close, and the
    // wait covers the worst case teardown of the previous note (tail grace plus
    // the safety timeouts in stop()) so back to back notes never see a false
    // busy.
    private static final Semaphore CAPTURE_SLOT = new Semaphore(1, true);
    private static final int CAPTURE_SLOT_WAIT_MILLIS = 4000;

    private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
    private final CountDownLatch finished = new CountDownLatch(1);
    private volatile TargetDataLine line = null;
    private volatile boolean recording = false;
    private volatile boolean stop_requested = false;
    private volatile boolean slot_held = false;
    private volatile boolean capture_lost = false;
    private volatile boolean got_audio = false;
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
     * on_ended runs when the capture stops on its own (the device never
     * delivered a sample, or it died mid-note beyond recovery) while nobody
     * asked it to: the manager has to tear the dialog down instead of letting
     * the user talk into a dead mic. It never runs when stop() was called,
     * because then the manager is already handling the end of the note.
     */
    public Outcome start(Runnable on_live, Runnable on_ended) {

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

            Helpers.threadRun(() -> capture(on_live, on_ended));

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

    private void capture(Runnable on_live, Runnable on_ended) {

        // 50ms chunks: quick first-data signal and a short stop latency
        byte[] buffer = new byte[1600];

        int resumes = 0;

        try {

            try {

                while (recording && pcm.size() < MAX_PCM_BYTES) {

                    TargetDataLine l = line;

                    int n = l != null ? l.read(buffer, 0, alignFrames(Math.min(buffer.length, MAX_PCM_BYTES - pcm.size()))) : 0;

                    if (n <= 0) {

                        // read() only returns short when the line has been
                        // stopped, flushed or closed underneath us. If we did
                        // not ask for it, the device died.
                        if (stop_requested || !recording) {
                            break;
                        }

                        // A line that never delivered a single sample was not
                        // lost, it was never alive: reopening it is pointless
                        // churn and the honest answer is that the mic is not
                        // working.
                        if (!got_audio) {
                            Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Capture line opened but delivered no audio at all");
                            break;
                        }

                        if (resumes >= MAX_RESUMES || !reopenLine()) {
                            capture_lost = true;
                            break;
                        }

                        resumes++;

                        Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Capture line died mid-note, resumed ({0}/{1}) after {2} ms of audio",
                                new Object[]{resumes, MAX_RESUMES, capturedMillis()});

                        continue;
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
                capture_lost = true;
            }

            // Tail flush in its own guard: it runs AFTER the audio is already
            // in the buffer, so a driver that throws here (an odd available(),
            // a line closed by the stop() safety net) must never cost the note.
            try {
                tailFlush(buffer);
            } catch (Exception ex) {
                Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Tail flush error: {0}", ex.getMessage());
            }

        } finally {

            recording = false;
            closeLine();
            releaseSlot();

            finished.countDown();

            // Nobody is capturing any more and nobody asked to stop: the
            // manager still shows the talk-now dialog and the global recording
            // silence is up, so it has to be told. It decides what to do with
            // whatever was captured; stop() tells it why the note ended.
            if (on_ended != null && !stop_requested) {

                Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Capture ended on its own after {0} ms of audio (lost: {1})",
                        new Object[]{capturedMillis(), capture_lost});

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
     * Closing right away discarded it and clipped the end of the recording.
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
     * Reopens the capture line after a driver-side death, waiting longer on
     * each failed attempt: a Bluetooth profile switch keeps the endpoint away
     * for a second or more. Called only from the capture thread, which already
     * owns the mic slot.
     */
    private boolean reopenLine() {

        closeLine();

        line = null;

        long gap_start = System.nanoTime();

        for (int attempt = 0; attempt < RESUME_BACKOFF_MILLIS.length; attempt++) {

            Helpers.parkThreadMillis(RESUME_BACKOFF_MILLIS[attempt]);

            if (stop_requested || !recording) {
                return false;
            }

            TargetDataLine l = null;

            try {

                // No falling back to another device here: the note would carry
                // on through a different microphone without the user knowing.
                l = AudioDeviceManager.getTargetDataLine(PCM_FORMAT, false);

                // Assigned before opening so a failure half way through is
                // still closed by closeLine() instead of leaking the device
                line = l;

                l.open(PCM_FORMAT);

                l.start();

                padGap(gap_start);

                return true;

            } catch (Exception ex) {

                Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Cannot reopen the capture line (attempt {0}/{1}): {2}",
                        new Object[]{attempt + 1, RESUME_BACKOFF_MILLIS.length, ex.getMessage()});

                closeLine();

                line = null;
            }
        }

        return false;
    }

    /**
     * Fills the reopen gap with silence so the note keeps lasting what the
     * user held the key: splicing the two halves sample to sample swallowed a
     * syllable and left a click at the joint.
     */
    private void padGap(long gap_start_nanos) {

        int bytes = alignFrames((int) Math.min((System.nanoTime() - gap_start_nanos) / 1000000L * (long) (SAMPLE_RATE * 2) / 1000L,
                Math.max(0, MAX_PCM_BYTES - pcm.size())));

        if (bytes > 0) {
            pcm.write(new byte[bytes], 0, bytes);
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
                if (!finished.await(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    // The capture thread is wedged inside the driver and will
                    // never release the mic on its own. Handing the slot back
                    // is safer than locking voice notes out for the rest of the
                    // session: releaseSlot() is idempotent, so the thread
                    // waking up later is harmless.
                    Logger.getLogger(VoiceRecorder.class.getName()).log(Level.SEVERE, "Capture thread wedged, releasing the microphone by force");
                    releaseSlot();
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        // Nothing was ever captured with this mic: keep the original outcome,
        // there is nothing to encode.
        if (outcome == Outcome.BUSY || outcome == Outcome.NO_LINE) {
            return null;
        }

        // A line that opened but never delivered a sample is a mic that is not
        // working, not a note that failed
        if (!got_audio) {
            finish(Outcome.NO_DATA);
            return null;
        }

        if (pcm.size() < MIN_PCM_BYTES) {
            finish(Outcome.EMPTY);
            return null;
        }

        // The device died mid-note. What survives is worth sending as long as
        // it is a real chunk of speech: throwing away ten good seconds because
        // the mic went away at the end is worse than a note that ends abruptly.
        // Only the short burst of noise a device produces on its way out gets
        // dropped, and then the user is told.
        if (capture_lost && capturedMillis() < RESCUE_MIN_MILLIS) {
            Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Capture lost with only {0} ms of audio, note discarded", capturedMillis());
            finish(Outcome.BROKEN);
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

                if (capture_lost) {
                    Logger.getLogger(VoiceRecorder.class.getName()).log(Level.WARNING, "Note rescued from a lost capture line: {0} ms of audio", capturedMillis());
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
