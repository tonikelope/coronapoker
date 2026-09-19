/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

/**
 * Owns one primary-pointer gesture from press through release.
 *
 * <p>GDX overlays act on press while the table HUD deliberately acts on
 * release.  Without this small gate, closing a menu or modal on press exposes
 * the HUD in time for the matching release to click straight through it.</p>
 */
final class GdxPointerCapture {

    private boolean wasDown;
    private boolean captureRelease;

    boolean update(boolean down) {
        boolean released = wasDown && !down;
        wasDown = down;
        if (released && captureRelease) {
            captureRelease = false;
            return false;
        }
        return released;
    }

    void capturePressedGesture() {
        if (wasDown) captureRelease = true;
    }
}
