/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static com.tonikelope.coronapoker.gdx.GdxShuffleGeometry.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class GdxShuffleGeometryTest {
    private final GdxShuffleGeometry mesh = new GdxShuffleGeometry();

    @Test void selectedArtworkKeepsTheSameGaugeAsACommunityCard() {
        for (float aspect : new float[]{.60f, STANDARD_ASPECT, .80f}) {
            float millimetresPerMeshUnit = 63f / aspect;
            for (float time : new float[]{0f, .8f, 1.5f, 2.8f, 3.5f, Motion.MID_BRIDGE.duration()}) {
                mesh.update(time, Motion.MID_BRIDGE, aspect, true);
                for (int card = 0; card < CARDS; card++) {
                    for (int strip = 0; strip <= STRIPS; strip++) {
                        int top = vertex(card, strip, 0), bottom = vertex(card, strip, 2);
                        double thickness = Math.hypot(mesh.vertices[top] - mesh.vertices[bottom],
                                mesh.vertices[top + 1] - mesh.vertices[bottom + 1]);
                        assertEquals(.32, millimetresPerMeshUnit * thickness, .00003);
                    }
                }
            }
            assertEquals(52 * .32, millimetresPerMeshUnit * mesh.vertices[vertex(51, 0, 0) + 1], .00003);
        }
    }

    @Test void shuffleCornersMatchTheRestingCardsInEachView() {
        var mesh = new GdxShuffleGeometry();
        for (float aspect : new float[]{.60f, STANDARD_ASPECT, .80f}) {
            for (boolean perspective : new boolean[]{true, false, true}) {
                mesh.update(0f, Motion.MID_BRIDGE, aspect, perspective);
                int tip = vertex(0, 0, 0);
                assertEquals(.075f, .5f - Math.abs(mesh.vertices[tip + 2]) / aspect, .00001f);
                int shoulder = 1;
                while (shoulder < STRIPS / 2
                        && Math.abs(mesh.vertices[vertex(0, shoulder, 0) + 2]) < aspect * .5f - .000001f) {
                    shoulder++;
                }
                float longitudinalRadius = Math.abs(mesh.vertices[vertex(0, shoulder, 0)] - mesh.vertices[tip]);
                assertEquals(perspective ? .075f * aspect : .075f, longitudinalRadius, .00001f);
            }
        }
    }

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
                    assertEquals(.32, 88 * Math.hypot(dx, dy), .00002);
                    double x = (mesh.vertices[top] + mesh.vertices[bottom]) * .5;
                    double y = (mesh.vertices[top + 1] + mesh.vertices[bottom + 1]) * .5;
                    if (strip > 0) length += Math.hypot(x - lastX, y - lastY);
                    lastX = x;
                    lastY = y;
                }
                assertEquals(88, 88 * length, .02);
                assertEquals((card + 1) * .32, 88 * mesh.vertices[vertex(card, 0, 0) + 1], .00002);
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
            assertEquals((card + 1) * .32, 88 * mesh.vertices[tip], .00002);
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
        assertEquals(16.64, mesh.vertices[vertex(51, 0, 0) + 1] * 88, .00002);
        assertEquals(1.72f, CYCLE_SECONDS, .000001f);
    }

    @Test
    void fullDeckSeparatesIntoTwoFlatPacketsBeforeTheyBend() {
        var motion = Motion.FULL_DECK;
        double gauge = .32 / 88;
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
        for (Motion motion : new Motion[]{Motion.FULL_DECK, Motion.BRIDGE, Motion.OPEN_BRIDGE, Motion.MID_BRIDGE}) {
            for (int tick = 0; tick < motion.leadFrames * 4; tick++) {
                mesh.update(tick * .25f * FRAME_SECONDS, motion);
                assertEquals(0f, mesh.vertices[vertex(0, 0, 2) + 1], .000001f);
                assertEquals(16.64, mesh.vertices[vertex(51, 0, 0) + 1] * 88, .00002);
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
                assertEquals(.32, 88 * Math.hypot(mesh.vertices[tip] - mesh.vertices[under],
                        mesh.vertices[tip + 1] - mesh.vertices[under + 1]), .00002);
                if (tick >= 2 * (motion.leadFrames + order + 5)) {
                    assertEquals((order + 1) * .32, 88 * mesh.vertices[tip + 1], .00003);
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
                    assertEquals(.32, 88 * Math.hypot(dx, dy), .00003);
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

    @Test
    void openBridgeStartsAtTheRiffleLandingAndLetsEachSheetSettleBeforeThePush() {
        checkOpenBridge(Motion.OPEN_BRIDGE, 0);
    }

    @Test
    void intermediateBridgeSplitsTheCrownHeightsAndHoldsTheOpenLandingForSixFrames() {
        checkOpenBridge(Motion.MID_BRIDGE, 6);
        float low = crownHeight(Motion.OPEN_BRIDGE);
        float high = crownHeight(Motion.BRIDGE);
        float middle = crownHeight(Motion.MID_BRIDGE);
        assertTrue(low < middle && middle < high);
        assertEquals((low + high) * .5f, middle, .04f); // millimetres

        var open = new GdxShuffleGeometry();
        int landing = Motion.OPEN_BRIDGE.leadFrames + 152;
        open.update(landing * FRAME_SECONDS, Motion.OPEN_BRIDGE);
        for (int tick = 0; tick <= 24; tick++) {
            mesh.update((landing + tick * .25f) * FRAME_SECONDS, Motion.MID_BRIDGE);
            assertArrayEquals(open.vertices, mesh.vertices, .000001f);
        }
        float[] landed = open.vertices.clone();
        open.update(Motion.OPEN_BRIDGE.duration(), Motion.OPEN_BRIDGE);
        for (int tick = 0; tick <= 72; tick++) {
            mesh.update((landing + 6 + tick * .25f) * FRAME_SECONDS, Motion.MID_BRIDGE);
            float t = tick / 72f;
            float fraction = t * t * (3f - 2f * t);
            for (int i = 0; i < landed.length; i += FLOATS_PER_VERTEX) {
                float expectedX = landed[i] + fraction * (open.vertices[i] - landed[i]);
                assertEquals(expectedX, mesh.vertices[i], .000002f);
                assertEquals(landed[i + 1], mesh.vertices[i + 1], .000002f);
                assertEquals(landed[i + 2], mesh.vertices[i + 2], .000002f);
            }
        }
    }

    private float crownHeight(Motion motion) {
        mesh.update((motion.leadFrames + 83) * FRAME_SECONDS, motion);
        float top = 0f;
        for (int strip = 0; strip <= STRIPS; strip++) {
            top = Math.max(top, mesh.vertices[vertex(CARDS - 1, strip, 0) + 1]);
        }
        return top * 88f;
    }

    private void checkOpenBridge(Motion motion, int pauseFrames) {
        assertEquals(Motion.BRIDGE.duration() + pauseFrames * FRAME_SECONDS, motion.duration(), .000001f);
        mesh.update((motion.leadFrames + 56) * FRAME_SECONDS, motion);
        float[] flat = mesh.vertices.clone();
        mesh.update((motion.leadFrames + 57) * FRAME_SECONDS, motion);
        int centre = vertex(0, STRIPS / 2, 0);
        assertTrue(mesh.vertices[centre + 1] > flat[centre + 1] + .0005f);
        assertEquals(flat[vertex(0, 0, 0)], mesh.vertices[vertex(0, 0, 0)], .00002f);

        mesh.update((motion.leadFrames + 84) * FRAME_SECONDS, motion);
        float[] arch = mesh.vertices.clone();
        for (int tick = 0; tick <= (152 - 84) * 4; tick++) {
            float frame = 84 + tick * .25f;
            mesh.update((motion.leadFrames + frame) * FRAME_SECONDS, motion);
            for (int card = 0; card < CARDS; card++) {
                int root = vertex(card, 0, 0), middle = vertex(card, STRIPS / 2, 0);
                float age = frame - (84f + 1.25f * card);
                if (age <= 0f) {
                    assertEquals(arch[root], mesh.vertices[root], .000002f);
                    assertEquals(arch[middle + 1], mesh.vertices[middle + 1], .000002f);
                } else if (age >= 4.25f) {
                    assertEquals(22, (Math.abs(mesh.vertices[root]) - .5f) * 88, .05);
                    assertEquals(mesh.vertices[root + 1], mesh.vertices[middle + 1], .000002f);
                }
                double length = 0, lastX = 0, lastY = 0;
                for (int strip = 0; strip <= STRIPS; strip++) {
                    int t = vertex(card, strip, 0), b = vertex(card, strip, 2);
                    double x = (mesh.vertices[t] + mesh.vertices[b]) * .5;
                    double y = (mesh.vertices[t + 1] + mesh.vertices[b + 1]) * .5;
                    if (strip > 0) length += Math.hypot(x - lastX, y - lastY);
                    assertTrue(mesh.vertices[b + 1] >= -.000001f);
                    assertEquals(.32, 88 * Math.hypot(mesh.vertices[t] - mesh.vertices[b],
                            mesh.vertices[t + 1] - mesh.vertices[b + 1]), .00003);
                    lastX = x;
                    lastY = y;
                }
                assertEquals(88, 88 * length, .02);
            }
        }
        float beforePush = mesh.vertices[vertex(0, 0, 0)];
        mesh.update((motion.leadFrames + 152.125f + pauseFrames) * FRAME_SECONDS, motion);
        float firstMovement = beforePush - mesh.vertices[vertex(0, 0, 0)];
        assertTrue(firstMovement > 0f);
        if (motion == Motion.MID_BRIDGE) {
            assertTrue(firstMovement < .0001f, "The squared deck starts sliding from rest");
            mesh.update((motion.leadFrames + 152.25f + pauseFrames) * FRAME_SECONDS, motion);
            float nextMovement = beforePush - mesh.vertices[vertex(0, 0, 0)] - firstMovement;
            assertTrue(nextMovement > 2f * firstMovement, "The push accelerates after the pause");
        } else {
            assertTrue(firstMovement > .001f);
        }
        mesh.update(motion.duration(), motion);
        float[] finished = mesh.vertices.clone();
        mesh.update(CYCLE_SECONDS, Motion.ORIGINAL);
        assertArrayEquals(finished, mesh.vertices, .000001f);
    }

    private static int vertex(int card, int strip, int corner) {
        return (card * VERTICES_PER_CARD + strip * VERTICES_PER_STRIP + corner) * FLOATS_PER_VERTEX;
    }
}
