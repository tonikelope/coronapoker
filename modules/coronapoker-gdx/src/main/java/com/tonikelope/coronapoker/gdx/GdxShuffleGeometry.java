/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.MathUtils;

/** Finite-thickness shuffle mesh. One world unit is the 88 mm length of a card. */
final class GdxShuffleGeometry {

    static final int CARDS_PER_PACKET = 26;
    static final int CARDS = CARDS_PER_PACKET * 2;
    static final float FRAME_SECONDS = .02f;
    static final int FRAME_COUNT = 86;
    static final float CYCLE_SECONDS = FRAME_COUNT * FRAME_SECONDS;
    private static final int FALL_FRAMES = 5;
    private static final int RIFFLE_LANDED = CARDS - 1 + FALL_FRAMES;
    private static final int BRIDGE_RELEASE_START = 84;
    private static final float BRIDGE_CARD_INTERVAL = 1.25f;
    private static final float BRIDGE_CARD_FALL = 4.25f;
    private static final float BRIDGE_LAST_LANDING =
            BRIDGE_RELEASE_START + (CARDS - 1) * BRIDGE_CARD_INTERVAL + BRIDGE_CARD_FALL;
    private static final int BRIDGE_PUSH_FRAMES = 18;
    private static final int BRIDGE_FRAMES = (int) Math.ceil(BRIDGE_LAST_LANDING) + BRIDGE_PUSH_FRAMES + 1;
    private static final float OPEN_BRIDGE_SUPPORT = .5f + spread(0);
    // At 43 degrees the lowest card lands with about 22 mm left to push.
    private static final float OPEN_BRIDGE_TURN = (float) Math.toRadians(43);
    private static final float OPEN_BRIDGE_CURVATURE = OPEN_BRIDGE_TURN / OPEN_BRIDGE_SUPPORT;
    private static final float OPEN_BRIDGE_HALF_CHORD = (float) Math.sin(OPEN_BRIDGE_TURN) / OPEN_BRIDGE_CURVATURE;
    // Over the open span, 45.6 degrees puts the crown halfway between the two bridges.
    private static final float MID_BRIDGE_TURN = (float) Math.toRadians(45.6);
    private static final float MID_BRIDGE_CURVATURE = MID_BRIDGE_TURN / OPEN_BRIDGE_SUPPORT;
    private static final float MID_BRIDGE_HALF_CHORD = (float) Math.sin(MID_BRIDGE_TURN) / MID_BRIDGE_CURVATURE;
    private static final int MID_BRIDGE_PAUSE_FRAMES = 6;

    // The Blender player started the WAV at the first release and cut it
    // at the start of frame 53 (52 intervals of 20 ms).
    static float soundStart(int cue) {
        return (Motion.MID_BRIDGE.leadFrames + (cue == 0 ? 0 : BRIDGE_RELEASE_START)) * FRAME_SECONDS;
    }

    static float soundEnd(int cue) {
        return cue == 0 ? soundStart(0) + CARDS * FRAME_SECONDS
                : (Motion.MID_BRIDGE.leadFrames + BRIDGE_LAST_LANDING) * FRAME_SECONDS;
    }

    enum Motion {
        ORIGINAL("Mezcla original", 0, FRAME_COUNT),
        FULL_DECK("Mazo entero", 46, FRAME_COUNT),
        BRIDGE("Mazo entero + puente", 46, BRIDGE_FRAMES),
        OPEN_BRIDGE("Puente abierto", 46, BRIDGE_FRAMES),
        MID_BRIDGE("Puente intermedio", 46, BRIDGE_FRAMES + MID_BRIDGE_PAUSE_FRAMES);

        final String label;
        final int leadFrames;

        final int releaseFrames;

        Motion(String label, int leadFrames, int releaseFrames) {
            this.label = label;
            this.leadFrames = leadFrames;
            this.releaseFrames = releaseFrames;
        }

        float duration() {
            return (leadFrames + releaseFrames) * FRAME_SECONDS;
        }

        float releaseTime(float elapsed) {
            return Math.max(0f, elapsed - leadFrames * FRAME_SECONDS);
        }

        boolean hasBridge() {
            return this == BRIDGE || this == OPEN_BRIDGE || this == MID_BRIDGE;
        }
    }

    private static final int PUSH_START = 65;
    private static final int PUSH_END = FRAME_COUNT - 1;
    private static final float WIDTH = 63f / 88f;
    private static final float THICKNESS = .27f / 88f;
    private static final float CORNER = 3.2f / 88f;
    private static final float HELD_BEND = 1.39f;
    private static final int ROUND_STRIPS = 6;
    static final int STRIPS = 42;
    static final int VERTICES_PER_STRIP = 8;
    static final int VERTICES_PER_CARD = (STRIPS + 1) * VERTICES_PER_STRIP;
    static final int INDICES_PER_CARD = (STRIPS * 4 + 2) * 6;
    static final int FLOATS_PER_VERTEX = 7;

    final float[] vertices = new float[CARDS * VERTICES_PER_CARD * FLOATS_PER_VERTEX];
    private final float[] alongCard = new float[STRIPS + 1];
    private final float[] halfWidths = new float[STRIPS + 1];
    private int writeOffset;

    GdxShuffleGeometry() {
        for (int s = 0; s <= STRIPS; s++) {
            int end = Math.min(s, STRIPS - s);
            float arc = end * MathUtils.HALF_PI / ROUND_STRIPS;
            float u = end < ROUND_STRIPS ? CORNER * (1f - MathUtils.cos(arc))
                    : MathUtils.lerp(CORNER, .5f, (end - ROUND_STRIPS) / (STRIPS * .5f - ROUND_STRIPS));
            alongCard[s] = s <= STRIPS / 2 ? u : 1f - u;
            halfWidths[s] = end < ROUND_STRIPS ? WIDTH * .5f - CORNER * (1f - MathUtils.sin(arc)) : WIDTH * .5f;
        }
    }

    static short[] indices() {
        short[] indices = new short[CARDS * INDICES_PER_CARD];
        int next = 0;
        for (int card = 0; card < CARDS; card++) {
            int first = card * VERTICES_PER_CARD;
            for (int strip = 0; strip < STRIPS; strip++) {
                int a = first + strip * VERTICES_PER_STRIP;
                int b = a + VERTICES_PER_STRIP;
                next = quad(indices, next, a, b, b + 1, a + 1);
                next = quad(indices, next, a + 3, b + 3, b + 2, a + 2);
                next = quad(indices, next, a + 5, b + 5, b + 4, a + 4);
                next = quad(indices, next, a + 6, b + 6, b + 7, a + 7);
            }
            int last = first + STRIPS * VERTICES_PER_STRIP;
            next = quad(indices, next, first + 4, first + 6, first + 7, first + 5);
            next = quad(indices, next, last + 5, last + 7, last + 6, last + 4);
        }
        return indices;
    }

    void update(float elapsed) {
        update(elapsed, Motion.ORIGINAL);
    }

    void update(float elapsed, Motion motion) {
        float leadFrame = elapsed / FRAME_SECONDS;
        if (leadFrame < motion.leadFrames) {
            writeOpening(leadFrame);
            return;
        }
        float frame = MathUtils.clamp(motion.releaseTime(elapsed) / FRAME_SECONDS, 0f, motion.releaseFrames - 1f);
        if (motion.hasBridge() && frame > RIFFLE_LANDED) {
            writeBridge(frame, motion);
            return;
        }
        float push = smooth((frame - PUSH_START) / (PUSH_END - PUSH_START));
        writeOffset = 0;
        for (int order = 0; order < CARDS; order++) writeCard(frame, push, order);
    }

    private void writeOpening(float frame) {
        // Cut two contiguous packets. Once their ends clear, curl them into
        // the original riffle grip. The bottom of the lower packet and the
        // top heel of the upper packet stay at their squared stack heights.
        float split = smooth((frame - 3f) / 17f);
        float curl = smooth((frame - 22f) / 21f);
        float approach = smooth((frame - 33f) / 13f);
        writeOffset = 0;
        for (int order = 0; order < CARDS; order++) {
            boolean upper = (order & 1) != 0;
            int layer = order / 2;
            float packed = (layer + .5f + (upper ? CARDS_PER_PACKET : 0)) * THICKNESS;
            float base = MathUtils.lerp(packed, riffleHeight(order), curl);
            float outer = MathUtils.lerp(.5f + .52f * split, .5f + spread(0), approach)
                    + (spread(layer) - spread(0)) * curl;
            writeBentCard(order, base, outer, HELD_BEND * curl);
        }
    }

    private static float riffleHeight(int order) {
        return (order + .5f) * THICKNESS;
    }

    private void writeBridge(float frame, Motion motion) {
        boolean open = motion != Motion.BRIDGE;
        float gather = smooth((frame - RIFFLE_LANDED) / (69f - RIFFLE_LANDED));
        float archStart = open ? RIFFLE_LANDED : 69f;
        float bow = smooth((frame - archStart) / (83f - archStart));
        float fullCurvature = open ? OPEN_BRIDGE_CURVATURE : 1.85f;
        float pushStart = BRIDGE_LAST_LANDING;
        if (motion == Motion.MID_BRIDGE) {
            fullCurvature = MID_BRIDGE_CURVATURE;
            pushStart += MID_BRIDGE_PAUSE_FRAMES;
        }
        float pushTime = MathUtils.clamp((frame - pushStart) / BRIDGE_PUSH_FRAMES, 0f, 1f);
        float square = pushTime * (2f - pushTime);
        writeOffset = 0;
        for (int order = 0; order < CARDS; order++) {
            // The underside lands first. Adjacent cards have overlapping
            // release windows, leaving a moving fan underneath the crown.
            float release = BRIDGE_RELEASE_START + order * BRIDGE_CARD_INTERVAL;
            float remaining = smooth((release + BRIDGE_CARD_FALL - frame) / BRIDGE_CARD_FALL);
            float curvature = fullCurvature * bow * remaining;
            float initialOffset = spread(order / 2);
            float offset, support;
            if (open) {
                // Bend in place. Arc length to the foot on the felt is
                // asin(k * chord) / k. Both open bridges land on the same chord.
                support = OPEN_BRIDGE_SUPPORT;
                if (remaining < 1f) {
                    float chord = motion == Motion.MID_BRIDGE
                            ? MathUtils.lerp(OPEN_BRIDGE_HALF_CHORD, MID_BRIDGE_HALF_CHORD, remaining)
                            : OPEN_BRIDGE_HALF_CHORD;
                    support = curvature == 0f ? chord
                            : (float) Math.asin(curvature * chord) / curvature;
                }
                offset = support - .5f + (initialOffset - spread(0)) * remaining;
            } else {
                offset = MathUtils.lerp(initialOffset, .13f, gather);
                support = .5f + offset;
            }
            offset *= 1f - square;
            float base = riffleHeight(order);
            if (curvature == 0f) writeBentCard(order, base, .5f + offset, 0f);
            else writeArchedCard(order, base, offset, support, curvature);
        }
    }

    private void writeArchedCard(int order, float base, float spread, float support, float curvature) {
        float direction = (order & 1) != 0 ? -1f : 1f;
        // Concentric sheets with their own radii slide over each other while
        // bending. Using arc length as the coordinate keeps every sheet 88 mm.
        float radius = 1f / curvature + base;
        float halfAngle = support * curvature * .5f;
        float rise = 2f / curvature * (float) Math.pow(Math.sin(halfAngle), 2);
        for (int s = 0; s <= STRIPS; s++) {
            float angle = direction * (.5f + spread - alongCard[s]) / radius;
            float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
            float halfSin = (float) Math.sin(angle * .5f);
            float x = radius * sin;
            float y = base + rise - 2f * radius * halfSin * halfSin;
            writeStrip(order, s, x, y, direction, direction * sin, cos);
        }
    }

    private static float spread(int layer) {
        return MathUtils.lerp(.325f, .405f, layer / (CARDS_PER_PACKET - 1f));
    }

    private void writeCard(float frame, float push, int order) {
        int layer = order / 2;
        float fall = smooth((frame - order) / FALL_FRAMES);
        float bend = HELD_BEND * (1f - fall);
        // Each outer end stays in its own interleaved slot throughout the release.
        float base = riffleHeight(order);
        float outer = .5f + spread(layer) * (1f - push);
        writeBentCard(order, base, outer, bend);
    }

    private void writeBentCard(int order, float base, float outer, float bend) {
        boolean left = (order & 1) != 0;
        float direction = left ? -1f : 1f;
        for (int s = 0; s <= STRIPS; s++) {
            float u = alongCard[s];
            float angle = u * bend;
            float sin = (float) Math.sin(angle);
            float cos = (float) Math.cos(angle);
            // Integrate a unit tangent along the neutral surface. The two skins
            // are always one material thickness apart along its local normal.
            float travel = bend > .0001f ? sin / bend : u;
            // Half-angle identity avoids cancellation near the end of a fall.
            float lift = travel * sin / (1f + cos);
            float x = direction * (outer - travel);
            float y = base + lift;
            writeStrip(order, s, x, y, direction, sin, cos);
        }
    }

    private void writeStrip(int order, int s, float x, float y, float direction, float sin, float cos) {
        int face = faceIndex(order);
        boolean left = direction < 0f;
        float u = alongCard[s];
        float half = THICKNESS * .5f;
        float nx = direction * sin * half;
        float ny = cos * half;
        float z = direction * halfWidths[s];
        float inset = .5f - halfWidths[s] / WIDTH;
        float shade = .84f + (left ? -.13f : .22f) * sin;
        float faceShade = .54f + .49f * sin;
        vertex(x + nx, y + ny, -z, inset, u, -1, shade);
        vertex(x + nx, y + ny, z, 1f - inset, u, -1, shade);
        vertex(x - nx, y - ny, -z, inset, 1f - u, face, faceShade);
        vertex(x - nx, y - ny, z, 1f - inset, 1f - u, face, faceShade);
        float edgeLight = .965f + .025f * (float) Math.cos((x + .42f) * 13f);
        vertex(x + nx, y + ny, -z, 0, 0, -2, edgeLight);
        vertex(x - nx, y - ny, -z, 0, 0, -2, edgeLight - .035f);
        vertex(x + nx, y + ny, z, 0, 0, -2, edgeLight);
        vertex(x - nx, y - ny, z, 0, 0, -2, edgeLight - .035f);
    }

    static int faceIndex(int order) {
        int layer = order / 2;
        boolean left = (order & 1) != 0;
        // The last lifted face in the Blender sequence is the ace of hearts.
        boolean firstHalf = layer == CARDS_PER_PACKET - 1 ? !left : left;
        return firstHalf ? layer : CARDS_PER_PACKET + layer;
    }

    private void vertex(float x, float y, float z, float u, float v, float material, float shade) {
        vertices[writeOffset++] = x;
        vertices[writeOffset++] = y;
        vertices[writeOffset++] = z;
        vertices[writeOffset++] = u;
        vertices[writeOffset++] = v;
        vertices[writeOffset++] = material;
        vertices[writeOffset++] = shade;
    }

    private static int quad(short[] indices, int next, int a, int b, int c, int d) {
        indices[next++] = (short) a;
        indices[next++] = (short) b;
        indices[next++] = (short) c;
        indices[next++] = (short) a;
        indices[next++] = (short) c;
        indices[next++] = (short) d;
        return next;
    }

    private static float smooth(float progress) {
        float t = MathUtils.clamp(progress, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
