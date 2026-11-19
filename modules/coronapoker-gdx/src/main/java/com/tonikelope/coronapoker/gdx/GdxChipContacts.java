/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.IntMap;
import java.util.ArrayList;
import java.util.Arrays;

/** Contacts for the rendered solid chips, in mesh coordinates (Y up). */
final class GdxChipContacts {
    private static final int DISCS_PER_BLOCK = 16;

    static final class Track {
        private long frame;
        private float remaining;
        private final Vector3 center = new Vector3(), normal = new Vector3(), offset = new Vector3();
    }

    private static final class Disc {
        final Vector3 center = new Vector3(), normal = new Vector3();
        final Vector3 previous = new Vector3(), previousNormal = new Vector3();
        boolean hasPrevious;
        Disc earlierOnPile;
        float verticalExtent;
    }

    private final ArrayList<Disc> discs = new ArrayList<>();
    private final ArrayList<Block> blocks = new ArrayList<>();
    private final IntMap<Track> layers = new IntMap<>();
    private final IntMap<Disc> arrivals = new IntMap<>();
    private final Quaternion rotation = new Quaternion();
    private final Vector3 wanted = new Vector3(), delta = new Vector3(), oldDelta = new Vector3();
    private final Vector3 axis = new Vector3(), push = new Vector3(), entryNormal = new Vector3();
    private final Vector3 preferred = new Vector3(), ray = new Vector3(), best = new Vector3();
    private float[] starts = new float[64], ends = new float[64];
    private int[] neighbors = new int[64];
    private int neighborCount;
    private float low, high, bestDistance;
    private float radius, half, reach, reach2, skin, retention;
    private int count;
    private long frame, lastTime;
    private float entry, exit, depth, entryLimit;
    private boolean overlap, tracking;

    void begin(float diameter, long nanos) {
        if (radius != diameter * .5f) frame++; // Coordinates before a resize belong to a different table.
        radius = diameter * .5f;
        half = GdxTableChipRack.thickness(diameter) * .5f;
        reach2 = 4f * (radius * radius + half * half);
        reach = Math.nextUp((float) Math.sqrt(reach2));
        skin = diameter * .00002f;
        retention = (float) Math.exp(-Math.max(0L, nanos - lastTime) / 120_000_000d);
        lastTime = nanos;
        frame++;
        count = 0;
        arrivals.clear();
    }

    private Disc next() {
        if (count == discs.size()) discs.add(new Disc());
        return discs.get(count++);
    }

    private void recordBounds(Disc disc) {
        int i = count - 1;
        int blockIndex = i / DISCS_PER_BLOCK;
        if (blockIndex == blocks.size()) blocks.add(new Block());
        Block block = blocks.get(blockIndex);
        if (i % DISCS_PER_BLOCK == 0) {
            block.min.set(disc.center);
            block.max.set(disc.center);
        } else {
            block.include(disc.center);
        }
        if (disc.hasPrevious) block.include(disc.previous);
    }

    /** Conservative bounds including both ends of every sweep in arrival order. */
    private static final class Block {
        final Vector3 min = new Vector3(), max = new Vector3();

        void include(Vector3 point) {
            min.set(Math.min(min.x, point.x), Math.min(min.y, point.y), Math.min(min.z, point.z));
            max.set(Math.max(max.x, point.x), Math.max(max.y, point.y), Math.max(max.z, point.z));
        }

        boolean intersects(Vector3 now, Vector3 previous, float reach) {
            return intersects(now.y, previous.y, min.y, max.y, reach)
                    && intersects(now.x, previous.x, min.x, max.x, reach)
                    && intersects(now.z, previous.z, min.z, max.z, reach);
        }

        private static boolean intersects(float now, float previous, float min, float max, float reach) {
            return Math.min(now, previous) - max <= reach && Math.max(now, previous) - min >= -reach;
        }
    }

    void standing(int layer, float x, float tableY, float bottom) {
        Disc d = next();
        d.center.set(x, bottom + half, -tableY);
        d.normal.set(Vector3.Y);
        d.verticalExtent = half;
        Track track = layers.get(layer);
        if (track == null) {
            track = new Track();
            layers.put(layer, track);
        }
        // A slot reassigned to a different pile is a new obstacle. Ordinary
        // landing sway is small and follows the same layer between frames.
        d.hasPrevious = track.frame != 0 && track.frame == frame - 1
                && track.center.y == d.center.y && track.center.dst2(d.center) < radius * radius * .04f;
        d.previous.set(track.center);
        d.previousNormal.set(d.normal);
        track.center.set(d.center);
        track.frame = frame;
        recordBounds(d);
    }

    void place(GdxTableChipFlight.Pose pose, Track track, float progress, int landingPile) {
        Disc d = next();
        d.earlierOnPile = landingPile >= 0 ? arrivals.get(landingPile) : null;
        pose.attitude(rotation);
        rotation.transform(d.normal.set(Vector3.Y)).nor();
        d.verticalExtent = support(d.normal, 0f, 1f, 0f);
        wanted.set(pose.x, pose.centerHeight(radius, half, d.normal.y), -pose.y);
        d.center.set(wanted);
        d.hasPrevious = track != null && track.frame == frame - 1 && track.frame != 0;
        float remaining = Math.max(0f, 1f - progress);
        if (d.hasPrevious) {
            d.previous.set(track.center);
            d.previousNormal.set(track.normal);
            float ratio = remaining / Math.max(.000001f, track.remaining);
            float release = Math.min(retention, ratio * ratio * ratio);
            d.center.mulAdd(track.offset, release);
        }

        // Earlier arrivals own their contact first. A later placement is checked
        // against every already fixed solid, including any touched by a slide.
        float floor = d.verticalExtent;
        // Near a shared landing the reserved layer below must stay below.
        // A sloped envelope brings the rim up continuously from the side.
        // It collapses to one chip thickness when both chips lie flat.
        if (d.earlierOnPile != null && progress > .65f) {
            float blend = Math.min(1f, (progress - .65f) / .35f);
            blend *= blend * (3f - 2f * blend);
            boolean displaced = d.hasPrevious && !track.offset.isZero(skin);
            // Only earlier arrivals to this pile can support the landing.
            for (Disc lower = d.earlierOnPile; lower != null; lower = lower.earlierOnPile) {
                if (displaced || contact(d, lower, false)) {
                    float above = landingFloor(d, lower);
                    floor = Math.max(floor, d.center.y + blend * (above - d.center.y));
                }
            }
        }
        d.center.y = Math.max(floor, d.center.y);
        preferred.set(d.center);
        boolean settled = false;
        for (int pass = 0; pass < 2 && !settled; pass++) {
            settled = true;
            Vector3 previous = pass == 0 && d.hasPrevious ? d.previous : d.center;
            scan:
            for (int start = 0; start < count - 1; start += DISCS_PER_BLOCK) {
                if (!blocks.get(start / DISCS_PER_BLOCK).intersects(d.center, previous, reach)) continue;
                int end = Math.min(count - 1, start + DISCS_PER_BLOCK);
                for (int i = start; i < end; i++) {
                    if (contact(d, discs.get(i), pass == 0)) {
                        if (!settled || pass > 0) {
                            findSpace(d, floor);
                            settled = true;
                            break scan;
                        }
                        d.center.add(push);
                        d.center.y = Math.max(floor, d.center.y);
                        settled = false;
                    }
                }
            }
        }
        delta.set(d.center).sub(wanted);
        pose.x += delta.x;
        pose.height += delta.y;
        pose.y -= delta.z;
        if (track != null) {
            track.center.set(d.center);
            track.normal.set(d.normal);
            track.offset.set(delta);
            track.remaining = remaining;
            track.frame = frame;
        }
        if (landingPile >= 0) arrivals.put(landingPile, d);
        recordBounds(d);
    }

    private float landingFloor(Disc d, Disc lower) {
        float dx = d.center.x - lower.center.x, dz = d.center.z - lower.center.z;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        float top = lower.center.y + lower.verticalExtent + d.verticalExtent;
        if (distance == 0f) return top;
        // Either the upper or the oblique support plane suffices to separate
        // the two discs. At a distance the oblique plane is below the felt.
        float y = .4472136f, radial = .8944272f / distance;
        float x = dx * radial, z = dz * radial;
        float support = support(lower.normal, x, y, z) + support(d.normal, x, y, z);
        return Math.min(top, lower.center.y + (support - distance * .8944272f) / y);
    }

    /** A cluster can invalidate a pairwise slide. Intersect candidate lines with all
     * support slabs and choose the nearest free interval to the advected pose. */
    private void findSpace(Disc d, float floor) {
        bestDistance = Float.POSITIVE_INFINITY;
        if (starts.length < count) {
            starts = new float[count * 2];
            ends = new float[starts.length];
            neighbors = new int[starts.length];
        }
        // The vertical solution is always finite. A better solution cannot
        // reach a disc beyond that displacement plus the two bounding radii.
        searchLine(d, floor, Vector3.Y, true);
        float range = bestDistance + reach + 2f * skin;
        neighborCount = 0;
        for (int i = 0; i < count - 1; i++) {
            if (preferred.dst2(discs.get(i).center) <= range * range) neighbors[neighborCount++] = i;
        }
        searchLine(d, floor, d.normal);
        searchLine(d, floor, push);
        if (d.hasPrevious) {
            searchLine(d, floor, axis.set(preferred).sub(wanted));
            searchLine(d, floor, axis.set(d.previous).sub(preferred));
        }
        for (int i = 0; i < 4; i++) {
            float angle = i * (float) Math.PI / 4f;
            float x = (float) Math.cos(angle), z = (float) Math.sin(angle);
            searchLine(d, floor, axis.set(x, 0f, z));
            searchLine(d, floor, axis.set(x, .5f, z));
            searchLine(d, floor, axis.set(x, -.5f, z));
        }
        d.center.set(best);
    }

    private void searchLine(Disc d, float floor, Vector3 direction) {
        searchLine(d, floor, direction, false);
    }

    private void searchLine(Disc d, float floor, Vector3 direction, boolean allDiscs) {
        if (direction.len2() < .000001f) return;
        ray.set(direction).nor();
        int intervals = 0;
        float floorLimit = floor - preferred.y;
        float min = ray.y > 0f ? floorLimit / ray.y : Float.NEGATIVE_INFINITY;
        float max = ray.y < 0f ? floorLimit / ray.y : Float.POSITIVE_INFINITY;
        int candidates = allDiscs ? count - 1 : neighborCount;
        for (int i = 0; i < candidates; i++) {
            Disc other = discs.get(allDiscs ? i : neighbors[i]);
            delta.set(preferred).sub(other.center);
            float along = delta.dot(ray);
            if (axis.set(delta).mulAdd(ray, -along).len2() > reach2 + skin * radius * 8f) continue;
            low = Math.max(min - skin, -along - radius * 2.1f);
            high = Math.min(max + skin, -along + radius * 2.1f);
            if (low >= high || low > bestDistance || high < -bestDistance) continue;
            if (!clipLine(d, other, Vector3.Y) || !clipLine(d, other, d.normal)
                    || !clipLine(d, other, other.normal) || !clipLine(d, other, axis.set(d.normal).crs(other.normal))
                    || !clipLine(d, other, axis.set(delta.x, 0f, delta.z)) || !clipLine(d, other, delta)) continue;
            // Sorting starts and ends independently still gives the union. A
            // gap opens whenever the number of entered intervals returns to zero.
            starts[intervals] = low;
            ends[intervals++] = high;
        }
        Arrays.sort(starts, 0, intervals);
        Arrays.sort(ends, 0, intervals);
        float free = min;
        for (int i = 0; i < intervals; i++) {
            if (i == 0 || starts[i] >= ends[i - 1]) consider(free, Math.min(max, starts[i]));
            free = Math.max(free, ends[i]);
        }
        consider(free, max);
    }

    private void consider(float from, float to) {
        if (from > to) return;
        float shift = Math.max(from, Math.min(to, 0f));
        if (Math.abs(shift) < bestDistance) {
            bestDistance = Math.abs(shift);
            best.set(preferred).mulAdd(ray, shift);
        }
    }

    private boolean clipLine(Disc a, Disc b, Vector3 direction) {
        float len2 = direction.len2();
        if (len2 < .00000001f) return true;
        float inv = 1f / (float) Math.sqrt(len2);
        float x = direction.x * inv, y = direction.y * inv, z = direction.z * inv;
        float center = delta.dot(x, y, z), speed = ray.dot(x, y, z);
        float extent = support(a.normal, x, y, z) + support(b.normal, x, y, z) + skin;
        if (Math.abs(speed) < .0000001f) return Math.abs(center) < extent;
        float t1 = (-extent - center) / speed, t2 = (extent - center) / speed;
        low = Math.max(low, Math.min(t1, t2));
        high = Math.min(high, Math.max(t1, t2));
        return low < high;
    }

    private boolean contact(Disc a, Disc b, boolean sweep) {
        tracking = sweep && a.hasPrevious && b.hasPrevious;
        float dy = a.center.y - b.center.y;
        if (tracking) {
            // A whole swept segment on the same side of any bounding slab
            // cannot enter the circumsphere, even if the two chips rotate.
            float oldY = a.previous.y - b.previous.y;
            if (outsideSameSlab(dy, oldY)) return false;
            float dx = a.center.x - b.center.x, oldX = a.previous.x - b.previous.x;
            if (outsideSameSlab(dx, oldX)) return false;
            float dz = a.center.z - b.center.z, oldZ = a.previous.z - b.previous.z;
            if (outsideSameSlab(dz, oldZ)) return false;
            delta.set(dx, dy, dz);
            oldDelta.set(oldX, oldY, oldZ);
            axis.set(delta).sub(oldDelta);
            float len2 = axis.len2();
            float t = len2 == 0f ? 0f : Math.max(0f, Math.min(1f, -oldDelta.dot(axis) / len2));
            if (axis.scl(t).add(oldDelta).len2() >= reach2) return false;
        } else {
            if (dy * dy >= reach2) return false;
            delta.set(a.center.x - b.center.x, dy, a.center.z - b.center.z);
            if (delta.len2() >= reach2) return false;
        }

        entry = Float.NEGATIVE_INFINITY;
        exit = 1f;
        overlap = true;
        depth = Float.POSITIVE_INFINITY;
        if (!slab(Vector3.Y, a, b) || !slab(a.normal, a, b) || !slab(b.normal, a, b)
                || !slab(axis.set(a.normal).crs(b.normal), a, b)
                || !slab(axis.set(delta.x, 0f, delta.z), a, b) || !slab(delta, a, b)) return false;
        if (tracking && (!slab(a.previousNormal, a, b) || !slab(b.previousNormal, a, b)
                || !slab(oldDelta, a, b))) return false;
        if (!overlap) {
            // Both endpoints can be clear after a dropped frame. Slide along the
            // entry face instead of letting the chips exchange sides through it.
            if (!tracking || !Float.isFinite(entry) || entry >= exit) return false;
            push.set(entryNormal).scl(entryLimit - delta.dot(entryNormal) + skin);
        }
        return true;
    }

    private boolean outsideSameSlab(float now, float previous) {
        return (now > reach && previous > reach) || (now < -reach && previous < -reach);
    }

    /** Clip relative motion against the support slabs of the two oriented cylinders. */
    private boolean slab(Vector3 direction, Disc a, Disc b) {
        float len2 = direction.len2();
        if (len2 < .00000001f) return true;
        float inv = 1f / (float) Math.sqrt(len2);
        float x = direction.x * inv, y = direction.y * inv, z = direction.z * inv;
        float end = delta.dot(x, y, z);
        float limit = support(a.normal, x, y, z) + support(b.normal, x, y, z);
        float gap = Math.abs(end) - limit;
        if (gap >= -skin * .1f) {
            overlap = false;
            if (!tracking) return false;
        }
        float sign = end < 0f ? -1f : 1f;
        if (tracking) {
            float start = oldDelta.dot(x, y, z);
            float oldLimit = support(a.previousNormal, x, y, z) + support(b.previousNormal, x, y, z);
            for (int side = -1; side <= 1; side += 2) {
                float g0 = side * start - oldLimit, g1 = side * end - limit;
                if (g0 >= -skin * .1f && g1 >= -skin * .1f) return false;
                if (g0 >= -skin * .1f && g1 < g0) {
                    float t = Math.max(0f, g0 / (g0 - g1));
                    if (t > entry) {
                        entry = t;
                        entryNormal.set(x * side, y * side, z * side);
                        entryLimit = limit;
                    }
                } else if (g1 > 0f && g0 < g1) {
                    exit = Math.min(exit, g0 / (g0 - g1));
                }
            }
            if (entry >= exit) return false;
            // Retain the incoming side through coincident centres.
            if (Math.abs(start) >= oldLimit - skin) sign = start < 0f ? -1f : 1f;
        }
        float distance = limit - sign * end;
        // Do not select a displacement that would immediately be undone by the felt.
        if (distance < depth && a.center.y + sign * y * (distance + skin) >= a.verticalExtent - skin) {
            depth = distance;
            push.set(sign * x, sign * y, sign * z).scl(distance + skin);
        } else if (!Float.isFinite(depth)) {
            depth = Float.MAX_VALUE;
            push.set(0f, b.center.y + b.verticalExtent + a.verticalExtent - a.center.y + skin, 0f);
        }
        return true;
    }

    private float support(Vector3 n, float x, float y, float z) {
        // The cross product retains the rim's projected width even when the
        // normals are almost parallel and their float dot rounds to one.
        float cx = n.y * z - n.z * y, cy = n.z * x - n.x * z, cz = n.x * y - n.y * x;
        return radius * (float) Math.sqrt(cx * cx + cy * cy + cz * cz) + half * Math.abs(n.dot(x, y, z));
    }
}
