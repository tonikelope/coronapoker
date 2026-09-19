/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class GdxPointerCaptureTest {

    @Test
    void overlayThatConsumesPressAlsoConsumesMatchingRelease() {
        GdxPointerCapture capture = new GdxPointerCapture();

        assertFalse(capture.update(true));
        capture.capturePressedGesture();
        assertFalse(capture.update(false));

        assertFalse(capture.update(true));
        assertTrue(capture.update(false));
    }

    @Test
    void uncapturedClickStillReachesReleaseDrivenTableControls() {
        GdxPointerCapture capture = new GdxPointerCapture();

        assertFalse(capture.update(true));
        assertTrue(capture.update(false));
    }
}
