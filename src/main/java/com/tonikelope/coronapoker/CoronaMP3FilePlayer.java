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

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine.Info;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import static javax.sound.sampled.AudioSystem.getAudioInputStream;
import static javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED;
import javax.sound.sampled.FloatControl;

//Thanks to -> https://odoepner.wordpress.com/2013/07/19/play-mp3-or-ogg-using-javax-sound-sampled-mp3spi-vorbisspi/
public class CoronaMP3FilePlayer {

    private volatile SourceDataLine line = null;
    private volatile boolean playing = false;
    private volatile boolean paused = false;
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
            Logger.getLogger(CoronaMP3FilePlayer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void play(AudioInputStream is, float volume) {

        try (final AudioInputStream in = is) {

            final AudioFormat outFormat = getOutFormat(in.getFormat());

            final Info info = new Info(SourceDataLine.class, outFormat);

            line = (SourceDataLine) AudioSystem.getLine(info);

            if (line != null) {

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

                line.close();
                paused = false;
            }

        } catch (Exception ex) {
            Logger.getLogger(CoronaMP3FilePlayer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void stop() {

        if (playing) {
            try {
                playing = false;
                line.drain();
                line.stop();

            } catch (Exception ex) {
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
            FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);

            if (vol == 0f) {
                gainControl.setValue(gainControl.getMinimum());
            } else {
                float db = Helpers.floatClean(20f * (float) Math.log10(vol), 3);
                gainControl.setValue(db >= gainControl.getMinimum() ? db : gainControl.getMinimum());
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

        while (line.isOpen() && playing && (n = in.read(buffer)) != -1) {

            while (paused && line.isOpen() && playing) {
                synchronized (pause_lock) {
                    try {
                        pause_lock.wait(1000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(CoronaMP3FilePlayer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }

            if (line.isOpen() && playing) {
                line.write(buffer, 0, n);
            }
        }
    }
}
