/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/** Inextensible transverse arcs with a small corner lead for a plastic card. */
final class GdxPlasticCard {

    static final float CORNER_FRACTION = 0.075f;
    static final float THICKNESS_MM = .32f;

    private GdxPlasticCard() { }

    static float thickness(float width) {
        return width * (THICKNESS_MM / 63f);
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
            // Advance the turnover ahead of the fan as the sheet starts to
            // settle, leaving the last part of the gesture for horizontal motion.
            float roll = turn + 0.44f * turn * turn * (1f - turn);
            float advance = (1f - minimumV) * roll;
            float tip = MathUtils.lerp(155f * lift, 180f,
                    turn);
            float release = Interpolation.smoother.apply(
                    MathUtils.clamp((turn - 0.84f) / 0.15f, 0f, 1f));
            floor = 0.0;
            z[0] = -extent * (1.0 - 2.0 * turn);
            y[0] = 0.0;
            for (int i = 0; i <= STEPS; i++) {
                // Roll the broad bend along the material instead of lifting
                // the whole sheet into a nearly vertical panel at mid-turn.
                rollingProfile.sample(point, normal, 1f, 1f, .5f,
                        minimumV + range * i / STEPS + advance);
                double tangent = Math.atan2(-normal.z, normal.y) * tip / 155.0;
                // Let the remaining curl unwind while the fan is still
                // opening, so the last edge meets the felt with little speed.
                angle[i] = tangent + (Math.PI - tangent) * release;
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

    static float uncoverLift(float initial, float progress, Motion motion) {
        float gripHeight = Math.max(initial, 0.994f + (motion.bend() - 1f) * 0.045f);
        float liftEnd = 0.56f + motion.tempo() * 0.35f;
        return MathUtils.lerp(initial, gripHeight, Interpolation.smoother.apply(
                MathUtils.clamp(progress / liftEnd, 0f, 1f)));
    }

    static float uncoverTurn(float initial, float progress, Motion motion) {
        float covered = 1f - initial;
        float lead = (0.30f + motion.tempo() * 0.40f)
                * covered * (1f - 0.56f * initial);
        float roll = MathUtils.clamp((progress - lead) / (1f - lead), 0f, 1f);
        return MathUtils.lerp(Interpolation.smoother.apply(roll),
                Interpolation.smooth.apply(roll), 0.45f * covered * covered);
    }

    static float uncoverSeconds(float configuredFlipSeconds) {
        return configuredFlipSeconds * 0.95f;
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

    /** Bends the gripped side first and releases it during a 3D community turn. */
    static final class CommunityTurn {
        private static final int STEPS = 96;
        private static final float RELEASED_AT = 0.91f;
        private static final double BEND_RADIANS = 0.48;
        private final double[] forward = new double[STEPS + 1];
        private final double[] lift = new double[STEPS + 1];

        private float width, height;
        private int flatDirection;
        private double sign, span, skewSin, skewCos, floor;

        void set(float width, float height, float progress, float direction, Motion motion) {
            this.width = width;
            this.height = height;
            float p = MathUtils.clamp(progress, 0f, 1f);
            flatDirection = p == 0f ? 1 : p == 1f ? -1 : 0;
            if (flatDirection != 0) return;

            p += 0.75f * motion.tempo() * (float) (Math.sin(2.0 * Math.PI * p) * Math.sin(Math.PI * p));
            float loadedAt = 0.33f + 0.38f * motion.tempo();
            double load = p < loadedAt
                    ? Interpolation.smoother.apply(p / loadedAt)
                    : 1f - Interpolation.smoother.apply(
                            MathUtils.clamp((p - loadedAt) / (RELEASED_AT - loadedAt), 0f, 1f));
            sign = Math.copySign(1.0, direction);
            double skew = -sign * (0.036 + 0.035 * motion.corner()) * load;
            skewSin = Math.sin(skew);
            skewCos = Math.cos(skew);
            span = width * skewCos + height * Math.abs(skewSin);

            // Integrate in material distance from the supported edge toward
            // the grip. The bend initially lives near the fingers and travels
            // inward as the card turns, sharing one profile across the plate.
            double spread = Interpolation.smooth.apply(
                    MathUtils.clamp((p - 0.11f) / 0.46f, 0f, 1f));
            double gripStart = 0.66 - 0.45 * spread + 0.16 * motion.corner();
            double bend = BEND_RADIANS * motion.bend() * load;
            double turn = Math.PI * Interpolation.smooth.apply(p) - bend * gripCurve(0.5, gripStart);
            floor = 0.0;
            for (int step = 1; step <= STEPS; step++) {
                double distance = (step - 0.5) / STEPS;
                double tangent = turn + bend * gripCurve(distance, gripStart);
                forward[step] = forward[step - 1] + Math.cos(tangent) / STEPS;
                lift[step] = lift[step - 1] + Math.sin(tangent) / STEPS;
                floor = Math.min(floor, lift[step]);
            }
        }

        void sample(Vector3 out, float u, float v) {
            float x = (u - 0.5f) * width;
            float z = (v - 0.5f) * height;
            if (flatDirection != 0) {
                out.set(x * flatDirection, 0f, z);
                return;
            }
            double along = x * skewCos + z * skewSin;
            double across = -x * skewSin + z * skewCos;
            double index = (0.5 - sign * along / span) * STEPS;
            int cell = MathUtils.clamp((int) index, 0, STEPS - 1);
            // The source grid extends slightly beyond the physical contour.
            // Continue the edge tangent so the contour can clip the mesh.
            double fraction = index - cell;
            double distance = forward[cell] + fraction * (forward[cell + 1] - forward[cell]);
            double elevation = lift[cell] + fraction * (lift[cell + 1] - lift[cell]);
            double rotated = sign * span * (forward[STEPS / 2] - distance);
            out.set((float) (rotated * skewCos - across * skewSin),
                    (float) ((elevation - floor) * span),
                    (float) (rotated * skewSin + across * skewCos));
        }

        private static double gripCurve(double distance, double start) {
            double t = Math.max(0.0, (distance - start) / (1.0 - start));
            return t * t * (3.0 - 2.0 * t);
        }
    }

    static void flight(Vector3 out, float width, float height, float u, float v, float progress) {
        flight(out, width, height, u, v, progress, 1f);
    }

    static void flight(Vector3 out, float width, float height, float u, float v,
            float progress, float bendScale) {
        double pulse = Math.sin(Math.PI * progress);
        double bend = 0.10 * bendScale * pulse * pulse;
        if (Math.abs(bend) < 0.0001f) {
            out.set((u - 0.5f) * width, 0f, (v - 0.5f) * height);
            return;
        }
        double radius = height / bend;
        double angle = (v - 0.5f) * bend;
        out.set((u - 0.5f) * width, (float) (radius * (1.0 - Math.cos(angle))),
                (float) (radius * Math.sin(angle)));
    }
}
