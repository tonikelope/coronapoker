/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GdxDialogInputGateTest {

    @Test
    void inheritedPressCannotChooseANewDialog() {
        GdxDialogInputGate gate = new GdxDialogInputGate();
        Object autoAction = new Object();
        Object rebuy = new Object();

        assertFalse(gate.accepts(autoAction, true));
        assertFalse(gate.accepts(rebuy, true),
                "the press that cancelled AUTO must not cancel rebuy");
        assertFalse(gate.accepts(rebuy, false),
                "the release frame only arms the new dialog");
        assertTrue(gate.accepts(rebuy, true),
                "a later, deliberate press is accepted");
    }

    @Test
    void evenAnIdleDialogWaitsOneCompleteFrameBeforeInput() {
        GdxDialogInputGate gate = new GdxDialogInputGate();
        Object gameOver = new Object();

        assertFalse(gate.accepts(gameOver, false));
        assertTrue(gate.accepts(gameOver, true));
    }

    @Test
    void clearingTheDialogAlsoClearsItsArmedState() {
        GdxDialogInputGate gate = new GdxDialogInputGate();
        Object first = new Object();

        assertFalse(gate.accepts(first, false));
        assertTrue(gate.accepts(first, true));
        assertTrue(gate.accepts(null, false));
        assertFalse(gate.accepts(first, true));
    }
}
