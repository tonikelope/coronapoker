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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * State and painting of the table's "lights off" overlay. Painted by the table
 * at the end of its {@code paint()}, and also by the GIF panel (a separate
 * window that darkens on its own). Previously the overlay was the
 * {@code LayerUI} of a {@code JLayer} wrapping the whole table, but a
 * {@code JLayer} forces every repaint of any of its components to start there,
 * even with the lights on, which is 99% of the game.
 */
public class BrightnessOverlay {

    // The overlay has TWO owners, reconciled only here:
    //
    // 1) The player's switch (table button and shortcut): a preference that persists until they
    //    change it.
    // 2) TEMPORARY blackouts imposed by the game: pause, and dialogs that run with the table dark
    //    (game over, recover, initial buy-in). These are COUNTED since they can overlap: each one
    //    increments on entry and decrements on exit, and the overlay stays up while any is alive.
    //
    // Previously there was no such separation: each call site saved the current brightness,
    // forced it, and restored it on exit, so two overlapping callers would clobber each other
    // (e.g. a pause starting while game-over was open could leave the table LIT during the pause,
    // with the switch showing the opposite of what was on screen).
    private volatile boolean user_lights_off = false;
    private final AtomicInteger forced_lights_off = new AtomicInteger(0);
    // Effective brightness actually painted, derived from the two fields above and never set
    // directly. Everything that reads/writes it today runs on the EDT (the dealer touches the
    // overlay from inside its GUIRun calls), but it's kept volatile with synchronized recompute
    // so a call from another thread can't leave it permanently stale.
    private volatile float brightness = 0f;
    // Overlay color, recreated only when brightness changes. Shared by every surface that
    // darkens, all of which paint at the same brightness and from the EDT.
    private Color cached_color = null;
    private float cached_brightness = -1f;

    /**
     * Black-overlay opacity for the configured light level: its complement (50%
     * light -&gt; 0.50 overlay). Clamped to the setting's range in case the
     * config value was hand-edited outside it.
     */
    private static float lightsOffBrightness() {

        return (100 - Math.max(GameFrame.NIVEL_LUZ_MIN, Math.min(GameFrame.NIVEL_LUZ, GameFrame.NIVEL_LUZ_MAX))) / 100f;
    }

    /**
     * Player-driven switch.
     */
    public void lightsOFF() {

        user_lights_off = true;
        refreshBrightness();
    }

    public void lightsON() {

        user_lights_off = false;
        refreshBrightness();
    }

    /**
     * What the player asked for, REGARDLESS of any temporary blackout the game
     * may currently be forcing: this is what decides whether their next click
     * turns lights on or off.
     */
    public boolean isUserLightsOff() {
        return user_lights_off;
    }

    /**
     * Temporary blackout requested by the game. Always call in pairs
     * (push/pop), preferably with the pop in a {@code finally}: a leaked push
     * leaves the table dark forever.
     */
    public void pushForcedLightsOFF() {

        forced_lights_off.incrementAndGet();
        refreshBrightness();
    }

    public void popForcedLightsOFF() {

        // Never go below zero: an extra pop (e.g. an error path decrementing twice) must not
        // leave the counter negative, which would make the next push fail to light the overlay.
        forced_lights_off.updateAndGet(pending -> pending > 0 ? pending - 1 : 0);
        refreshBrightness();
    }

    /**
     * Recomputes the effective brightness. Public because changing the light
     * level in Settings must be reflected in an overlay that's already up.
     */
    public synchronized void refreshBrightness() {

        brightness = (user_lights_off || forced_lights_off.get() > 0) ? BrightnessOverlay.lightsOffBrightness() : 0f;
    }

    public float getBrightness() {
        return brightness;
    }

    /**
     * Paints the overlay over the given surface; no-op with the lights on. Must
     * be called AFTER painting content (from {@code paint()}, not
     * {@code paintComponent()}) so it ends up on top.
     */
    public void paintOverlay(Graphics g, int width, int height) {

        float b = getBrightness();

        if (b > 0f) {
            if (cached_color == null || cached_brightness != b) {
                cached_color = new Color(0f, 0f, 0f, b);
                cached_brightness = b;
            }
            Graphics2D g2d = (Graphics2D) g.create();
            try {
                g2d.setColor(cached_color);
                g2d.fillRect(0, 0, width, height);
            } finally {
                g2d.dispose();
            }
        }
    }

}
