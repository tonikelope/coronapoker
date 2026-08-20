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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the single current recovery-settings schema and its ANTE/STRADDLE fields. */
class RecoverSettingsSchemaSmoke {

    @Test
    @DisplayName("ANTE/STRADDLE keys round-trip on and off")
    void keysRoundTrip() {
        GameFrame.ANTE = true;
        GameFrame.STRADDLE = true;
        GameFrame.applyRecoverSettings(GameFrame.serializeRecoverSettings());
        assertTrue(GameFrame.ANTE, "ANTE=1 must enable the ante");
        assertTrue(GameFrame.STRADDLE, "STRADDLE=1 must enable the straddle");

        GameFrame.ANTE = false;
        GameFrame.STRADDLE = false;
        GameFrame.applyRecoverSettings(GameFrame.serializeRecoverSettings());
        assertFalse(GameFrame.ANTE, "ANTE=0 must disable the ante");
        assertFalse(GameFrame.STRADDLE, "STRADDLE=0 must disable the straddle");
    }

    @Test
    @DisplayName("serialize -> apply restores the exact ANTE/STRADDLE state")
    void serializeApplyRoundTrip() {
        GameFrame.ANTE = true;
        GameFrame.STRADDLE = false;
        String serialized = GameFrame.serializeRecoverSettings();

        GameFrame.ANTE = false;
        GameFrame.STRADDLE = true;
        GameFrame.applyRecoverSettings(serialized);

        assertTrue(GameFrame.ANTE, "serialized ANTE=on must be restored");
        assertFalse(GameFrame.STRADDLE, "serialized STRADDLE=off must be restored");
    }

    @Test
    @DisplayName("A partial recover row is rejected")
    void missingCurrentKeysAreRejected() {
        GameFrame.ANTE = true;
        GameFrame.STRADDLE = true;
        assertThrows(IllegalArgumentException.class,
                () -> GameFrame.applyRecoverSettings("DIFFICULTY=HARD"));
        assertTrue(GameFrame.ANTE, "rejected settings must not mutate current state");
        assertTrue(GameFrame.STRADDLE, "rejected settings must not mutate current state");
    }
}
