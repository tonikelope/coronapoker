/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class GdxScrollbarDragTest {

    @Test
    void pressingAnyPointOfThumbDoesNotMakeItJump() {
        assertKeepsRatio(32f);
        assertKeepsRatio(45f);
        assertKeepsRatio(49f);
    }

    @Test
    void draggingPreservesExactGrabPointAndClampsOutsideTrack() {
        var capture = GdxScrollbarDrag.begin(35f, 10f, 100f,
                20f, 0.25f, false);
        assertEquals(0.50f, GdxScrollbarDrag.ratio(55f, 10f, 100f,
                20f, capture.grabOffset(), false), 0.0001f);
        assertEquals(0f, GdxScrollbarDrag.ratio(-100f, 10f, 100f,
                20f, capture.grabOffset(), false), 0.0001f);
        assertEquals(1f, GdxScrollbarDrag.ratio(500f, 10f, 100f,
                20f, capture.grabOffset(), false), 0.0001f);
    }

    @Test
    void pressingEmptyTrackRecentresThumbAndCanContinueDragging() {
        var capture = GdxScrollbarDrag.begin(82f, 10f, 100f,
                20f, 0.10f, false);
        assertEquals(10f, capture.grabOffset(), 0.0001f);
        assertEquals(0.775f, capture.ratio(), 0.0001f);
        assertEquals(0.90f, GdxScrollbarDrag.ratio(92f, 10f, 100f,
                20f, capture.grabOffset(), false), 0.0001f);
    }

    @Test
    void invertedScrollbarKeepsLogicalDirection() {
        var capture = GdxScrollbarDrag.begin(75f, 10f, 100f,
                20f, 0.25f, true);
        assertEquals(0.25f, capture.ratio(), 0.0001f);
        assertEquals(0.50f, GdxScrollbarDrag.ratio(55f, 10f, 100f,
                20f, capture.grabOffset(), true), 0.0001f);
    }

    @Test
    void thumbThatFillsTrackIsStable() {
        var capture = GdxScrollbarDrag.begin(40f, 10f, 20f,
                40f, 0.75f, false);
        assertEquals(0f, capture.ratio(), 0.0001f);
        assertEquals(0f, GdxScrollbarDrag.ratio(500f, 10f, 20f,
                40f, capture.grabOffset(), false), 0.0001f);
    }

    private static void assertKeepsRatio(float pointer) {
        var capture = GdxScrollbarDrag.begin(pointer, 10f, 100f,
                20f, 0.25f, false);
        assertEquals(0.25f, capture.ratio(), 0.0001f);
    }
}
