/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class GdxLobbyDigitalClockTest {

    @Test
    void mapsEveryDecimalDigitToItsSevenSegmentDisplay() {
        int[] expected = {0x3f, 0x06, 0x5b, 0x4f, 0x66,
            0x6d, 0x7d, 0x07, 0x7f, 0x6f};
        for (int digit = 0; digit <= 9; digit++) {
            assertEquals(expected[digit], GdxSevenSegmentDisplay.mask(
                    Character.forDigit(digit, 10)));
        }
        assertEquals(0, GdxSevenSegmentDisplay.mask(':'));
    }

    @Test
    void blinksColonsAtRelaxedThreeQuarterSecondIntervals() {
        assertEquals(true, GdxSevenSegmentDisplay.colonsVisible(0L));
        assertEquals(true, GdxSevenSegmentDisplay.colonsVisible(749L));
        assertEquals(false, GdxSevenSegmentDisplay.colonsVisible(750L));
        assertEquals(false, GdxSevenSegmentDisplay.colonsVisible(1_499L));
        assertEquals(true, GdxSevenSegmentDisplay.colonsVisible(1_500L));
    }

    @Test
    void compactGameClockFitsInsideTheExistingCommunityBar() {
        assertEquals(66.8f, GdxSevenSegmentDisplay.width("00:00:00",
                8.5f, 1.4f, 3f), 0.001f);
    }
}
