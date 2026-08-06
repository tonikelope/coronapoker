/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ZERO-TRUST RECOVER: telling a benign "action played while I was out of the hand"
 * apart from a forged action, when the host replays one of MY OWN actions with no
 * verifiable record (bare, record="*").
 *
 * When a player leaves mid-hand the host synthesises its FOLD with no signature
 * (§4.5: nobody may sign in the actor's name) and stores it bare. On reconnect the
 * host replays it as "...#*#*". That is legitimate and unverifiable — the client
 * was gone — so it must be ACCEPTED with a soft notice, NOT rejected as a forgery.
 *
 * {@link Crupier#isBenignPostAbsenceRecover} draws the line with two facts the peer
 * already holds locally: the 1-based replay index of the action and how many of the
 * peer's own actions it managed to persist before reconnecting. Benign iff the
 * action is a FOLD (an absent seat can only end up folded — a bet would move money
 * the peer never authorised) AND its index is strictly beyond what the peer stored
 * (it happened during the absence, so there is no local record to confront it with).
 * A bare action at or before the stored count means the host stripped the signature
 * of an action the peer actually witnessed: a forgery, and the hard warning stands.
 */
class RecoverAbsenceActionClassifierTest {

    @Test
    @DisplayName("A FOLD later than everything stored locally is the benign absence case")
    void foldBeyondStoredIsBenign() {
        // Left after 2 recorded actions; the host's exit-fold is my 3rd (index 3 > 2).
        assertTrue(Crupier.isBenignPostAbsenceRecover(Player.FOLD, 3, 2));
    }

    @Test
    @DisplayName("Left before acting at all: the very first replayed FOLD is still benign")
    void foldWithNothingStoredIsBenign() {
        // Persisted count 0 (never got to act), exit-fold is index 1 > 0.
        assertTrue(Crupier.isBenignPostAbsenceRecover(Player.FOLD, 1, 0));
    }

    @Test
    @DisplayName("A bare FOLD at an index I DID witness is a stripped signature, not absence")
    void foldWithinStoredIsForgery() {
        // Index equal to the stored count: this action is one I recorded locally, so a
        // bare replay means the host removed its signature. Hard path, not benign.
        assertFalse(Crupier.isBenignPostAbsenceRecover(Player.FOLD, 2, 2));
        // And strictly before the stored count is even more clearly witnessed.
        assertFalse(Crupier.isBenignPostAbsenceRecover(Player.FOLD, 1, 2));
    }

    @Test
    @DisplayName("A non-FOLD attributed to an absent seat is never benign (it would move money)")
    void nonFoldBeyondStoredIsNeverBenign() {
        assertFalse(Crupier.isBenignPostAbsenceRecover(Player.BET, 3, 2));
        assertFalse(Crupier.isBenignPostAbsenceRecover(Player.CHECK, 3, 2));
        assertFalse(Crupier.isBenignPostAbsenceRecover(Player.ALLIN, 3, 2));
    }

    @Test
    @DisplayName("When the local count could not be read (MAX_VALUE) nothing is ever benign")
    void unreadableLocalCountStaysHard() {
        // sqlCountLocalHandActions returns Integer.MAX_VALUE on SQL failure: no index can
        // exceed it, so the classifier conservatively refuses to soften the warning.
        assertFalse(Crupier.isBenignPostAbsenceRecover(Player.FOLD, 5, Integer.MAX_VALUE));
    }
}
