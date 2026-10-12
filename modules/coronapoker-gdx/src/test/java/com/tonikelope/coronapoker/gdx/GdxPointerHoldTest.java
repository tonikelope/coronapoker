/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class GdxPointerHoldTest {

    @Test
    void onlyTheOwningPointerAndButtonCanReleaseTheGesture() {
        GdxPointerHold hold = new GdxPointerHold();

        assertTrue(hold.capture(2, 0));
        assertTrue(hold.active());
        assertFalse(hold.release(1, 0));
        assertFalse(hold.release(2, 1));
        assertTrue(hold.release(2, 0));
        assertFalse(hold.active());
    }

    @Test
    void aCapturedGestureCannotBeReplacedAndCanBeCancelled() {
        GdxPointerHold hold = new GdxPointerHold();

        assertTrue(hold.capture(0, 0));
        assertFalse(hold.capture(1, 0));
        hold.clear();
        assertFalse(hold.active());
        assertTrue(hold.capture(1, 0));
    }
}
