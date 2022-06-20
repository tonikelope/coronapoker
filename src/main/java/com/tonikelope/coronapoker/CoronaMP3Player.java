/*
 * Copyright (C) 2020 tonikelope
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

public class CoronaMP3Player {

    private volatile SourceDataLine line;
    private volatile boolean playing = false;
    private volatile boolean paused = false;
    private final Object pause_lock = new Object();

    public boolean isPlaying() {
        return playing;
    }

    public void play(String filePath, float vol) {

        final File file = new File(filePath);
        try {
            play(getAudioInputStream(file), vol);
        } catch (UnsupportedAudioFileException | IOException ex) {
            Logger.getLogger(CoronaMP3Player.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void play(AudioInputStream is, float vol) {

        try (final AudioInputStream in = is) {

            final AudioFormat outFormat = getOutFormat(in.getFormat());

            final Info info = new Info(SourceDataLine.class, outFormat);

            line = (SourceDataLine) AudioSystem.getLine(info);

            if (line != null) {

                line.open(outFormat);
                line.getControl(FloatControl.Type.MASTER_GAIN);
                setVolume(vol);
                line.start();

                playing = true;

                stream(getAudioInputStream(outFormat, in), line);

                if (playing) {
                    playing = false;
                    line.drain();
                    line.stop();

                }

                line.close();

            }

        } catch (Exception ex) {
            Logger.getLogger(CoronaMP3Player.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void stop() {

        try {
            playing = false;
            line.drain();
            line.stop();
        } catch (Exception ex) {
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

        FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);

        if (vol == 0f) {
            gainControl.setValue(gainControl.getMinimum());
        } else {
            float db = Helpers.floatClean(20f * (float) Math.log10(vol), 2);
            gainControl.setValue(db >= gainControl.getMinimum() ? db : gainControl.getMinimum());
        }
    }

    private AudioFormat getOutFormat(AudioFormat inFormat) {
        final int ch = inFormat.getChannels();
        final float rate = inFormat.getSampleRate();
        return new AudioFormat(PCM_SIGNED, rate, 16, ch, ch * 2, rate, false);
    }

    private void stream(AudioInputStream in, SourceDataLine line)
            throws IOException {
        final byte[] buffer = new byte[65536];
        for (int n = 0; playing && n != -1; n = in.read(buffer, 0, buffer.length)) {

            while (paused) {
                synchronized (pause_lock) {
                    try {
                        pause_lock.wait(1000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(CoronaMP3Player.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }

            if (playing) {
                line.write(buffer, 0, n);
            }
        }
    }
}
