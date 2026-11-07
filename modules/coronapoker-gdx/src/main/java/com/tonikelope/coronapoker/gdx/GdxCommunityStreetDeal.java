/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;

/** Presentation timeline for one authorised community reveal. */
record GdxCommunityStreetDeal(float appearsAt, float flightSeconds,
        float flipSeconds, float spreadSeconds) {

    private static final float FLOP_STACK_HOLD_SECONDS = 0.08f;
    private static final float FLOP_SPREAD_SECONDS = 0.52f;
    // Leave room for the spread inside the dealer's four-second barrier,
    // including its two-second all-in lead-in and the renderer's end beat.
    private static final float MAX_FLOP_FLIP_SECONDS = 1f;
    private static final float MAX_FLOP_SPREAD_SECONDS = 0.70f;

    static GdxCommunityStreetDeal flop(float leadIn, float flightSeconds,
            float flipSeconds, int speedPercent) {
        return new GdxCommunityStreetDeal(
                Math.max(0f, leadIn - flightSeconds), flightSeconds,
                Math.min(flipSeconds, MAX_FLOP_FLIP_SECONDS),
                Math.min(FLOP_SPREAD_SECONDS * speedPercent / 100f,
                        MAX_FLOP_SPREAD_SECONDS));
    }

    static GdxCommunityStreetDeal single(float leadIn, float flightSeconds,
            float flipSeconds) {
        // Use the dealer's lead-in for the flight when there is time for it.
        // The flip starts only after landing and the prescribed lead-in.
        return new GdxCommunityStreetDeal(
                Math.max(0f, leadIn - flightSeconds), flightSeconds,
                flipSeconds, 0f);
    }

    boolean flop() {
        return spreadSeconds > 0f;
    }

    boolean visible(float elapsed) {
        return elapsed >= appearsAt;
    }

    boolean inFlight(float elapsed) {
        return visible(elapsed) && elapsed < landing();
    }

    float flightProgress(float elapsed) {
        return progress(elapsed, appearsAt, flightSeconds);
    }

    float flipProgress(float elapsed) {
        return progress(elapsed, landing(), flipSeconds);
    }

    boolean flopFaceUp(float elapsed) {
        return elapsed >= landing() + flipSeconds;
    }

    float revealProgress(float elapsed) {
        return flop() ? progress(elapsed, spreadStart(), spreadSeconds)
                : flipProgress(elapsed);
    }

    float flopOffsetX(int slot, float gap, float elapsed) {
        return -slot * gap * (1f
                - Interpolation.smooth.apply(revealProgress(elapsed)));
    }

    float revealEnd() {
        return flop() ? spreadStart() + spreadSeconds : landing() + flipSeconds;
    }

    private float spreadStart() {
        return landing() + flipSeconds + FLOP_STACK_HOLD_SECONDS;
    }

    private float landing() {
        return appearsAt + flightSeconds;
    }

    private static float progress(float elapsed, float start, float duration) {
        if (duration == 0f) return elapsed >= start ? 1f : 0f;
        return MathUtils.clamp((elapsed - start) / duration, 0f, 1f);
    }
}
