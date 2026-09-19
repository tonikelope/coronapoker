/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GdxAvatarZoomTest {

    @Test
    void matchesSwingHoverDelay() {
        assertFalse(CoronaPokerGdxTable.avatarZoomDelayReached(0.249f));
        assertTrue(CoronaPokerGdxTable.avatarZoomDelayReached(0.250f));
        assertFalse(CoronaPokerGdxTable.avatarZoomDelayReached(Float.NaN));
    }

    @Test
    void doublesTheAvatarButCapsItForShortViewports() {
        assertEquals(144f, CoronaPokerGdxTable.avatarZoomSize(72f, 1080f));
        assertEquals(90f, CoronaPokerGdxTable.avatarZoomSize(72f, 200f));
        assertEquals(72f, CoronaPokerGdxTable.avatarZoomSize(72f, 100f));
        assertEquals(0f, CoronaPokerGdxTable.avatarZoomSize(0f, 1080f));
    }
}
