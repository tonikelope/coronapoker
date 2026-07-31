/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ZERO-TRUST: the two synthetic folds and the wire that tells them apart.
 *
 * A synthetic FOLD replaces a remote player's action in two very different
 * situations, and confusing them is how the table hangs:
 *
 * <ul>
 *   <li><b>the peer left</b> — its EXIT already travelled, so every receiver
 *       reaches that seat and synthesises the very same fold on its own. The
 *       host must NOT put it on the wire.</li>
 *   <li><b>the wire did not verify</b> (bad signature, record that does not bind
 *       to the played action, record absent or malformed) — only the direct
 *       receiver saw that wire. If the host keeps quiet, the rest of the table
 *       stays waiting on that seat forever, so this one MUST reach the wire.</li>
 * </ul>
 *
 * What the host emits for the second case is a bare fold with no record and no
 * signature (§4.5: the only is_voluntary=0 record in the protocol is the
 * community reveal, never an action, so nobody may sign in the actor's name).
 * Every receiver then classifies that wire as "no record with the chain active"
 * and synthesises the identical fold: no peer contributes a record for the seat
 * and H_t advances in lockstep by mutual omission.
 *
 * The tests pin, end to end and with no game engine: the contract of both
 * synths, the fact that only the canonical 92-byte record is verifiable at all,
 * and the round trip proving the emitted fold is exactly what the receiving side
 * treats as unsigned.
 */
class UnverifiedActionSynthTest {

    /** A 7-slot action[] carrying a genuine BET, as read from a healthy wire. */
    private static Object[] genuineBetAction() {
        Object[] action = new Object[7];
        action[0] = Player.BET;
        action[1] = 50d;
        action[2] = null;
        action[3] = new byte[CanonicalActionRecord.RECORD_BYTES];
        action[4] = new byte[64];
        action[5] = Boolean.TRUE;
        action[6] = null;
        return action;
    }

    // ---- which synth is which ---------------------------------------------

    @Test
    @DisplayName("The departed-peer fold stays off the wire")
    void exitSynthIsSilent() {
        Object[] action = genuineBetAction();

        Crupier.synthesizeExitFoldAction(action);

        assertFalse(Crupier.isUnverifiedSynthFold(action),
                "the EXIT already travelled: rebroadcasting this fold would be noise");
    }

    @Test
    @DisplayName("The fold synthesised because the wire did not verify MUST reach the wire")
    void unverifiedSynthMustBeRebroadcast() {
        Object[] action = genuineBetAction();

        Crupier.synthesizeUnverifiedFoldAction(action);

        assertTrue(Crupier.isUnverifiedSynthFold(action),
                "nobody else saw that wire: without the rebroadcast the table waits on this seat forever");
    }

    @Test
    @DisplayName("Both synths leave the same fold: FOLD, 0, no cinematic, no record, no sig, not voluntary")
    void bothSynthsShareTheFoldContract() {
        for (boolean unverified : new boolean[]{false, true}) {
            Object[] action = genuineBetAction();
            action[2] = "rounders.gif";

            if (unverified) {
                Crupier.synthesizeUnverifiedFoldAction(action);
            } else {
                Crupier.synthesizeExitFoldAction(action);
            }

            assertEquals(Player.FOLD, action[0], "the falsified decision must not survive");
            assertEquals(0d, action[1], "no money moves on a synthetic fold");
            assertNull(action[2]);
            assertNull(action[3], "no record is contributed for this seat");
            assertNull(action[4]);
            assertEquals(Boolean.FALSE, action[5],
                    "not voluntary: the betting round skips both the local record build and the chain absorb");
        }
    }

    @Test
    @DisplayName("An action[] without the seventh slot is never an unverified synth")
    void legacyActionArraysDefaultToSilent() {
        // The bot path builds a 3-slot action[] and the recovery replay a 6-slot
        // one. Neither can ask for a rebroadcast: the safe default is silence.
        assertFalse(Crupier.isUnverifiedSynthFold(new Object[]{Player.CHECK, 0d, null}));

        Object[] recovered = new Object[6];
        recovered[0] = Player.FOLD;
        recovered[1] = 0d;
        recovered[5] = Boolean.FALSE;
        assertFalse(Crupier.isUnverifiedSynthFold(recovered));

        assertFalse(Crupier.isUnverifiedSynthFold(null));
    }

    @Test
    @DisplayName("Both synths reject an action[] too short to hold their contract")
    void shortActionArraysAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Crupier.synthesizeExitFoldAction(new Object[3]));
        assertThrows(IllegalArgumentException.class,
                () -> Crupier.synthesizeExitFoldAction(null));
        // The unverified one needs the seventh slot to mark the rebroadcast.
        assertThrows(IllegalArgumentException.class,
                () -> Crupier.synthesizeUnverifiedFoldAction(new Object[6]));
        assertThrows(IllegalArgumentException.class,
                () -> Crupier.synthesizeUnverifiedFoldAction(null));
    }

    // ---- only a canonical record is verifiable at all ---------------------

    @Test
    @DisplayName("Only the canonical 92-byte record is verifiable")
    void onlyTheCanonicalRecordLengthIsVerifiable() {
        // The signer always emits exactly RECORD_BYTES (CanonicalActionRecord.encode)
        // and the encrypted + HMAC'd channel drops anything altered in flight, so a
        // different length can only come from a modified client. Letting it through
        // used to skip the signature check, the binding and the synth, while the
        // plaintext decision and amount moved the money all the same.
        assertTrue(Crupier.isVerifiableWireRecord(new byte[CanonicalActionRecord.RECORD_BYTES]));

        assertFalse(Crupier.isVerifiableWireRecord(null));
        assertFalse(Crupier.isVerifiableWireRecord(new byte[0]));
        assertFalse(Crupier.isVerifiableWireRecord(new byte[CanonicalActionRecord.RECORD_BYTES - 1]));
        assertFalse(Crupier.isVerifiableWireRecord(new byte[CanonicalActionRecord.RECORD_BYTES + 1]));
        // Two canonical records glued together must not pass either.
        assertFalse(Crupier.isVerifiableWireRecord(new byte[2 * CanonicalActionRecord.RECORD_BYTES]));
    }

    // ---- the is_voluntary bit an action may claim -------------------------

    /** A canonical record carrying a chosen is_voluntary bit. */
    private static byte[] recordWithVoluntary(boolean voluntary) {
        byte[] prevH = new byte[32];
        byte[] handId = new byte[16];
        byte[] playerId = new byte[32];
        Arrays.fill(prevH, (byte) 0x11);
        Arrays.fill(handId, (byte) 0x22);
        Arrays.fill(playerId, (byte) 0x33);
        return CanonicalActionRecord.encode(prevH, handId, playerId,
                CanonicalActionRecord.STREET_PREFLOP, CanonicalActionRecord.ACTION_BET,
                5000L, false, voluntary);
    }

    @Test
    @DisplayName("The is_voluntary bit is read back exactly as the signer wrote it")
    void voluntaryFlagRoundTrips() {
        assertTrue(Crupier.readWireVoluntaryFlag(recordWithVoluntary(true)));
        assertFalse(Crupier.readWireVoluntaryFlag(recordWithVoluntary(false)));
    }

    @Test
    @DisplayName("An action claiming is_voluntary=0 is not a legitimate wire (§4.5)")
    void anActionMayNeverClaimNonVoluntary() {
        // Every action on the wire is signed by whoever played it: a human with its
        // own key, a bot with the host's (§10), both with is_voluntary=1. The only
        // is_voluntary=0 record in the protocol is the community reveal, which
        // travels on its own command, and the departed-peer fold never reaches the
        // wire at all. So a zero here can only come from a modified client trying to
        // make the receiver verify against the host key, and it gets the same
        // treatment as any other unverifiable action.
        byte[] forged = recordWithVoluntary(false);
        assertTrue(Crupier.isVerifiableWireRecord(forged),
                "the record is well formed: what disqualifies it is the flag, not its shape");
        assertFalse(Crupier.readWireVoluntaryFlag(forged));
    }

    // ---- round trip: what the host emits is what the receiver rejects -----

    /** Splits the wire the way the receiving loop does, envelope included. */
    private static String[] asReceived(String comando) {
        return ("GAME#123456#" + comando).split("#");
    }

    @Test
    @DisplayName("The synthetic fold the host emits carries no record, so every receiver synthesises it too")
    void theSynthFoldWireIsUnsignedOnEveryReceiver() {
        Object[] action = genuineBetAction();
        Crupier.synthesizeUnverifiedFoldAction(action);

        // Exactly what the betting round builds once the synth replaced the action:
        // no local record is built for a synthetic fold, so both slots go out as "*".
        String comando = Crupier.buildActionWireCommand("alice", (int) action[0], action[1],
                "*", (byte[]) action[3], (byte[]) action[4]);

        assertEquals("ACTION#" + Base64.getEncoder().encodeToString("alice".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + "#" + Player.FOLD + "#0#*#*#*", comando);

        String[] partes = asReceived(comando);
        assertFalse(Crupier.wireCarriesRecordAndSig(partes),
                "the receiver must take this as an unsigned action and synthesise the same fold");
    }

    @Test
    @DisplayName("An honest action keeps carrying its record and signature")
    void anHonestActionWireStillCarriesRecordAndSig() {
        byte[] record = new byte[CanonicalActionRecord.RECORD_BYTES];
        Arrays.fill(record, (byte) 0x11);
        byte[] sig = new byte[64];
        Arrays.fill(sig, (byte) 0x22);

        String comando = Crupier.buildActionWireCommand("alice", Player.BET, 50d, "*", record, sig);

        String[] partes = asReceived(comando);
        assertTrue(Crupier.wireCarriesRecordAndSig(partes));
        assertEquals("50.0", partes[5], "the BET amount travels in the plaintext slot");
        assertArrayEqualsB64(record, partes[7]);
        assertArrayEqualsB64(sig, partes[8]);
    }

    @Test
    @DisplayName("A stripped or truncated action never counts as carrying a record")
    void strippedWiresNeverCarryRecordAndSig() {
        assertFalse(Crupier.wireCarriesRecordAndSig(null));
        // Pre-identity shape: no record/sig fields at all.
        assertFalse(Crupier.wireCarriesRecordAndSig("GAME#1#ACTION#bm9iaQ==#0#0#*".split("#")));
        // Both stripped, only the record stripped, only the sig stripped.
        assertFalse(Crupier.wireCarriesRecordAndSig("GAME#1#ACTION#bm9iaQ==#0#0#*#*#*".split("#")));
        assertFalse(Crupier.wireCarriesRecordAndSig("GAME#1#ACTION#bm9iaQ==#0#0#*#*#c2ln".split("#")));
        assertFalse(Crupier.wireCarriesRecordAndSig("GAME#1#ACTION#bm9iaQ==#0#0#*#cmVj#*".split("#")));
        assertTrue(Crupier.wireCarriesRecordAndSig("GAME#1#ACTION#bm9iaQ==#0#0#*#cmVj#c2ln".split("#")));
    }

    @Test
    @DisplayName("The all-in cinematic rides its own slot and never leaks into the amount")
    void allInWireKeepsTheAmountAtZero() {
        // On ALLIN the amount slot is fixed at 0 and the animation travels apart;
        // only a BET writes a figure there.
        String comando = Crupier.buildActionWireCommand("alice", Player.ALLIN, 0d, "hulk_b64", null, null);
        String[] partes = asReceived(comando);
        assertEquals("0", partes[5]);
        assertEquals("hulk_b64", partes[6]);
    }

    private static void assertArrayEqualsB64(byte[] expected, String b64) {
        assertTrue(Arrays.equals(expected, Base64.getDecoder().decode(b64)));
    }
}
