/*
 * Copyright (C) 2026 tonikelope
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

import java.util.function.DoubleConsumer;

/**
 * Numeric counter that rolls toward a target value, used for the game's live
 * counters (player stack / player pot / main pot).
 *
 * Fire-and-forget (never blocks a thread) and coalescing: if the target changes
 * mid-roll, the current leg is recalculated from the value currently shown
 * toward the new target, without falling behind or jumping. Two modes:
 * <ul>
 * <li>constant SPEED (3-arg constructor below): leg duration = distance /
 * speed, clamped to [min_ms, max_ms].</li>
 * <li>constant TIME ({@link #RollingCounter(DoubleConsumer, long)}): every leg
 * takes the same fixed duration regardless of distance, so several counters
 * started together finish together (e.g. all-in probabilities).</li>
 * </ul>
 *
 * EDT-only: roll/set/invalidate and the Timer tick all run on the Event
 * Dispatch Thread, so the fields need no synchronization. The actual painting
 * is done by the {@code render} callback (writes the label's text for a given
 * value); this class only drives the interpolation.
 */
public class RollingCounter {

    private final DoubleConsumer render;

    private double speed;   // value units per second
    private long min_ms;
    private long max_ms;
    private long fixed_ms;  // >0 => fixed duration per leg (constant-time mode); ignores speed/min/max

    private double shown;          // value currently painted
    private boolean shown_valid;   // false when the label shows non-numeric text ("----", "see buy-in"...)
    private double last_rendered;  // last value handed to render (dedupe: skip identical re-renders)
    private boolean has_rendered;  // false until the first render.accept
    private double target;
    private double leg_from;
    private long leg_start_ms;
    private long leg_dur_ms;

    private javax.swing.Timer timer;

    public RollingCounter(DoubleConsumer render, double speed, long min_ms, long max_ms) {
        this.render = render;
        this.speed = speed;
        this.min_ms = min_ms;
        this.max_ms = max_ms;
        this.shown_valid = false;
    }

    /**
     * Constant-time variant; see the class Javadoc.
     */
    public RollingCounter(DoubleConsumer render, long fixed_ms) {
        this.render = render;
        this.fixed_ms = fixed_ms;
        this.shown_valid = false;
    }

    /**
     * Rolls toward {@code value}. Jumps instantly if {@code animate} is false
     * or the displayed value isn't valid (came from a non-numeric state).
     * EDT-only.
     */
    public void roll(double value, boolean animate) {
        value = Helpers.doubleClean(value);

        if (!animate || !shown_valid) {
            set(value);
            return;
        }

        // Already heading straight to this target: don't restart the leg.
        if (timer != null && timer.isRunning() && Helpers.doubleSecureCompare(value, target) == 0) {
            return;
        }

        double dist = Math.abs(value - shown);
        if (Helpers.doubleSecureCompare(0f, dist) == 0) {
            set(value);
            return;
        }

        this.leg_from = shown;
        this.target = value;
        this.leg_start_ms = System.currentTimeMillis();
        this.leg_dur_ms = fixed_ms > 0
                ? fixed_ms
                : Math.max(min_ms, Math.min(max_ms, Math.round(dist / speed * 1000.0)));

        if (timer == null) {
            // Same fixed tick as the rest of the game's animations (GameFrame.getTickMs,
            // 2 ms): interpolation is TIME-based (p = elapsed/leg_dur_ms), so a finer tick
            // only smooths the animation — it doesn't speed it up or change leg duration.
            timer = new javax.swing.Timer(GameFrame.getTickMs(), (e) -> tick());
        }
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    private void tick() {
        long elapsed = System.currentTimeMillis() - leg_start_ms;
        double p = leg_dur_ms <= 0 ? 1.0 : Math.min(1.0, elapsed / (double) leg_dur_ms);

        shown = leg_from + (target - leg_from) * p;

        boolean last = p >= 1.0;
        if (last) {
            shown = target;
            timer.stop();
        }

        double v = Helpers.doubleClean(shown);
        // Dedupe: render formats v into the label (and, for the fitted pot/all-in labels,
        // re-measures the font). At the 2 ms tick most interpolation steps are sub-quantum, so v is
        // identical to what's already shown — re-rendering would produce byte-identical output. Skip
        // those. EXCEPTION: always render the FINAL tick of a leg. Some render callbacks wrap the
        // number in a prefix/suffix that is NOT a function of v (pot tag, all-in "%"); if that
        // wrapper changed while the value stayed put, the terminal render guarantees it still reaches
        // the label at least once per leg. Otherwise zero visual change; removes the bulk of the
        // ~500 Hz format/measure work.
        if (!last && has_rendered && Helpers.doubleSecureCompare(v, last_rendered) == 0) {
            return;
        }
        last_rendered = v;
        has_rendered = true;
        render.accept(v);
    }

    /**
     * Sets {@code value} immediately (no animation) and marks it valid.
     * EDT-only. Used by resets/recover and by the counting overlay (which
     * already animates frame by frame on its own).
     */
    public void set(double value) {
        if (timer != null) {
            timer.stop();
        }
        value = Helpers.doubleClean(value);
        this.shown = value;
        this.target = value;
        this.shown_valid = true;
        this.last_rendered = value;
        this.has_rendered = true;
        render.accept(value);
    }

    /**
     * Marks the label as now showing non-numeric text (painted by the caller).
     * The next roll() will jump instead of animating from a value that no
     * longer applies. EDT-only.
     */
    public void invalidate() {
        if (timer != null) {
            timer.stop();
        }
        this.shown_valid = false;
    }

    /**
     * True if the displayed value is numeric and valid (i.e. can be animated
     * FROM). False after invalidate() or before the first set/roll. EDT-only.
     */
    public boolean isValid() {
        return shown_valid;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public void setBounds(long min_ms, long max_ms) {
        this.min_ms = min_ms;
        this.max_ms = max_ms;
    }
}
