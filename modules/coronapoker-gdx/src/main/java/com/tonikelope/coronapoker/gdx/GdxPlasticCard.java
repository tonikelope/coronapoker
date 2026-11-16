/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/** Inextensible transverse arcs with a small corner lead for a plastic card. */
final class GdxPlasticCard {

    private GdxPlasticCard() { }

    static float thickness(float width) {
        return width * 0.0048f;
    }

    /**
     * Inextensible sheet with a broad cylindrical bend and gently ramped
     * curvature at the contact line and grip. The profile is integrated in
     * material arc length, so lifting never scales or crops the supported
     * part of the card. The maximum curvature has a finite, broad radius.
     */
    static final class PeekShape {
        private static final int STEPS = 192;
        // Both sheets follow one developable surface. Their short edges lift
        // together while the far 20 percent remains supported.
        private static final double DX = 0.0;
        private static final double DZ = 1.0;
        private static final double LENGTH = 0.80;
        private final double[] arc = new double[STEPS + 1];
        private final double[] forward = new double[STEPS + 1];
        private final double[] up = new double[STEPS + 1];
        private final double[] turn = new double[STEPS + 1];
        private float amount = Float.NaN;

        void set(float lift) {
            lift = MathUtils.clamp(lift, 0f, 1f);
            if (lift == amount) return;
            amount = lift;
            if (lift < 0.00001f) return;
            double tip = Math.toRadians(155.0) * lift;
            double ramp = .12;
            double ds = LENGTH / STEPS;
            arc[0] = forward[0] = up[0] = turn[0] = 0.0;
            for (int i = 1; i <= STEPS; i++) {
                double q = i / (double) STEPS;
                double integrated = q < ramp ? q * q / (2.0 * ramp)
                        : q > 1.0 - ramp ? 1.0 - ramp - (1.0 - q) * (1.0 - q) / (2.0 * ramp)
                        : q - ramp * .5;
                double angle = tip * integrated / (1.0 - ramp);
                double previous = turn[i - 1];
                arc[i] = i * ds;
                forward[i] = forward[i - 1] + ds * .5 * (Math.cos(previous) + Math.cos(angle));
                up[i] = up[i - 1] + ds * .5 * (Math.sin(previous) + Math.sin(angle));
                turn[i] = angle;
            }
        }

        float sample(Vector3 out, float width, float height, float u, float v) {
            return sample(out, null, width, height, u, v);
        }

        float sample(Vector3 out, Vector3 normal, float width, float height, float u, float v) {
            double x = (u - 0.5) * width;
            double z = (v - 0.5) * height;
            double distance = (x * DX + z * DZ) / height + 0.30;
            if (amount < 0.00001f || distance <= 0.0) {
                out.set((float) x, 0f, (float) z);
                if (normal != null) normal.set(0f, 1f, 0f);
                return 1f;
            }
            int low = 0, high = STEPS;
            while (high - low > 1) {
                int middle = (low + high) >>> 1;
                if (arc[middle] < distance) low = middle;
                else high = middle;
            }
            double t = Math.min(1.0, (distance - arc[low]) / (arc[high] - arc[low]));
            double angle = turn[low] + (turn[high] - turn[low]) * t;
            double extra = Math.max(0.0, distance - LENGTH);
            double along = forward[low] + (forward[high] - forward[low]) * t + extra * Math.cos(angle);
            double lift = up[low] + (up[high] - up[low]) * t + extra * Math.sin(angle);
            out.set((float) (x + DX * (along - distance) * height), (float) (lift * height),
                    (float) (z + DZ * (along - distance) * height));
            if (normal != null) normal.set((float) (-DX * Math.sin(angle)),
                    (float) Math.cos(angle), (float) (-DZ * Math.sin(angle)));
            return (float) (1.0 - 0.20 * Math.pow(Math.sin(angle), 2.0));
        }
    }

    /** Continues the same short-edge lift into an inextensible face-up sheet. */
    static final class UncoverShape {
        private static final int STEPS = 256;
        private final PeekShape peek = new PeekShape();
        private final PeekShape rollingProfile = new PeekShape();
        private final Vector3 point = new Vector3(), normal = new Vector3();
        private final double[] z = new double[STEPS + 1], y = new double[STEPS + 1];
        private final double[] angle = new double[STEPS + 1];
        private float lastLift = Float.NaN, lastTurn = Float.NaN;
        private double floor;
        private float minimumV, range, lastExtent = Float.NaN;

        void set(float lift, float turn) { set(lift, turn, .5f); }

        void set(float lift, float turn, float extent) {
            if (lift == lastLift && turn == lastTurn && extent == lastExtent) return;
            lastExtent = extent;
            minimumV = .5f - extent;
            range = 2f * extent;
            lastLift = lift;
            lastTurn = turn;
            peek.set(lift);
            rollingProfile.set(1f);
            float advance = (1f - minimumV) * turn;
            float tip = MathUtils.lerp(155f * lift, 180f,
                    turn);
            floor = 0.0;
            z[0] = -extent * (1.0 - 2.0 * turn);
            y[0] = 0.0;
            for (int i = 0; i <= STEPS; i++) {
                // Roll the broad bend along the material instead of lifting
                // the whole sheet into a nearly vertical panel at mid-turn.
                rollingProfile.sample(point, normal, 1f, 1f, .5f,
                        minimumV + range * i / STEPS + advance);
                angle[i] = Math.atan2(-normal.z, normal.y) * tip / 155.0;
                if (i > 0) {
                    z[i] = z[i - 1] + (Math.cos(angle[i - 1]) + Math.cos(angle[i])) * range / (2.0 * STEPS);
                    y[i] = y[i - 1] + (Math.sin(angle[i - 1]) + Math.sin(angle[i])) * range / (2.0 * STEPS);
                    floor = Math.min(floor, y[i]);
                }
            }
        }

        float sample(Vector3 out, Vector3 outNormal, float width, float height, float u, float v) {
            if (lastTurn == 0f) return peek.sample(out, outNormal, width, height, u, v);
            float cell = MathUtils.clamp((v - minimumV) / range, 0f, 1f) * STEPS;
            int i = Math.min(STEPS - 1, (int) cell);
            double t = cell - i;
            double a = angle[i] + (angle[i + 1] - angle[i]) * t;
            double extra = v < minimumV ? v - minimumV : Math.max(0f, v - minimumV - range);
            double forward = z[i] + (z[i + 1] - z[i]) * t + extra * Math.cos(a);
            double up = y[i] + (y[i + 1] - y[i]) * t + extra * Math.sin(a) - floor;
            out.set((u - .5f) * width, (float) up * height, (float) forward * height);
            outNormal.set(0f, (float) Math.cos(a), (float) -Math.sin(a));
            return (float) (1.0 - .20 * Math.pow(Math.sin(a), 2.0));
        }
    }

    static float uncoverLift(float initial, float progress) {
        return MathUtils.lerp(initial, 1f, Interpolation.smoother.apply(
                MathUtils.clamp(progress / .65f, 0f, 1f)));
    }

    static float uncoverTurn(float initial, float progress) {
        float lead = .35f * (1f - initial);
        return Interpolation.smoother.apply(MathUtils.clamp(
                (progress - lead) / (1f - lead), 0f, 1f));
    }

    static float uncoverSeconds(float configuredFlipSeconds) {
        return configuredFlipSeconds * 1.15f;
    }

    record Motion(float tempo, float bend, float corner) { }
    static final Motion STANDARD = new Motion(0f, 1f, 0.19f);

    /** One bounded gesture per event, independent of render frame rate. */
    static Motion motion(long seed) {
        long mixed = seed + 0x9e3779b97f4a7c15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        mixed ^= mixed >>> 31;
        float a = (mixed & 0xffffL) / 65535f;
        float b = ((mixed >>> 16) & 0xffffL) / 65535f;
        float c = ((mixed >>> 32) & 0xffffL) / 65535f;
        return new Motion((a - 0.5f) * 0.07f, 0.87f + 0.26f * b,
                (c < 0.5f ? -1f : 1f) * (0.14f + 0.08f * Math.abs(c * 2f - 1f)));
    }

    static void flight(Vector3 out, float width, float height, float u, float v, float progress) {
        double pulse = Math.sin(Math.PI * progress);
        double bend = 0.10 * pulse * pulse;
        if (Math.abs(bend) < 0.0001f) {
            out.set((u - 0.5f) * width, 0f, (v - 0.5f) * height);
            return;
        }
        double radius = height / bend;
        double angle = (v - 0.5f) * bend;
        out.set((u - 0.5f) * width, (float) (radius * (1.0 - Math.cos(angle))),
                (float) (radius * Math.sin(angle)));
    }

    static void reveal(Vector3 out, float width, float height, float u, float v,
            float progress, float direction) {
        reveal(out, width, height, u, v, progress, direction, STANDARD);
    }

    static void reveal(Vector3 out, float width, float height, float u, float v,
            float progress, float direction, Motion motion) {
        float p = MathUtils.clamp(progress, 0f, 1f);
        p += motion.tempo() * (float) (Math.sin(2.0 * Math.PI * p) * Math.sin(Math.PI * p));
        if (p == 0f || p == 1f) {
            out.set((u - 0.5f) * width * (p == 0f ? 1f : -1f),
                    0f, (v - 0.5f) * height);
            return;
        }
        float pulse = (float) Math.sin(Math.PI * p);
        float turn = MathUtils.PI * Interpolation.smoother.apply(p);
        // The near corner starts first. The far edge follows through the
        // same continuous surface, rather than turning a rigid rectangle.
        float cornerLead = motion.corner() * pulse * (2f * v - 1f);
        double theta = Math.copySign(MathUtils.clamp(turn + cornerLead,
                0f, MathUtils.PI), direction);
        double bend = 0.80 * motion.bend() * pulse * (0.75 + 0.25 * v);
        double from = theta - bend * 0.5;
        double to = theta + bend * 0.5;
        double at = from + u * bend;
        double radius = width / bend;
        double x = radius * (Math.sin(at) - (Math.sin(from) + Math.sin(to)) * 0.5);
        // Find the lowest point of the arc, including its interior. This
        // keeps the supported edge on the felt throughout the turn.
        double floor = Math.min(Math.cos(from), Math.cos(to));
        if (from <= -Math.PI && to >= -Math.PI || from <= Math.PI && to >= Math.PI) {
            floor = -1.0;
        }
        double y = radius * (Math.cos(at) - floor);
        out.set((float) x, (float) y, (v - 0.5f) * height);
    }
}
