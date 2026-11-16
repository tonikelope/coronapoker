/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.MathUtils;
import java.util.List;

/** Table-space toss with two exact, flat contacts and a short clearance arc. */
final class GdxTableChipFlight {
    private static final float RAMP = .24f;
    static final float TOUCHDOWN = .90f;

    record Contact(float x, float y, float height, float rotation) {}
    record Pile(float x, float y, float height) {}

    static final class Pose {
        float x, y, height, rotation, pitch, roll;
        boolean tumbling;
        float axisX, axisZ, tumbleAngle;
    }

    private GdxTableChipFlight() {}

    /** Mix the start time and chip identity so the throw stays fixed throughout its animation. */
    static long tossSeed(long startedAt, int chipIndex, int color) {
        long seed = startedAt ^ ((long) chipIndex << 32) ^ color;
        seed = (seed ^ (seed >>> 30)) * 0xbf58476d1ce4e5b9L;
        seed = (seed ^ (seed >>> 27)) * 0x94d049bb133111ebL;
        return seed ^ (seed >>> 31);
    }

    static float variation(long seed, int shift) {
        return ((seed >>> shift) & 0xffffL) / 65535f;
    }

    static void sampleInitialDrop(Pose out, Contact to, float progress, float diameter,
            float flightSeconds, long seed) {
        setFlatPose(out, to);
        float t = MathUtils.clamp(progress, 0f, 1f);
        float remaining = 1f - t;
        // A 39 mm chip establishes the scale. Release from rest under 9.81 m/s².
        float dropHeight = .5f * GdxTableChipRack.gravity(diameter) * flightSeconds * flightSeconds;
        out.height += dropHeight * (1f - t * t);
        out.x += diameter * .04f * (variation(seed, 0) - .5f) * remaining;
        out.y += diameter * .04f * (variation(seed, 16) - .5f) * remaining;
        out.rotation += (variation(seed, 32) - .5f) * 3.6f * remaining;
    }

    static void sampleThrow(Pose out, Contact from, Contact to, float progress,
            float diameter, List<Pile> piles, float lane, long seed) {
        if (progress < TOUCHDOWN) {
            sampleParabola(out, from, to, progress / TOUCHDOWN, diameter, piles, lane, seed);
            return;
        }
        setFlatPose(out, to);
        float rebound = MathUtils.clamp((progress - TOUCHDOWN) / (1f - TOUCHDOWN), 0f, 1f);
        out.height += diameter * .045f * arch(rebound);
    }

    /** A throw with a ballistic centre of mass and an end-over-end flip. */
    static void sampleParabola(Pose out, Contact from, Contact to, float progress,
            float diameter, List<Pile> piles, float lane, long seed) {
        float t = MathUtils.clamp(progress, 0f, 1f);
        if (t == 0f || t == 1f) {
            setFlatPose(out, t == 0f ? from : to);
            return;
        }
        float dx = to.x() - from.x(), dy = to.y() - from.y();
        float length = Math.max(1f, (float) Math.hypot(dx, dy));
        out.x = MathUtils.lerp(from.x(), to.x(), t);
        out.y = MathUtils.lerp(from.y(), to.y(), t);

        float tumbleBias = .35f * (variation(seed, 32) - .5f);
        float rise = diameter * (.83f + .42f * variation(seed, 16))
                + Math.min(length, diameter * 8f) * .078f;
        rise = Math.max(rise, clearanceRise(from, to, diameter, piles, tumbleBias));
        // Solve the release velocity and flight time under gravity to reach the target height.
        out.height = ballisticHeight(from.height(), to.height(), t, rise, diameter);

        out.tumbling = true;
        // Vary how diagonally the chip flips while keeping the axis horizontal.
        float cant = .55f * (variation(seed, 48) - .5f) + .06f * MathUtils.clamp(lane, -3f, 3f);
        float cos = MathUtils.cos(cant), sin = MathUtils.sin(cant);
        out.axisX = (dy * cos + dx * sin) / length;
        out.axisZ = (dx * cos - dy * sin) / length;
        float angle = tumble(t, tumbleBias);
        out.tumbleAngle = (seed & 1L) == 0L ? -angle : angle;
        float facingDelta = MathUtils.atan2(MathUtils.sin(to.rotation() - from.rotation()),
                MathUtils.cos(to.rotation() - from.rotation()));
        float yaw = 1.15f * (variation(seed, 20) - .5f) * arch(t);
        out.rotation = from.rotation() + facingDelta * t + yaw;
    }

    static float ballisticHeight(float from, float to, float progress, float rise, float diameter) {
        if (progress <= 0f) return from;
        if (progress >= 1f) return to;
        float gravity = GdxTableChipRack.gravity(diameter);
        float flightTime = (float) Math.sqrt(8f * rise / gravity);
        float time = progress * flightTime;
        float releaseSpeed = (to - from) / flightTime + .5f * gravity * flightTime;
        return from + time * (releaseSpeed - .5f * gravity * time);
    }

    private static float clearanceRise(Contact from, Contact to, float diameter,
            List<Pile> piles, float tumbleBias) {
        // The swept disc has to clear the piles it actually crosses. Compute
        // one launch height for the entire parabola, instead of lifting the
        // chip in the middle of the flight when it reaches an obstacle.
        float collisionRadius = diameter * 1.045f;
        float halfThickness = GdxTableChipRack.thickness(diameter) * .5f;
        float needed = 0f;
        for (Pile pile : piles) {
            if (MathUtils.isEqual(pile.height(), 0f)
                    || nearContact(pile, from, diameter)
                    || nearContact(pile, to, diameter)) continue;
            for (int sample = 1; sample < 28; sample++) {
                float t = sample / 28f;
                float x = MathUtils.lerp(from.x(), to.x(), t);
                float y = MathUtils.lerp(from.y(), to.y(), t);
                float dx = x - pile.x(), dy = y - pile.y();
                if (dx * dx + dy * dy >= collisionRadius * collisionRadius) continue;
                float angle = tumble(t, tumbleBias);
                float lip = diameter * .5f * Math.abs(MathUtils.sin(angle))
                        + halfThickness * (Math.abs(MathUtils.cos(angle)) - 1f);
                float surface = MathUtils.lerp(from.height(), to.height(), t);
                needed = Math.max(needed, (pile.height() + lip + 3f - surface) / arch(t));
            }
        }
        return needed;
    }

    private static float tumble(float t, float bias) {
        float spinProgress = travel(t, .22f);
        return MathUtils.PI2 * (spinProgress + bias * arch(spinProgress));
    }

    private static boolean nearContact(Pile pile, Contact contact, float diameter) {
        float dx = pile.x() - contact.x(), dy = pile.y() - contact.y();
        return dx * dx + dy * dy < diameter * diameter * .01f;
    }

    private static float arch(float t) {
        return 4f * t * (1f - t);
    }

    static void sample(Pose out, Contact from, Contact to, float progress,
            float diameter, float clearance, float lane, float variation) {
        float t = MathUtils.clamp(progress, 0f, 1f);
        out.tumbling = false;
        if (t == 0f || t == 1f) {
            setFlatPose(out, t == 0f ? from : to);
            return;
        }

        float dx = to.x() - from.x(), dy = to.y() - from.y();
        float length = Math.max(1f, (float) Math.hypot(dx, dy));
        float travel = travel(t);
        float release = smooth(Math.min(1f, t / .17f));
        float approach = smooth(Math.min(1f, (1f - t) / .20f));
        float pathArch = MathUtils.sin(MathUtils.PI * t);
        float lateral = MathUtils.clamp(lane, -3f, 3f) * diameter * .22f
                * pathArch * pathArch;
        out.x = MathUtils.lerp(from.x(), to.x(), travel) - dy / length * lateral;
        out.y = MathUtils.lerp(from.y(), to.y(), travel) + dx / length * lateral;

        float surface = MathUtils.lerp(from.height(), to.height(), travel);
        float above = Math.max(0f, clearance - surface) * (release * approach);
        float arch = (diameter * (.20f + .09f * variation)
                + Math.min(length, diameter * 8f) * .055f) * pathArch;
        out.height = surface + above + arch;

        float delta = MathUtils.atan2(MathUtils.sin(to.rotation() - from.rotation()),
                MathUtils.cos(to.rotation() - from.rotation()));
        float spin = variation < .5f ? -MathUtils.PI2 : MathUtils.PI2;
        out.rotation = from.rotation() + delta * travel + spin * travel;

        float tilt = pathArch * pathArch;
        float nose = (.28f + .13f * variation) * MathUtils.cos(MathUtils.PI * t);
        float bank = (.13f + .10f * variation) * MathUtils.sin(MathUtils.PI * (t + variation));
        out.pitch = tilt * (nose * dy + bank * dx) / length;
        out.roll = tilt * (nose * dx - bank * dy) / length;
    }

    private static void setFlatPose(Pose out, Contact contact) {
        out.x = contact.x();
        out.y = contact.y();
        out.height = contact.height();
        out.rotation = contact.rotation();
        out.tumbling = false;
        out.tumbleAngle = 0f;
        out.pitch = 0f;
        out.roll = 0f;
    }

    /** Integrate a smooth acceleration ramp, cruise, and a mirrored brake. */
    static float travel(float t) {
        return travel(t, RAMP);
    }

    private static float travel(float t, float ramp) {
        if (t < ramp) return rampDistance(t, ramp) / (1f - ramp);
        if (t > 1f - ramp) return 1f - rampDistance(1f - t, ramp) / (1f - ramp);
        return (t - ramp * .5f) / (1f - ramp);
    }

    private static float rampDistance(float time, float duration) {
        float ramp = time / duration;
        return duration * ramp * ramp * ramp * (1f - ramp * .5f);
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }
}
