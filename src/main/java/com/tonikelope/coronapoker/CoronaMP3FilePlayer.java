/*
 * Copyright (C) 2020 tonikelope
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
 */
package com.tonikelope.coronapoker;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import static javax.sound.sampled.AudioSystem.getAudioInputStream;
import static javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED;
import javax.sound.sampled.FloatControl;

public class CoronaMP3FilePlayer {

    private volatile SourceDataLine line = null;
    private volatile boolean playing = false;
    private volatile boolean paused = false;
    private volatile boolean stopped = false;
    private final Object pause_lock = new Object();

    public boolean isPlaying() {
        return playing;
    }

    public boolean isPaused() {
        return paused;
    }

    public void play(String path, float volume) {
        try {
            play(getAudioInputStream(new File(path)), volume);
        } catch (UnsupportedAudioFileException | IOException ex) {
            // Log as warning only, as it's a file issue, not necessarily a crash
            Logger.getLogger(CoronaMP3FilePlayer.class.getName()).log(Level.WARNING, "Cannot play file {0}: {1}", new Object[]{path, ex.getMessage()});
        }
    }

    public void play(AudioInputStream is, float volume) {

        try (final AudioInputStream in = is) {

            // stop() may win the race before playback begins (the old "if
            // (playing)" guard lost that stop and the whole track played as a
            // zombie). A stopped player can never start again.
            if (stopped) {
                return;
            }

            final AudioFormat outFormat = getOutFormat(in.getFormat());

            // This is where the flood happens if the audio device is busy or missing
            line = AudioDeviceManager.getSourceDataLine(outFormat);

            if (line != null) {
                try {
                    line.open(outFormat);
                    setVolume(volume);
                    line.start();
                    playing = true;

                    stream(getAudioInputStream(outFormat, in), line);

                    if (playing) {
                        playing = false;
                        line.drain();
                        line.stop();
                    }

                    paused = false;
                } finally {
                    try {
                        line.close();
                    } catch (Exception ignored) {
                    }
                }
            }

        } catch (LineUnavailableException | IllegalArgumentException ex) {
            // SILENCE THE FLOOD: Handle hardware/line issues with low priority logging
            if (Audio.AUDIO_AVAILABLE) {
                Logger.getLogger(CoronaMP3FilePlayer.class.getName()).log(Level.FINE, "Audio line unavailable: {0}", ex.getMessage());
            }
        } catch (Exception ex) {
            // Only log generic exceptions as severe if they have a message
            if (ex.getMessage() != null) {
                Logger.getLogger(CoronaMP3FilePlayer.class.getName()).log(Level.SEVERE, "Unexpected player error: {0}", ex.getMessage());
            }
        } finally {
            playing = false;
        }
    }

    public void stop() {

        stopped = true;

        if (playing) {
            try {
                playing = false;
                if (line != null) {
                    line.drain();
                    line.stop();
                }
            } catch (Exception ex) {
                // Silently ignore stop errors
            }
        }
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
        synchronized (pause_lock) {
            pause_lock.notifyAll();
        }
    }

    public void setVolume(float vol) {
        if (line != null && line.isOpen()) {
            try {
                FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);

                if (vol == 0f) {
                    gainControl.setValue(gainControl.getMinimum());
                } else {
                    float db = Helpers.floatClean(20f * (float) Math.log10(vol), 3);
                    gainControl.setValue(db >= gainControl.getMinimum() ? db : gainControl.getMinimum());
                }
            } catch (Exception ex) {
                // Some lines don't support volume control
            }
        }
    }

    private AudioFormat getOutFormat(AudioFormat inFormat) {
        final int ch = inFormat.getChannels();
        final float rate = inFormat.getSampleRate();
        return new AudioFormat(PCM_SIGNED, rate, 16, ch, ch * 2, rate, false);
    }

    private void stream(AudioInputStream in, SourceDataLine line) throws IOException {
        final byte[] buffer = new byte[65536];
        int n = -1;

        while (line.isOpen() && playing && !stopped && !Thread.currentThread().isInterrupted() && (n = in.read(buffer)) != -1) {
            while (paused && line.isOpen() && playing && !stopped && !Thread.currentThread().isInterrupted()) {
                synchronized (pause_lock) {
                    try {
                        pause_lock.wait(1000);
                    } catch (InterruptedException ex) {
                        // Pool shutdown during paused playback. Restoring the flag plus
                        // the !isInterrupted() guard in both while conditions exits the
                        // inner and outer loops without spinning.
                        Thread.currentThread().interrupt();
                    }
                }
            }

            if (line.isOpen() && playing && !stopped) {
                line.write(buffer, 0, n);
            }
        }
    }
}
