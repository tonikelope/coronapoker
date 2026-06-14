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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;

//Thanks to -> https://stackoverflow.com/a/42079313
public class GifLabel extends JLabel {

    public final static long GIF_BARRIER_TIMEOUT = 5;

    private volatile int frames = 0;
    private volatile int conta_frames = 0;
    private volatile int repeat = 1;
    private volatile int conta_repeat = 0;
    private volatile String audio = null;
    private volatile int audio_frame_start = -1;
    private volatile int audio_frame_end = -1;
    private volatile boolean gif_finished = false;
    private volatile CyclicBarrier gif_barrier = null;
    private volatile boolean audio_playing = false;
    private volatile Runnable audio_on_start = null;

    // Frame pre-decodificado servido por TablePanel.showCentralFrames (motor
    // con catch-up de los giros de carta). Mientras no es null tiene prioridad
    // sobre el icono y se pinta estirado a los bounds como el resto de GIFs.
    private volatile BufferedImage frame_override = null;

    public GifLabel() {
    }

    @Override
    public void setIcon(Icon icon) {
        gif_finished = false;
        conta_frames = 0;
        conta_repeat = 0;
        repeat = 1;
        audio = null;
        audio_playing = false;
        frame_override = null;

        // Toolkit.getImage(URL) caches Images by URL for the entire JVM lifetime
        // and the GIF's internal frame counter survives across dialog instances.
        // Flushing resets the Image so the animation restarts from frame 0 on each setIcon.
        if (icon instanceof ImageIcon) {
            Image img = ((ImageIcon) icon).getImage();
            if (img != null) {
                img.flush();
            }
        }

        super.setIcon(icon);
    }

    public void setIcon(Icon icon, int frames) {
        this.frames = frames;
        setIcon(icon);
    }

    public void setBarrier(CyclicBarrier barrier) {

        CyclicBarrier previous = gif_barrier;

        gif_barrier = barrier;

        // A superseding notify reuses this shared label: break the previous
        // rendezvous at once so its waiters (the notify thread, the finished-GIF
        // callback and any action cinematic awaiting it) cancel cooperatively
        // instead of each parking until GIF_BARRIER_TIMEOUT. The happy path never
        // installs a second barrier on the same label, so it never resets here.
        if (previous != null && previous != barrier) {
            previous.reset();
        }
    }

    public CyclicBarrier getGif_barrier() {
        return gif_barrier;
    }

    public void setRepeat(int r) {
        if (r >= 1) {
            conta_repeat = 0;
            repeat = r;
        }
    }

    public void addAudio(String aud, int start_frame, int end_frame) {
        addAudio(aud, start_frame, end_frame, null);
    }

    // Variante con callback que se ejecuta en el MISMO frame en que arranca el
    // audio (audio_frame_start), para sincronizar un efecto visual con el sonido
    // (p.ej. lanzar la ficha voladora al bote cuando suena el chip de un GIF de
    // acción en modo cinemática). El callback se dispara una sola vez.
    public void addAudio(String aud, int start_frame, int end_frame, Runnable on_audio_start) {
        if (!audio_playing && aud != null && (start_frame < end_frame || end_frame < 0) && start_frame > 0) {
            this.audio = aud;
            this.audio_frame_start = start_frame;
            this.audio_frame_end = end_frame;
            this.audio_on_start = on_audio_start;
        }
    }

    public void setFrameOverride(BufferedImage frame) {
        this.frame_override = frame;
        repaint();
    }

    // Hardware-accelerated dynamic scaling.
    // Instead of scaling the image pixel by pixel in CPU, we stretch it dynamically on the GPU.
    @Override
    protected void paintComponent(Graphics g) {
        BufferedImage override = frame_override;
        if (override != null) {
            Graphics2D g2d = (Graphics2D) g;
            if (override.getWidth() != getWidth() || override.getHeight() != getHeight()) {
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            }
            g2d.drawImage(override, 0, 0, getWidth(), getHeight(), null);
            return;
        }
        if (getIcon() != null && getIcon() instanceof ImageIcon) {
            Image img = ((ImageIcon) getIcon()).getImage();
            if (img != null) {
                // Draw original image stretching it to this JLabel bounds
                g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
            }
        } else {
            super.paintComponent(g);
        }
    }

    @Override
    public boolean imageUpdate(Image img, int infoflags, int x, int y, int w, int h) {

        if (gif_finished) {
            return false; // Cut the ImageObserver loop if GIF has already finished
        }

        repaint();

        if ((infoflags & FRAMEBITS) != 0) {

            conta_frames++;

            if (audio != null) {
                if (!audio_playing && conta_frames == audio_frame_start) {
                    if (audio_frame_end > 0) {
                        audio_playing = true;
                    }
                    Audio.playWavResource(audio);
                    // Efecto visual sincronizado con el arranque del audio (una vez).
                    if (audio_on_start != null) {
                        Runnable r = audio_on_start;
                        audio_on_start = null;
                        r.run();
                    }
                } else if (audio_playing && conta_frames == audio_frame_end) {
                    audio_playing = false;
                    Audio.stopWavResource(audio);
                    audio = null;
                }
            }

        }

        boolean imageupdate = ((infoflags & (ALLBITS | ABORT)) == 0);

        gif_finished = !imageupdate || (frames != 0 && conta_frames == frames);

        if (gif_finished) {

            conta_repeat++;

            if (conta_repeat < repeat) {
                gif_finished = false;
                conta_frames = 0;
            }
        }

        if (gif_finished && gif_barrier != null) {
            Helpers.threadRun(() -> {
                try {
                    gif_barrier.await(GIF_BARRIER_TIMEOUT, TimeUnit.SECONDS);
                } catch (InterruptedException | java.util.concurrent.BrokenBarrierException | java.util.concurrent.TimeoutException ex) {
                    Helpers.logCooperativeCancellation(Logger.getLogger(GifLabel.class.getName()),
                            "GIF label barrier", ex);
                } catch (Exception ex) {
                    Logger.getLogger(GifLabel.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
        }

        // Stop Swing from requesting more frames if the animation is done and not repeating
        return !gif_finished && imageupdate;
    }

}
