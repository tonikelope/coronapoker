/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GdxPlasticCardTest {
    @Test void theWholeCardStartsAndFinishesFlatAtItsOriginalFootprint() {
        Vector3 point = new Vector3();
        for (float direction : new float[]{-1f, 1f}) {
            for (float progress : new float[]{0f, 1f}) {
                for (int row = 0; row <= 16; row++) {
                    for (int column = 0; column <= 28; column++) {
                        float u = column / 28f;
                        float v = row / 16f;
                        GdxPlasticCard.reveal(point, 100f, 140f, u, v, progress, direction);
                        assertEquals((u - 0.5f) * 100f * (progress == 0f ? 1 : -1), point.x, 0.0001f);
                        assertEquals(0f, point.y, 0.0001f);
                        assertEquals((v - 0.5f) * 140f, point.z, 0.0001f);
                    }
                }
            }
        }
    }

    @Test void flexPreservesThePlasticWidthAndNeverPassesThroughTheFelt() {
        Vector3 point = new Vector3();
        Vector3 previous = new Vector3();
        for (float direction : new float[]{-1f, 1f}) {
            for (int frame = 1; frame < 100; frame++) {
                for (float v : new float[]{0f, 0.5f, 1f}) {
                    float length = 0f;
                    float minHeight = Float.POSITIVE_INFINITY;
                    for (int column = 0; column <= 100; column++) {
                        GdxPlasticCard.reveal(point, 100f, 140f, column / 100f,
                                v, frame / 100f, direction);
                        assertTrue(Float.isFinite(point.x) && Float.isFinite(point.y));
                        assertTrue(point.y >= -0.0001f, "Card penetrates the felt");
                        if (column > 0) length += point.dst(previous);
                        previous.set(point);
                        minHeight = Math.min(minHeight, point.y);
                    }
                    assertEquals(100f, length, 0.002f, "Plastic stretches during the turn");
                    assertEquals(0f, minHeight, 0.002f, "No part of the card remains supported");
                }
            }
        }
    }

    @Test void theTurningSurfaceActuallyBowsAndItsNearCornerLeads() {
        Vector3 left = new Vector3();
        Vector3 middle = new Vector3();
        Vector3 right = new Vector3();
        for (float direction : new float[]{-1f, 1f}) {
            GdxPlasticCard.reveal(left, 100f, 140f, 0f, 0.5f, 0.35f, direction);
            GdxPlasticCard.reveal(right, 100f, 140f, 1f, 0.5f, 0.35f, direction);
            GdxPlasticCard.reveal(middle, 100f, 140f, 0.5f, 0.5f, 0.35f, direction);
            assertTrue(middle.dst(new Vector3(left).lerp(right, 0.5f)) > 6f);
            GdxPlasticCard.reveal(left, 100f, 140f, 0.5f, 0f, 0.25f, direction);
            GdxPlasticCard.reveal(right, 100f, 140f, 0.5f, 1f, 0.25f, direction);
            assertTrue(Math.abs(left.y - right.y) > 5f, "All corners move as one rigid panel");
        }
    }

    @Test void airborneFlexDoesNotStretchTheCardOrSnapOnLanding() {
        Vector3 current = new Vector3();
        Vector3 previous = new Vector3();
        for (float progress : new float[]{0f, 0.0001f, 0.25f, 0.5f, 0.75f, 0.9999f, 1f}) {
            float length = 0f;
            for (int step = 0; step <= 100; step++) {
                GdxPlasticCard.flight(current, 100f, 140f, 0.5f, step / 100f, progress);
                if (step > 0) length += current.dst(previous);
                previous.set(current);
            }
            assertEquals(140f, length, 0.002f);
        }
        GdxPlasticCard.flight(current, 100f, 140f, 1f, 1f, 0.9999f);
        GdxPlasticCard.flight(previous, 100f, 140f, 1f, 1f, 1f);
        assertTrue(current.dst(previous) < 0.01f);
        assertEquals(0.48f, GdxPlasticCard.thickness(100f), 0.0001f);
    }

    @Test void theFinalHandoffDoesNotPopOrBounce() {
        Vector3 previous = new Vector3();
        Vector3 settled = new Vector3();
        for (float direction : new float[]{-1f, 1f}) {
            for (float u : new float[]{0f, 0.5f, 1f}) {
                for (float v : new float[]{0f, 1f}) {
                    GdxPlasticCard.reveal(previous, 100f, 140f, u, v, 0.9999f, direction);
                    GdxPlasticCard.reveal(settled, 100f, 140f, u, v, 1f, direction);
                    assertTrue(previous.dst(settled) < 0.02f);
                }
            }
        }
    }
}
