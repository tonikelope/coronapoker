/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.sra;

import com.tonikelope.coronapoker.CanonicalActionRecord;
import com.tonikelope.coronapoker.HandStateChain;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ZERO-TRUST RECOVER: reconstructing H_t when a player LEFT mid-hand and reconnected.
 *
 * When a player leaves mid-hand, its exit-fold is handled by MUTUAL OMISSION: no peer
 * absorbs any record into H_t for that slot (the departed seat contributes nothing).
 * On recover, the chain MUST reproduce that omission. If the reconnected client instead
 * re-signs and absorbs a fold for its own exit-fold slot, the chain gains a record that
 * the live chain never had: H_t silently drifts off-live (the rebuilt record self-satisfies
 * its own PREV_H, so the absorb does not throw), and the very next ORIGINAL record — a bot's
 * action or the next street, still carrying its live PREV_H — fails to absorb. Host and every
 * client drift identically, so they agree on the same wrong H_final and consensus reports the
 * hand "verified" — a FALSE positive.
 *
 * This test reproduces that exact mechanism at the HandStateChain level (no game engine) and
 * pins the fix: reproducing the exit-fold as mutual omission yields the live H_final exactly.
 */
public class RecoverExitFoldChainConvergenceTest {

    private static byte[] handId(int seed) {
        byte[] out = new byte[16];
        for (int i = 0; i < 16; i++) {
            out[i] = (byte) (seed + i);
        }
        return out;
    }

    private static byte[] deck(int seed, int len) {
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = (byte) (seed + i);
        }
        return out;
    }

    private static byte[] commit(int seed) {
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) {
            out[i] = (byte) (seed * 7 + i);
        }
        return out;
    }

    private static List<byte[]> commits(int base, int n) {
        List<byte[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(commit(base + i));
        }
        return out;
    }

    private static HandStateChain freshChain(byte[] hid, byte[] dck) {
        List<byte[]> ids = new ArrayList<>();
        for (String n : new String[]{"alice", "bob", "carol"}) {
            ids.add(CanonicalActionRecord.playerIdFromNick(n));
        }
        return HandStateChain.start(hid, ids, commits(1, ids.size()), commits(100, ids.size()), dck);
    }

    @Test
    @DisplayName("Re-absorbing a reconnected player's exit-fold breaks the next original record; omitting it converges to the live H_final")
    void exitFoldMustBeMutualOmissionOnRecover() {
        final byte[] hid = handId(0x33);
        final byte[] dck = deck(0x02, 1664);
        final byte[] sig = new byte[64]; // absorb() checks length, not validity
        final byte[] pidAlice = CanonicalActionRecord.playerIdFromNick("alice");
        final byte[] pidBob = CanonicalActionRecord.playerIdFromNick("bob");
        final byte[] pidCarol = CanonicalActionRecord.playerIdFromNick("carol");

        // --- LIVE: alice acts, bob LEAVES (mutual omission), carol acts ---
        HandStateChain live = freshChain(hid, dck);
        byte[] h0 = live.getCurrentHash();
        byte[] recAlice = CanonicalActionRecord.encode(h0, hid, pidAlice,
                CanonicalActionRecord.STREET_FLOP, CanonicalActionRecord.ACTION_BET, 500L, false, true);
        byte[] h1 = live.absorb(recAlice, sig);
        // bob's exit-fold: MUTUAL OMISSION — nothing absorbed.
        // carol's original record therefore carries PREV_H = h1.
        byte[] recCarol = CanonicalActionRecord.encode(h1, hid, pidCarol,
                CanonicalActionRecord.STREET_TURN, CanonicalActionRecord.ACTION_CHECK, 500L, false, true);
        byte[] hFinalLive = live.absorb(recCarol, sig);

        // --- BUGGY RECOVER: reconnected bob RE-ABSORBS a fold for its exit-fold slot ---
        HandStateChain buggy = freshChain(hid, dck);
        buggy.absorb(recAlice, sig);
        byte[] recBobFold = CanonicalActionRecord.encode(buggy.getCurrentHash(), hid, pidBob,
                CanonicalActionRecord.STREET_FLOP, CanonicalActionRecord.ACTION_FOLD, 0L, false, true);
        buggy.absorb(recBobFold, sig); // absorbs fine (self-satisfies its own PREV_H) — divergence is silent
        // carol's ORIGINAL record (PREV_H = h1) now fails: the exact observed symptom.
        assertThrows(IllegalStateException.class, () -> buggy.absorb(recCarol, sig),
                "re-absorbing the exit-fold drifts H_t; carol's original PREV_H no longer matches");

        // --- FIXED RECOVER: exit-fold reproduced as mutual omission (nothing absorbed) ---
        HandStateChain fixed = freshChain(hid, dck);
        fixed.absorb(recAlice, sig);
        // bob's exit-fold: absorb nothing, exactly like live.
        byte[] hFinalFixed = fixed.absorb(recCarol, sig);
        assertArrayEquals(hFinalLive, hFinalFixed,
                "omitting the exit-fold reproduces the live H_final byte-for-byte");
    }
}
