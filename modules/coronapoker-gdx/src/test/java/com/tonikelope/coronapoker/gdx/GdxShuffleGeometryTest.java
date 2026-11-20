/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static com.tonikelope.coronapoker.gdx.GdxShuffleGeometry.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class GdxShuffleGeometryTest {
    private final GdxShuffleGeometry mesh = new GdxShuffleGeometry();

    @Test
    void everyCardKeepsItsGaugeAndLengthThroughoutTheLoop() {
        for (int tick = 0; tick <= 340; tick++) {
            mesh.update(tick * .005f);
            for (int card = 0; card < CARDS; card++) {
                double length = 0, lastX = 0, lastY = 0;
                for (int strip = 0; strip <= STRIPS; strip++) {
                    int top = vertex(card, strip, 0), bottom = vertex(card, strip, 2);
                    double dx = mesh.vertices[top] - mesh.vertices[bottom];
                    double dy = mesh.vertices[top + 1] - mesh.vertices[bottom + 1];
                    assertEquals(.27, 88 * Math.hypot(dx, dy), .00002);
                    double x = (mesh.vertices[top] + mesh.vertices[bottom]) * .5;
                    double y = (mesh.vertices[top + 1] + mesh.vertices[bottom + 1]) * .5;
                    if (strip > 0) length += Math.hypot(x - lastX, y - lastY);
                    lastX = x;
                    lastY = y;
                }
                assertEquals(88, 88 * length, .02);
                assertEquals((card + 1) * .27, 88 * mesh.vertices[vertex(card, 0, 0) + 1], .00002);
            }
        }
    }

    @Test
    void oppositeTipsReleaseAFrameApartAndLandInSuccessiveSlots() {
        for (int card = 0; card < CARDS; card++) {
            int tip = vertex(card, STRIPS, 0) + 1;
            mesh.update(0f);
            float held = mesh.vertices[tip];
            mesh.update(card * FRAME_SECONDS);
            assertEquals(held, mesh.vertices[tip], .000001f);
            mesh.update((card + .5f) * FRAME_SECONDS);
            assertTrue(mesh.vertices[tip] < held);
            mesh.update((card + 5) * FRAME_SECONDS);
            assertEquals((card + 1) * .27, 88 * mesh.vertices[tip], .00002);
            assertEquals(card % 2 == 0, mesh.vertices[vertex(card, 0, 0)] > 0f);
        }
    }

    @Test
    void closureOnlySlidesTheFlatCardsAndLeavesAFourteenMillimetreDeck() {
        mesh.update(1.12f);
        float[] landed = mesh.vertices.clone();
        mesh.update(1.30f);
        assertArrayEquals(landed, mesh.vertices, .000001f);
        for (int phase = 0; phase <= 20; phase++) {
            mesh.update((65 + phase) * FRAME_SECONDS);
            for (int card = 0; card < CARDS; card++) {
                int outer = vertex(card, 0, 0), tip = vertex(card, STRIPS, 0);
                assertEquals(mesh.vertices[outer + 1], mesh.vertices[tip + 1], .000001f);
                assertEquals(landed[outer + 1], mesh.vertices[outer + 1], .000001f);
                if (phase == 20) {
                    assertEquals(.5, Math.abs(mesh.vertices[outer]), .000001);
                    assertEquals(-mesh.vertices[outer], mesh.vertices[tip], .000001f);
                }
            }
        }
        assertEquals(14.04, mesh.vertices[vertex(51, 0, 0) + 1] * 88, .00002);
        assertEquals(1.72f, CYCLE_SECONDS, .000001f);
    }

    @Test
    void fullDeckSeparatesIntoTwoFlatPacketsBeforeTheyBend() {
        var motion = Motion.FULL_DECK;
        double gauge = .27 / 88;
        for (int tick = 0; tick <= 88; tick++) {
            float frame = tick * .25f;
            mesh.update(frame * FRAME_SECONDS, motion);
            for (int order = 0; order < CARDS; order++) {
                int root = vertex(order, 0, 0), tip = vertex(order, STRIPS, 0);
                assertEquals(mesh.vertices[root + 1], mesh.vertices[tip + 1], .000001);
                assertEquals(1, Math.abs(mesh.vertices[root] - mesh.vertices[tip]), .000001);
                if (order >= 2) assertEquals(gauge,
                        mesh.vertices[root + 1] - mesh.vertices[vertex(order - 2, 0, 0) + 1], .000001);
                if (frame == 0) {
                    int slot = order / 2 + ((order & 1) == 1 ? CARDS_PER_PACKET : 0);
                    assertEquals((slot + 1) * gauge, mesh.vertices[root + 1], .000001);
                    assertEquals(.5, Math.abs(mesh.vertices[root]), .000001);
                }
            }
            if (frame >= 20) assertTrue(mesh.vertices[vertex(0, STRIPS, 0)]
                    - mesh.vertices[vertex(1, STRIPS, 0)] > .039f);
        }
    }

    @Test
    void extendedCutsEnterTheOriginalGripAndReuseEveryRifflePose() {
        var original = new GdxShuffleGeometry();
        for (Motion motion : new Motion[]{Motion.FULL_DECK, Motion.BRIDGE}) {
            for (int tick = 0; tick < motion.leadFrames * 4; tick++) {
                mesh.update(tick * .25f * FRAME_SECONDS, motion);
                assertEquals(0f, mesh.vertices[vertex(0, 0, 2) + 1], .000001f);
                assertEquals(14.04, mesh.vertices[vertex(51, 0, 0) + 1] * 88, .00002);
            }
            original.update(0f);
            mesh.update(Math.nextDown(motion.leadFrames * FRAME_SECONDS), motion);
            assertArrayEquals(original.vertices, mesh.vertices, .000002f);
            int sharedFrames = motion == Motion.FULL_DECK ? FRAME_COUNT - 1 : CARDS - 1 + 5;
            for (int tick = 0; tick <= sharedFrames * 4; tick++) {
                float frame = tick * .25f;
                original.update(frame * FRAME_SECONDS);
                mesh.update((motion.leadFrames + frame) * FRAME_SECONDS, motion);
                assertArrayEquals(original.vertices, mesh.vertices, .000006f);
            }
        }
    }

    @Test
    void fullDeckRetainsItsGaugeAndClosesAtTheOriginalReleaseCadence() {
        var motion = Motion.FULL_DECK;
        for (int tick = 0; tick < (motion.leadFrames + FRAME_COUNT) * 2; tick++) {
            mesh.update(tick * FRAME_SECONDS * .5f, motion);
            for (int order = 0; order < CARDS; order++) {
                int tip = vertex(order, STRIPS, 0), under = vertex(order, STRIPS, 2);
                assertEquals(.27, 88 * Math.hypot(mesh.vertices[tip] - mesh.vertices[under],
                        mesh.vertices[tip + 1] - mesh.vertices[under + 1]), .00002);
                if (tick >= 2 * (motion.leadFrames + order + 5)) {
                    assertEquals((order + 1) * .27, 88 * mesh.vertices[tip + 1], .00003);
                }
            }
        }
        mesh.update(motion.duration(), motion);
        float[] squared = mesh.vertices.clone();
        mesh.update(CYCLE_SECONDS, Motion.ORIGINAL);
        assertArrayEquals(squared, mesh.vertices, .000001f);
    }

    @Test
    void bridgeBendsTheInterleavedDeckAndReleasesFromTheBottomBeforeSquaring() {
        var motion = Motion.BRIDGE;
        float[] held = new float[CARDS];
        for (int frame = 0; frame < motion.releaseFrames; frame++) {
            mesh.update((motion.leadFrames + frame) * FRAME_SECONDS, motion);
            for (int order = 0; order < CARDS; order++) {
                int root = vertex(order, 0, 0);
                int middle = vertex(order, STRIPS / 2, 0);
                float rise = mesh.vertices[middle + 1] - mesh.vertices[root + 1];
                if (frame == 83) {
                    assertTrue(rise > .20f);
                    held[order] = rise;
                }
                if (frame >= 84 && frame <= 152) {
                    float sinceRelease = frame - (84f + 1.25f * order);
                    if (sinceRelease <= 0f) assertEquals(held[order], rise, .000002f);
                    else if (sinceRelease >= 4.25f) assertEquals(0f, rise, .000002f);
                    else assertTrue(rise > 0f && rise < held[order]);
                    if (order == 0 && frame >= 89) {
                        assertEquals(.63f, Math.abs(mesh.vertices[root]), .000001f);
                    }
                }
                double length = 0, lastX = 0, lastY = 0;
                for (int strip = 0; strip <= STRIPS; strip++) {
                    int t = vertex(order, strip, 0), b = vertex(order, strip, 2);
                    assertTrue(mesh.vertices[b + 1] >= -.000001f);
                    double dx = mesh.vertices[t] - mesh.vertices[b];
                    double dy = mesh.vertices[t + 1] - mesh.vertices[b + 1];
                    assertEquals(.27, 88 * Math.hypot(dx, dy), .00003);
                    double x = (mesh.vertices[t] + mesh.vertices[b]) * .5;
                    double y = (mesh.vertices[t + 1] + mesh.vertices[b + 1]) * .5;
                    if (strip > 0) length += Math.hypot(x - lastX, y - lastY);
                    lastX = x;
                    lastY = y;
                }
                assertEquals(88, 88 * length, .02);
            }
        }
        float[] finished = mesh.vertices.clone();
        mesh.update(CYCLE_SECONDS, Motion.ORIGINAL);
        assertArrayEquals(finished, mesh.vertices, .000001f);

        // The last sheet touches down 152 frames after the riffle begins.
        // Inspect both sides of that instant, inside a single display frame.
        float landing = motion.leadFrames + 152f;
        mesh.update((landing - .125f) * FRAME_SECONDS, motion);
        assertTrue(mesh.vertices[vertex(51, STRIPS / 2, 0) + 1] > mesh.vertices[vertex(51, 0, 0) + 1]);
        assertEquals(.63f, mesh.vertices[vertex(0, 0, 0)], .000001f);
        mesh.update(landing * FRAME_SECONDS, motion);
        assertEquals(mesh.vertices[vertex(51, 0, 0) + 1], mesh.vertices[vertex(51, STRIPS / 2, 0) + 1], .000001f);
        assertEquals(.63f, mesh.vertices[vertex(0, 0, 0)], .000001f);
        mesh.update((landing + .125f) * FRAME_SECONDS, motion);
        assertTrue(mesh.vertices[vertex(0, 0, 0)] < .629f);
    }

    private static int vertex(int card, int strip, int corner) {
        return (card * VERTICES_PER_CARD + strip * VERTICES_PER_STRIP + corner) * FLOATS_PER_VERTEX;
    }
}
