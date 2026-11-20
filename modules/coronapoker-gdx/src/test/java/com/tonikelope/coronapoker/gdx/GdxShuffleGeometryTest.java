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

    private static int vertex(int card, int strip, int corner) {
        return (card * VERTICES_PER_CARD + strip * VERTICES_PER_STRIP + corner) * FLOATS_PER_VERTEX;
    }
}
