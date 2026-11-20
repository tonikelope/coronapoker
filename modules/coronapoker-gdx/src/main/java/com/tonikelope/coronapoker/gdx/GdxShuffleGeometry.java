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
    private static final int PUSH_START = 65;
    private static final int PUSH_END = FRAME_COUNT - 1;
    private static final int FALL_FRAMES = 5;
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
        float frame = MathUtils.clamp(elapsed / FRAME_SECONDS, 0f, PUSH_END);
        float push = smooth((frame - PUSH_START) / (PUSH_END - PUSH_START));
        writeOffset = 0;
        for (int order = 0; order < CARDS; order++) writeCard(frame, push, order);
    }

    private void writeCard(float frame, float push, int order) {
        boolean left = (order & 1) != 0;
        float direction = left ? -1f : 1f;
        int layer = order / 2;
        float fall = smooth((frame - order) / FALL_FRAMES);
        float bend = HELD_BEND * (1f - fall);
        // Each outer end stays in its own interleaved slot throughout the release.
        float base = (order + .5f) * THICKNESS;
        float spread = MathUtils.lerp(.325f, .405f, layer / (CARDS_PER_PACKET - 1f));
        float outer = .5f + spread * (1f - push);
        int face = faceIndex(order);
        float half = THICKNESS * .5f;
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
