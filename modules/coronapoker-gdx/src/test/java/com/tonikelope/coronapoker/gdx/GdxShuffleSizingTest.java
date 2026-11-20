/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GdxShuffleSizingTest {

    @Test
    void replaysTheRecordedRiffleFromItsStartAtBothReleases() {
        assertEquals(.92f, GdxShuffleGeometry.soundStart(0), .00001f);
        assertEquals(2.60f, GdxShuffleGeometry.soundStart(1), .00001f);
        assertEquals(1.96f, GdxShuffleGeometry.soundEnd(0), .00001f);
        assertEquals(3.96f, GdxShuffleGeometry.soundEnd(1), .00001f);
        assertEquals(4.46f, GdxShuffleGeometry.Motion.MID_BRIDGE.duration(), .00001f);
    }

    @Test
    void textOnlyShufflePulseIsSlowAndBounded() {
        float minimum = Float.POSITIVE_INFINITY;
        float maximum = Float.NEGATIVE_INFINITY;
        for (int sample = 0; sample <= 240; sample++) {
            float pulse = CoronaPokerGdxTable.shuffleFallbackPulse(
                    sample / 60f);
            minimum = Math.min(minimum, pulse);
            maximum = Math.max(maximum, pulse);
        }

        assertTrue(minimum >= 0f);
        assertTrue(maximum <= 1f);
        assertTrue(maximum - minimum > 0.95f);
    }
}
