/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import java.util.List;

/** A low throw, a soft touchdown and a final slide with continuous deceleration. */
final class GdxCardDealMotion {
    static final float TOUCHDOWN = 0.68f;
    private static final float ALIGNMENT_TRAVEL = travel(TOUCHDOWN);
    private static final int SWEEP_STEPS = 96;
    private static final float[] DETOURS = {.55f, -.55f, 1.05f, -1.05f, 1.55f, -1.55f};
    private static final float[] COMMUNITY_CONTACTS = {.82f, .89f};
    private static final float CURVED_TOUCHDOWN = .76f;
    private static final float[] COMMUNITY_SLIDES = {.42f, -.42f, .72f, -.72f, 1.10f, -1.10f};
    private static final float COMMUNITY_BRAKE = .42f;
    private static final float MAX_DIRECT_COMMUNITY_LIFT = .88f;
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
    record Slide(float x, float z) {
        float length() {
            return (float) Math.hypot(x, z);
        }

        static float speedFactor(float touchdown) {
            return -2f * touchdown / (1f - touchdown);
        }
    }

    record Bounds(float left, float right, float rear, float front) {
        boolean contains(float x, float z) {
            return x >= left && x <= right && z >= rear && z <= front;
        }
    }

    record Route(float sideways, float lift, float riseUntil, float descendFrom, float touchdown,
            boolean directCommunity, Slide slide) {
        Route(float sideways, float lift, float riseUntil, float descendFrom, float touchdown) {
            this(sideways, lift, riseUntil, descendFrom, touchdown, false);
        }

        Route(float sideways, float lift, float riseUntil, float descendFrom, float touchdown,
                boolean directCommunity) {
            this(sideways, lift, riseUntil, descendFrom, touchdown, directCommunity, null);
        }

        boolean approachesAtFeltHeight() {
            return directCommunity || slide != null;
        }

        float airborne(float progress) {
            return MathUtils.clamp(progress / touchdown, 0f, 1f);
        }

        float cost() {
            return lift * 1.65f + Math.abs(sideways) * .40f + (touchdown - TOUCHDOWN) * 18f;
        }
    }

    private static float communityTravel(float progress) {
        float cruise = 1f - COMMUNITY_BRAKE * .5f;
        if (progress <= 1f - COMMUNITY_BRAKE) return progress / cruise;
        float remaining = 1f - progress;
        return 1f - remaining * remaining / (2f * COMMUNITY_BRAKE * cruise);
    }

    private static float routeTravel(float progress, Route route) {
        return route.directCommunity ? communityTravel(progress) : travel(progress);
    }

    private static float clearanceLift(float progress, float travelled, Route route, boolean community) {
        if (route.lift == 0f || progress >= route.touchdown) return 0f;
        float rise = route.riseUntil == 0f ? 1f : smooth(progress / route.riseUntil);
        if (progress <= route.descendFrom) return route.lift * rise;
        float remaining = (route.touchdown - progress) / (route.touchdown - route.descendFrom);
        if (community && route.slide == null) {
            // Spend the clearance height as the card covers the remaining approach.
            // In particular, the final braking frames should carry very little height.
            float contact = routeTravel(route.touchdown, route);
            remaining = (contact - travelled) / (contact - routeTravel(route.descendFrom, route));
        }
        return route.lift * rise * smooth(remaining);
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
        sample(out, from, to, progress, width, route, route.directCommunity);
    }

    static void sample(Vector3 out, Vector3 from, Vector3 to, float progress, float width,
            Route route, boolean community) {
        float p = MathUtils.clamp(progress, 0f, 1f);
        float q = route.airborne(p);
        float t = routeTravel(p, route);
        float y = to.y + launchHeight(from, to, q);
        float lift = clearanceLift(p, t, route, community);
        if (route.slide != null) {
            out.set(approachCoordinate(from.x, to.x, route.slide.x, p, route.touchdown),
                    y + lift, approachCoordinate(from.z, to.z, route.slide.z, p, route.touchdown));
            return;
        }
        if (route.directCommunity) {
            out.set(MathUtils.lerp(from.x, to.x, t), y + lift, MathUtils.lerp(from.z, to.z, t));
            return;
        }
        float dx = to.x - from.x, dz = to.z - from.z;
        float distance = (float) Math.hypot(dx, dz);
        float arch = Math.min(width * 0.12f, distance * 0.018f);
        float pulse = q == 1f ? 0f : (float) Math.sin(Math.PI * q);
        float sideways = route.sideways * lateralShape(t) / Math.max(1f, distance);
        out.set(MathUtils.lerp(from.x, to.x, t) - dz * sideways,
                y + arch * pulse * pulse + lift,
                MathUtils.lerp(from.z, to.z, t) + dx * sideways);
    }

    static float angle(float from, float to, float progress) {
        return MathUtils.lerp(from, to, smooth(progress));
    }

    static float angle(float from, float to, float progress, Route route) {
        return angle(from, to, route.approachesAtFeltHeight() ? route.airborne(progress) : progress);
    }

    /** Give even a shallow descent enough rendered frames to settle. */
    static float communityProgress(float progress, float seconds, float width, Route route) {
        if (progress <= 0f || progress >= 1f || seconds <= 0f || route.lift == 0f) return progress;
        float remaining = 1f - route.descendFrom;
        float falling = route.touchdown - route.descendFrom;
        if (falling <= 0f) return progress;
        float landingSeconds = .09f + .06f * Math.min(1f, route.lift / width);
        float reserve = Math.min(.46f, landingSeconds / seconds * remaining / falling);
        if (reserve <= remaining) return progress;
        float start = 1f - reserve;
        float beforeSpeed = route.descendFrom / start;
        float afterSpeed = remaining / reserve;
        // Match the horizontal and vertical velocities at the timing join.
        float speed = 2f * beforeSpeed * afterSpeed / (beforeSpeed + afterSpeed);
        if (progress < start) {
            return timedSegment(progress / start, 0f, route.descendFrom, beforeSpeed * start, speed * start);
        }
        return timedSegment((progress - start) / reserve, route.descendFrom, 1f, speed * reserve, afterSpeed * reserve);
    }

    private static float timedSegment(float t, float start, float end, float startSpeed, float endSpeed) {
        float t2 = t * t;
        return start + t2 * (3f - 2f * t) * (end - start)
                + t * (1f - t) * ((1f - t) * startSpeed - t * endSpeed);
    }

    static Route planCommunity(Vector3 from, Vector3 to, float width, float height,
            float startAngle, float endAngle, float restHeight, List<Pile> piles, Bounds bounds) {
        if (piles.isEmpty()) return new Route(0f, 0f, 0f, 0f, COMMUNITY_CONTACTS[0], true);

        var clearance = new CommunityClearance(from, to, width, height, startAngle, endAngle,
                restHeight, piles, bounds);
        Route best = null;
        float bestCost = Float.POSITIVE_INFINITY;
        for (float touchdown : COMMUNITY_CONTACTS) {
            float slideLength = clearance.distance() * (1f - communityTravel(touchdown));
            if (touchdown > COMMUNITY_CONTACTS[0] && slideLength < width * .16f) continue;
            Route direct = clearance.inspect(touchdown, null);
            float penalty = (touchdown - COMMUNITY_CONTACTS[0]) * width * .30f;
            if (direct != null && direct.lift <= width * MAX_DIRECT_COMMUNITY_LIFT
                    && direct.lift + penalty < bestCost) {
                best = direct;
                bestCost = direct.lift + penalty;
            }
        }
        if (best != null) return best;

        // Try entering the community row from either end. If that strip is
        // blocked too, approach from in front of or behind the target instead.
        Route across = clearance.curved(true);
        if (across != null) return across;
        Route depth = clearance.curved(false);
        if (depth != null) return depth;
        return plan(from, to, width, height, startAngle, endAngle, restHeight, piles, bounds);
    }

    private static float approachCoordinate(float start, float target, float offset,
            float progress, float touchdown) {
        if (progress >= touchdown) {
            float remaining = (1f - progress) / (1f - touchdown);
            return target + offset * remaining * remaining;
        }
        float q = progress / touchdown;
        float delta = target + offset - start;
        // The cubic's final tangent matches the speed and direction of the
        // flat, braking slide. The card reaches the felt without a turn or stop.
        float tangent = offset * Slide.speedFactor(touchdown);
        return start + q * delta + (delta - tangent) * q * q * (1f - q);
    }

    private record CommunityClearance(Vector3 from, Vector3 to, float width, float height,
            float startAngle, float endAngle, float restHeight, List<Pile> piles, Bounds bounds) {
        float distance() {
            return (float) Math.hypot(to.x - from.x, to.z - from.z);
        }

        Route curved(boolean across) {
            Route best = null;
            float bestCost = Float.POSITIVE_INFINITY;
            for (float amount : COMMUNITY_SLIDES) {
                float offset = amount * width;
                Slide slide = across ? new Slide(offset, 0f) : new Slide(0f, offset);
                Route route = inspect(CURVED_TOUCHDOWN, slide);
                if (route == null) continue;
                float cost = route.lift + slide.length() * .22f;
                if (cost < bestCost) {
                    best = route;
                    bestCost = cost;
                }
            }
            return best;
        }

        Route inspect(float touchdown, Slide slide) {
            float halfW = width * .5f, halfH = height * .5f;
            float corner = (float) Math.hypot(halfW, halfH);
            float distance = distance();
            float margin = Math.max(2f, width * .025f);
            float first = 1f, last = 0f, lift = 0f;
            for (int step = 0; step < SWEEP_STEPS; step++) {
                float p0 = (float) step / SWEEP_STEPS, p1 = (float) (step + 1) / SWEEP_STEPS;
                float mid = (p0 + p1) * .5f;
                float x, z, reach;
                if (slide == null) {
                    float t = communityTravel(mid);
                    x = MathUtils.lerp(from.x, to.x, t);
                    z = MathUtils.lerp(from.z, to.z, t);
                    reach = Math.max(t - communityTravel(p0), communityTravel(p1) - t) * distance;
                } else {
                    x = approachCoordinate(from.x, to.x, slide.x, mid, touchdown);
                    z = approachCoordinate(from.z, to.z, slide.z, mid, touchdown);
                    reach = approachReach(slide, p0, p1, touchdown);
                    if (!bounds.contains(x, z)) return null;
                }
                float q0 = Math.min(1f, p0 / touchdown), q1 = Math.min(1f, p1 / touchdown);
                float angle = angle(startAngle, endAngle, mid / touchdown);
                float cosine = MathUtils.cosDeg(angle), sine = MathUtils.sinDeg(angle);
                float rotation = Math.max(Math.abs(angle - angle(startAngle, endAngle, q0)),
                        Math.abs(angle(startAngle, endAngle, q1) - angle));
                reach += corner * rotation * MathUtils.degreesToRadians;
                float top = restHeight;
                for (Pile pile : piles) {
                    float px = pile.x - x, pz = pile.z - z;
                    float radius = pile.radius + reach;
                    if (px * px + pz * pz > (corner + radius) * (corner + radius)) continue;
                    float u = Math.max(0f, Math.abs(px * cosine + pz * sine) - halfW);
                    float v = Math.max(0f, Math.abs(-px * sine + pz * cosine) - halfH);
                    if (u * u + v * v <= radius * radius) top = Math.max(top, pile.top + margin);
                }
                if (top == restHeight) continue;
                float natural = restHeight + to.y + Math.min(launchHeight(from, to, q0),
                        launchHeight(from, to, q1));
                if (natural >= top) continue;
                if (p1 >= touchdown) return null;
                first = Math.min(first, p0);
                last = Math.max(last, p1);
                lift = Math.max(lift, top - natural);
            }
            if (lift > 0f) {
                float descent = MathUtils.clamp(.22f * lift / width, .055f, .18f);
                if (touchdown - last < descent) return null;
            } else {
                first = 0f;
            }
            return new Route(0f, lift, first, last, touchdown, slide == null, slide);
        }

        private float approachReach(Slide slide, float p0, float p1, float touchdown) {
            if (p0 >= touchdown) {
                return slide.length() * (1f - p0) * (p1 - p0) / ((1f - touchdown) * (1f - touchdown));
            }
            float q0 = p0 / touchdown, q1 = Math.min(1f, p1 / touchdown);
            float slope0 = q0 * (2f - 3f * q0);
            float slope1 = q1 * (2f - 3f * q1);
            float slopeMin = Math.min(slope0, slope1);
            float slopeMax = q0 <= 1f / 3f && q1 >= 1f / 3f ? 1f / 3f : Math.max(slope0, slope1);
            float dx = to.x + slide.x - from.x, dz = to.z + slide.z - from.z;
            float tx = dx - slide.x * Slide.speedFactor(touchdown);
            float tz = dz - slide.z * Slide.speedFactor(touchdown);
            // The velocity is affine in the quadratic slope. Its largest
            // magnitude over the interval must be at one of the slope extrema.
            double speed = Math.max(Math.hypot(dx + tx * slopeMin, dz + tz * slopeMin),
                    Math.hypot(dx + tx * slopeMax, dz + tz * slopeMax));
            return (float) speed * (p1 - p0) / (2f * touchdown);
        }
    }

    static Route plan(Vector3 from, Vector3 to, float width, float height, float startAngle,
            float endAngle, float restHeight, List<Pile> piles, Bounds bounds) {
        if (piles.isEmpty()) return DIRECT;
        Route best = inspect(from, to, width, height, startAngle, endAngle, restHeight, piles, 0f, bounds);
        if (best.lift() < width * .26f) return best;
        for (float detour : DETOURS) {
            float offset = detour * width;
            if (Math.abs(offset) * .40f >= best.cost()) continue;
            Route alternative = inspect(from, to, width, height, startAngle, endAngle, restHeight, piles, offset, bounds);
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
            // A delayed touchdown stretches the arc. Bound its height for
            // every admissible touchdown and over the whole sweep interval.
            float earliestPhase = p0;
            float latestPhase = airborne(p1);
            float launch = Math.min(launchHeight(from, to, earliestPhase),
                    launchHeight(from, to, latestPhase));
            float pulse = latestPhase == 1f ? 0f : Math.min(
                    MathUtils.sin(MathUtils.PI * earliestPhase),
                    MathUtils.sin(MathUtils.PI * latestPhase));
            float naturalHeight = to.y + launch + Math.min(width * .12f, distance * .018f) * pulse * pulse;
            float top = restHeight;
            for (Pile pile : piles) {
                float px = pile.x - x, pz = pile.z - z;
                float radius = pile.radius + motion;
                float broad = corner + radius;
                if (px * px + pz * pz > broad * broad) continue;
                float localX = Math.max(0f, Math.abs(px * cosine + pz * sine) - halfW);
                float localZ = Math.max(0f, Math.abs(-px * sine + pz * cosine) - halfH);
                if (localX * localX + localZ * localZ <= radius * radius) top = Math.max(top, pile.top + margin);
            }
            if (naturalHeight + restHeight < top) {
                first = Math.min(first, p0);
                last = Math.max(last, p1);
                lift = Math.max(lift, top - naturalHeight - restHeight);
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
        // Finish the detour in the air. The card approaches and slides on one
        // tangent after that, with zero sideways speed at the join.
        float u = Math.min(1f, travel / ALIGNMENT_TRAVEL);
        float p = 4f * u * (1f - u);
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
