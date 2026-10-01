/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GdxShuffleSizingTest {

    @Test
    void preservesCanonicalShuffleSizeAtFullHdAndAbove() {
        assertEquals(960, CoronaPokerGdxTable.shuffleDecodeWidth(1920, 1080));
        assertEquals(960, CoronaPokerGdxTable.shuffleDecodeWidth(2560, 1440));
        assertEquals(960, CoronaPokerGdxTable.shuffleDecodeWidth(3840, 2160));
    }

    @Test
    void scalesShuffleDecodeOnceForLowerResolutionBackbuffers() {
        int hd = CoronaPokerGdxTable.shuffleDecodeWidth(1280, 720);
        int laptop = CoronaPokerGdxTable.shuffleDecodeWidth(1366, 768);
        int narrow = CoronaPokerGdxTable.shuffleDecodeWidth(1024, 768);

        assertEquals(640, hd);
        assertEquals(683, laptop);
        assertTrue(narrow <= Math.round(1024 * 0.78f));
        assertTrue(hd < laptop && laptop < 960);
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
