/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.smoke;

import com.tonikelope.coronapoker.GameFrame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the ANTE / STRADDLE persistence contract added in the ante+straddle
 * sprint (config plumbing phase). Both flags must round-trip through
 * {@link GameFrame#serializeRecoverSettings()} /
 * {@link GameFrame#applyRecoverSettings(String)}, and a recover row saved before
 * the feature (no ANTE/STRADDLE keys) must leave them at their OFF default
 * rather than inheriting a stale value from another game in the same session.
 */
class AnteStraddleRecoverCompatSmoke {

    @Test
    @DisplayName("ANTE/STRADDLE keys round-trip on and off")
    void keysRoundTrip() {
        GameFrame.applyRecoverSettings("ANTE=1#STRADDLE=1");
        assertTrue(GameFrame.ANTE, "ANTE=1 must enable the ante");
        assertTrue(GameFrame.STRADDLE, "STRADDLE=1 must enable the straddle");

        GameFrame.applyRecoverSettings("ANTE=0#STRADDLE=0");
        assertFalse(GameFrame.ANTE, "ANTE=0 must disable the ante");
        assertFalse(GameFrame.STRADDLE, "STRADDLE=0 must disable the straddle");
    }

    @Test
    @DisplayName("serialize -> apply restores the exact ANTE/STRADDLE state")
    void serializeApplyRoundTrip() {
        GameFrame.ANTE = true;
        GameFrame.STRADDLE = false;
        String serialized = GameFrame.serializeRecoverSettings();

        // Flip both, then prove the restore brings back the serialized state.
        GameFrame.ANTE = false;
        GameFrame.STRADDLE = true;
        GameFrame.applyRecoverSettings(serialized);

        assertTrue(GameFrame.ANTE, "serialized ANTE=on must be restored");
        assertFalse(GameFrame.STRADDLE, "serialized STRADDLE=off must be restored");
    }

    @Test
    @DisplayName("A pre-feature recover row resets ANTE/STRADDLE to OFF (no stale carry-over)")
    void missingKeysResetToOff() {
        GameFrame.ANTE = true;
        GameFrame.STRADDLE = true;
        // A row from before the feature: no ANTE/STRADDLE keys.
        GameFrame.applyRecoverSettings("DIFFICULTY=HARD");
        assertFalse(GameFrame.ANTE, "missing ANTE key must reset to OFF, not stay stale-on");
        assertFalse(GameFrame.STRADDLE, "missing STRADDLE key must reset to OFF, not stay stale-on");
    }
}
