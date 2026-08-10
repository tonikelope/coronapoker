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

/**
 * JLabel that plays an animated GIF via the AWT image-observer callback, with
 * hardware-accelerated stretch scaling, optional frame-synced audio, an optional
 * pre-decoded frame override (used for smooth catch-up during card-spin
 * animations), and a {@link CyclicBarrier} rendezvous fired when the GIF (and
 * its repeats) finishes.
 *
 * @see <a href="https://stackoverflow.com/a/42079313">Base painting technique source</a>
 */
public class GifLabel extends JLabel {

    /** Seconds to wait for the GIF-completion barrier's rendezvous before giving up. */
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

    // Pre-decoded frame supplied by TablePanel.showCentralFrames (the card-spin
    // catch-up engine). While non-null it takes priority over the icon and is
    // painted stretched to the bounds, like any other GIF frame.
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

    /**
     * Like {@link #setIcon(Icon)}, additionally recording the GIF's total frame
     * count so playback force-finishes at {@code frames} even if the image
     * observer never reports {@code ALLBITS}/{@code ABORT}.
     */
    public void setIcon(Icon icon, int frames) {
        this.frames = frames;
        setIcon(icon);
    }

    /**
     * Installs the barrier used to signal that this GIF (and its repeats) has
     * finished playing.
     *
     * @param barrier new barrier, or {@code null} to stop signaling completion
     */
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

    /** @return the barrier installed via {@link #setBarrier}, or {@code null}. */
    public CyclicBarrier getGif_barrier() {
        return gif_barrier;
    }

    /** Sets how many times the GIF should loop before finishing; ignored when {@code r} is less than 1. */
    public void setRepeat(int r) {
        if (r >= 1) {
            conta_repeat = 0;
            repeat = r;
        }
    }

    /** {@link #addAudio(String, int, int, Runnable)} without a frame-start callback. */
    public void addAudio(String aud, int start_frame, int end_frame) {
        addAudio(aud, start_frame, end_frame, null);
    }

    /**
     * Schedules a sound effect to play from {@code start_frame} to {@code end_frame},
     * and/or a callback fired once on that same start frame — e.g. to launch a flying
     * chip into the pot in sync with the chip sound of a cinematic action GIF.
     * {@code aud} may be {@code null} when only the frame-synced callback is needed
     * (sound disabled, but the associated gesture must still fire).
     *
     * @param aud sound resource name, or {@code null} for no sound
     * @param start_frame frame at which playback/callback starts (must be {@code > 0})
     * @param end_frame frame at which playback stops, or negative to play to the end
     * @param on_audio_start callback fired once on {@code start_frame}, or {@code null}
     */
    public void addAudio(String aud, int start_frame, int end_frame, Runnable on_audio_start) {
        if (!audio_playing && (aud != null || on_audio_start != null) && (start_frame < end_frame || end_frame < 0) && start_frame > 0) {
            this.audio = aud;
            this.audio_frame_start = start_frame;
            this.audio_frame_end = end_frame;
            this.audio_on_start = on_audio_start;
        }
    }

    /**
     * Paints {@code frame} instead of the current icon, stretched to the label's
     * bounds, until cleared with {@code null}. Triggers a repaint.
     */
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

        // Offscreen GIF: a hidden label paints nothing, so skip the per-frame EDT repaint it would
        // otherwise enqueue while it plays out its remaining frames/repeats. Frame/repeat counting
        // and the completion barrier below are untouched (return value unchanged), so a re-shown or
        // awaited GIF behaves exactly as before. (isVisible() is a plain flag read, safe off-EDT.)
        if (isVisible()) {
            repaint();
        }

        if ((infoflags & FRAMEBITS) != 0) {

            conta_frames++;

            if (audio != null || audio_on_start != null) {
                if (!audio_playing && conta_frames == audio_frame_start) {
                    // There's only end-of-audio state to manage (stopping at audio_frame_end)
                    // when there's actual audio; with audio null, only the callback fires.
                    if (audio != null && audio_frame_end > 0) {
                        audio_playing = true;
                    }
                    if (audio != null) {
                        Audio.playWavResource(audio);
                    }
                    // Visual effect synced to the audio start (fires once).
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
