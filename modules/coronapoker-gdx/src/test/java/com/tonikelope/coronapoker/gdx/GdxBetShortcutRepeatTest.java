/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class GdxBetShortcutRepeatTest {

    @Test
    void mapsOnlyBetStepShortcutsToARepeatDirection() {
        assertEquals(-1, CoronaPokerGdxTable.betStepDirection(
                GdxShortcutBindings.BET_DOWN));
        assertEquals(1, CoronaPokerGdxTable.betStepDirection(
                GdxShortcutBindings.BET_UP));
        assertEquals(0, CoronaPokerGdxTable.betStepDirection(
                GdxShortcutBindings.BET));
        assertEquals(0, CoronaPokerGdxTable.betStepDirection(null));
    }

    @Test
    void usesTheSameInitialDelayAndCadenceAsHeldPointerControls() {
        GdxKeyRepeat repeat = new GdxKeyRepeat();
        repeat.press(19);

        assertEquals(0, repeat.update(
                GdxKeyRepeat.INITIAL_DELAY_SECONDS - 0.01f, true));
        assertEquals(1, repeat.update(0.01f, true));
        assertEquals(1, repeat.update(GdxKeyRepeat.INTERVAL_SECONDS, true));
        assertEquals(0, repeat.update(1f, false));
    }
}
