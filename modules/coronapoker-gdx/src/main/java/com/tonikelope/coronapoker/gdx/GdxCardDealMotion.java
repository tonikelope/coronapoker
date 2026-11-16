/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import java.util.List;

/** A low throw, a soft touchdown and a final slide with continuous deceleration. */
final class GdxCardDealMotion {
    static final float TOUCHDOWN = 0.68f;
    private static final int SWEEP_STEPS = 96;
    private static final float[] DETOURS = {.55f, -.55f, 1.05f, -1.05f, 1.55f, -1.55f};
    private static final Route DIRECT = new Route(0f, 0f, 0f, 0f, TOUCHDOWN);

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

    record Pile(float x, float z, float radius, float top) {}

    record Bounds(float left, float right, float rear, float front) {
        boolean contains(float x, float z) {
            return x >= left && x <= right && z >= rear && z <= front;
        }
    }

    record Route(float sideways, float lift, float riseUntil, float descendFrom, float touchdown) {
        float airborne(float progress) {
            return MathUtils.clamp(progress / touchdown, 0f, 1f);
        }

        float cost() {
            return lift * 1.65f + Math.abs(sideways) * .40f + (touchdown - TOUCHDOWN) * 18f;
        }
    }

    static float travel(float progress) {
        float p = MathUtils.clamp(progress, 0f, 1f);
        return p * (2f - p);
    }

    static float airborne(float progress) {
        return MathUtils.clamp(progress / TOUCHDOWN, 0f, 1f);
    }

    static void sample(Vector3 out, Vector3 from, Vector3 to, float progress, float width) {
        sample(out, from, to, progress, width, DIRECT);
    }

    static void sample(Vector3 out, Vector3 from, Vector3 to, float progress, float width, Route route) {
        float p = MathUtils.clamp(progress, 0f, 1f);
        float t = travel(p);
        float q = airborne(p);
        float dx = to.x - from.x, dz = to.z - from.z;
        float distance = (float) Math.hypot(dx, dz);
        float arch = Math.min(width * 0.12f, distance * 0.018f);
        float pulse = (float) Math.sin(Math.PI * q);
        float sideways = route.sideways * lateralShape(t) / Math.max(1f, distance);
        float lift = 0f;
        if (p < route.touchdown && route.lift > 0f) {
            float rise = route.riseUntil <= 0f ? 1f : smooth(p / route.riseUntil);
            float fall = smooth((route.touchdown - p) / (route.touchdown - route.descendFrom));
            lift = route.lift * rise * fall;
        }
        out.set(MathUtils.lerp(from.x, to.x, t) - dz * sideways,
                to.y + (from.y - to.y) * (1f - q) * (1f - q) + arch * pulse * pulse + lift,
                MathUtils.lerp(from.z, to.z, t) + dx * sideways);
    }

    static float angle(float from, float to, float progress) {
        return MathUtils.lerp(from, to, smooth(progress));
    }

    static Route plan(Vector3 from, Vector3 to, float width, float height, float startAngle,
            float endAngle, float restHeight, List<Pile> piles, Bounds bounds) {
        if (piles.isEmpty()) return DIRECT;
        Route best = inspect(from, to, width, height, startAngle, endAngle, restHeight, piles, 0f, bounds);
        if (best.lift() < width * .26f) return best;
        for (float detour : DETOURS) {
            float offset = detour * width;
            if (Math.abs(offset) * .40f >= best.cost()) continue;
            Route alternative = inspect(from, to, width, height, startAngle, endAngle,
                    restHeight, piles, offset, bounds);
            if (alternative != null && alternative.cost() < best.cost()) best = alternative;
        }
        return best;
    }

    private static Route inspect(Vector3 from, Vector3 to, float width, float height, float startAngle,
            float endAngle, float restHeight, List<Pile> piles, float offset, Bounds bounds) {
        float dx = to.x - from.x, dz = to.z - from.z;
        float distance = (float) Math.hypot(dx, dz);
        float nx = -dz / Math.max(1f, distance), nz = dx / Math.max(1f, distance);
        float halfW = width * .5f, halfH = height * .5f;
        float corner = (float) Math.hypot(halfW, halfH);
        float first = 1f, last = 0f, lift = 0f;
        float margin = Math.max(2f, width * .027f);
        for (int step = 0; step < SWEEP_STEPS; step++) {
            float p0 = sweepProgress(step), p1 = sweepProgress(step + 1);
            float mid = (p0 + p1) * .5f;
            float travel = travel(mid);
            float side = offset * lateralShape(travel);
            float x = from.x + dx * travel + nx * side;
            float z = from.z + dz * travel + nz * side;
            if (offset != 0f && !bounds.contains(x, z)) return null;
            float angle = angle(startAngle, endAngle, mid);
            float cosine = MathUtils.cosDeg(angle), sine = MathUtils.sinDeg(angle);

            // Expand the disc by the maximum translation and corner rotation
            // across this interval, so a thin overlap between samples still counts.
            float du = Math.max(travel - travel(p0), travel(p1) - travel);
            float turn = Math.max(Math.abs(angle - angle(startAngle, endAngle, p0)),
                    Math.abs(angle(startAngle, endAngle, p1) - angle));
            float motion = du * (distance + 4f * Math.abs(offset)) + corner * turn * MathUtils.degreesToRadians;
            float q0 = airborne(p0), q1 = airborne(p1);
            float launch = Math.min(launchHeight(from, to, q0), launchHeight(from, to, q1));
            float pulse = Math.min(MathUtils.sin(MathUtils.PI * q0), MathUtils.sin(MathUtils.PI * q1));
            float naturalHeight = to.y + launch + Math.min(width * .12f, distance * .018f) * pulse * pulse;
            for (Pile pile : piles) {
                float px = pile.x - x, pz = pile.z - z;
                float radius = pile.radius + motion;
                float broad = corner + radius;
                if (px * px + pz * pz > broad * broad) continue;
                float localX = Math.max(0f, Math.abs(px * cosine + pz * sine) - halfW);
                float localZ = Math.max(0f, Math.abs(-px * sine + pz * cosine) - halfH);
                if (localX * localX + localZ * localZ > radius * radius
                        || naturalHeight + restHeight >= pile.top + margin) continue;
                first = Math.min(first, p0);
                last = Math.max(last, p1);
                lift = Math.max(lift, pile.top + margin - naturalHeight - restHeight);
            }
        }
        if (lift <= 0f) return offset == 0f ? DIRECT : new Route(offset, 0f, 0f, 0f, TOUCHDOWN);
        // Hold the margin through the last overlap before easing down to a clear slide.
        float descendFrom = last < 1f ? last : .985f;
        float touchdown = Math.min(1f, Math.max(TOUCHDOWN, descendFrom + .14f));
        return new Route(offset, lift, first, descendFrom, touchdown);
    }

    private static float launchHeight(Vector3 from, Vector3 to, float phase) {
        float remaining = 1f - phase;
        return (from.y - to.y) * remaining * remaining;
    }

    private static float lateralShape(float travel) {
        float p = 4f * travel * (1f - travel);
        return p * p;
    }

    private static float sweepProgress(int step) {
        float p = (float) step / SWEEP_STEPS;
        if (p <= .75f) return p;
        float remaining = 1f - p;
        return 1f - 4f * remaining * remaining;
    }

    private static float smooth(float value) {
        float t = MathUtils.clamp(value, 0f, 1f);
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }
}
