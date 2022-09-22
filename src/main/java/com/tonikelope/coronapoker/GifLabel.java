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

import java.awt.Image;
import java.awt.Toolkit;
import java.util.concurrent.CyclicBarrier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.JLabel;

//Thanks to -> https://stackoverflow.com/a/42079313
public class GifLabel extends JLabel {

    private volatile int frames = 0;
    private volatile int conta_frames = 0;
    private volatile String audio = null;
    private volatile int audio_frame_start = -1;
    private volatile int audio_frame_end = -1;
    private volatile boolean gif_finished = false;
    private volatile CyclicBarrier gif_barrier = null;
    private volatile boolean audio_playing = false;
    private volatile boolean fsync = false;

    public void setFsync(boolean fsync) {
        this.fsync = fsync;
    }
    
    @Override
    public void setIcon(Icon icon) {
        gif_finished = false;
        conta_frames = 0;
        audio = null;
        audio_playing = false;
        super.setIcon(icon);
    }

    public void setIcon(Icon icon, int frames) {
        this.frames = frames;
        setIcon(icon);
    }

    public void setBarrier(CyclicBarrier barrier) {
        gif_barrier = barrier;
    }

    public void addAudio(String aud, int start_frame, int end_frame) {
        if (!audio_playing && aud != null && start_frame < end_frame && start_frame > 0) {
            this.audio = aud;
            this.audio_frame_start = start_frame;
            this.audio_frame_end = end_frame;
        }
    }

    @Override
    public boolean imageUpdate(Image img, int infoflags, int x, int y, int w, int h) {

        repaint();
        
        if (!gif_finished) {

            if ((infoflags & FRAMEBITS) != 0) {
                
                if(fsync && conta_frames>0){
                    Toolkit.getDefaultToolkit().sync();
                }

                conta_frames++;
                
                if (audio != null) {
                    if (!audio_playing && conta_frames == audio_frame_start) {
                        audio_playing = true;
                        Audio.playWavResource(audio);
                    } else if (audio_playing && conta_frames == audio_frame_end) {
                        audio_playing = false;
                        Audio.stopWavResource(audio);
                        audio = null;
                    }
                }

            }

            boolean imageupdate = ((infoflags & (ALLBITS | ABORT)) == 0);

            gif_finished = !imageupdate || (frames != 0 && conta_frames == frames);

            if (gif_finished && gif_barrier != null) {

                try {
                    gif_barrier.await();
                } catch (Exception ex) {
                    Logger.getLogger(GifLabel.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            return imageupdate;

        } else {
            return false;
        }
    }

}
