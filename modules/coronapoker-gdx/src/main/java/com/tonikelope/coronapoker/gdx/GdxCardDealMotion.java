/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/** A low throw, a soft touchdown and a final slide with continuous deceleration. */
final class GdxCardDealMotion {
    static final float TOUCHDOWN = 0.78f;

    private GdxCardDealMotion() { }

    /** Starts on the first rendered frame, with one consistent time for all passes. */
    static final class FrameClock {
        private long firstFrameNanos = Long.MIN_VALUE;
        private float elapsed;

        void advance(long nowNanos) {
            if (firstFrameNanos == Long.MIN_VALUE) firstFrameNanos = nowNanos;
            elapsed = Math.max(elapsed, Math.max(0L, nowNanos - firstFrameNanos)
                    / 1_000_000_000f);
        }

        float elapsedSeconds() { return elapsed; }
    }

    static float travel(float progress) {
        float p = MathUtils.clamp(progress, 0f, 1f);
        return p * (2f - p);
    }

    static float airborne(float progress) {
        return MathUtils.clamp(progress / TOUCHDOWN, 0f, 1f);
    }

    static void sample(Vector3 out, Vector3 from, Vector3 to, float progress, float width) {
        float p = MathUtils.clamp(progress, 0f, 1f);
        float t = travel(p);
        float q = airborne(p);
        float distance = Vector3.dst(from.x, 0f, from.z, to.x, 0f, to.z);
        float arch = Math.min(width * 0.55f, distance * 0.065f);
        float pulse = (float) Math.sin(Math.PI * q);
        out.set(MathUtils.lerp(from.x, to.x, t),
                to.y + (from.y - to.y) * (1f - q) * (1f - q) + arch * pulse * pulse,
                MathUtils.lerp(from.z, to.z, t));
    }
}
