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
     * Quasi-static Euler elastica for the lifted corner. The supported sheet
     * is clamped flat, the lifted end carries a force and no bending moment.
     * The first integral of B theta'' + F sin(beta - theta) = 0 is integrated
     * in angle space, then resampled by arc length. Extruding along the
     * diagonal contact line is developable: the plastic cannot stretch.
     */
    static final class PeekShape {
        private static final int STEPS = 192;
        private static final double DX = -Math.sin(Math.toRadians(14.0));
        private static final double DZ = Math.cos(Math.toRadians(14.0));
        private static final double LENGTH = 0.56;
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
            double tip = Math.toRadians(132.0) * lift;
            double force = Math.toRadians(170.0);
            double limit = 2.0 * Math.sqrt(tip / Math.sin(force - tip));
            double prevWeight = 2.0 * tip / Math.sqrt(Math.cos(force - tip) - Math.cos(force));
            double prevAngle = 0.0;
            arc[0] = forward[0] = up[0] = turn[0] = 0.0;
            for (int i = 1; i <= STEPS; i++) {
                double q = i / (double) STEPS;
                double angle = tip * (2.0 * q - q * q);
                double weight = i == STEPS ? limit : 2.0 * tip * (1.0 - q)
                        / Math.sqrt(Math.cos(force - tip) - Math.cos(force - angle));
                double step = 0.5 / STEPS;
                arc[i] = arc[i - 1] + step * (prevWeight + weight);
                forward[i] = forward[i - 1] + step * (prevWeight * Math.cos(prevAngle) + weight * Math.cos(angle));
                up[i] = up[i - 1] + step * (prevWeight * Math.sin(prevAngle) + weight * Math.sin(angle));
                turn[i] = angle;
                prevWeight = weight;
                prevAngle = angle;
            }
            double scale = LENGTH / arc[STEPS];
            for (int i = 1; i <= STEPS; i++) {
                arc[i] *= scale;
                forward[i] *= scale;
                up[i] *= scale;
            }
        }

        float sample(Vector3 out, float width, float height, float u, float v) {
            return sample(out, null, width, height, u, v);
        }

        float sample(Vector3 out, Vector3 normal, float width, float height, float u, float v) {
            double x = (u - 0.5) * width;
            double z = (v - 0.5) * height;
            double distance = (x * DX + z * DZ) / height + 0.065;
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
        double bend = 0.17 * Math.sin(2.0 * Math.PI * progress);
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
