/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

/**
 * Prevents a press owned by one surface from resolving a dialog that appears
 * during that same input gesture.
 *
 * <p>libGDX's polled input remains visible even when an {@code InputProcessor}
 * consumed the original event.  A newly presented dialog therefore waits for
 * one neutral frame before accepting a fresh click or decision key.</p>
 */
final class GdxDialogInputGate {

    private Object owner;
    private boolean neutralObserved;

    boolean accepts(Object nextOwner, boolean decisionInputHeld) {
        if (nextOwner == null) {
            owner = null;
            neutralObserved = false;
            return true;
        }
        if (owner != nextOwner) {
            owner = nextOwner;
            neutralObserved = false;
        }
        if (!neutralObserved) {
            if (!decisionInputHeld) neutralObserved = true;
            return false;
        }
        return true;
    }
}
