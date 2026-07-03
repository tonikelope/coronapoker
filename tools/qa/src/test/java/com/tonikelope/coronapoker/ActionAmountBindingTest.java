/*
 * ZERO-TRUST anti-forgery: unit tests for the money<->record binding.
 *
 * The receiver ties the SIGNED amount (AMOUNT_CENTS of the canonical action
 * record) to the action actually played, by recomputing the expected amount
 * from the decision + its own replicated pre-action state
 * (Crupier.expectedActionAmountCents) and comparing it against the record. A
 * modified client that signs one amount while playing another no longer slips a
 * false figure into the signed history / H_t.
 *
 * These tests pin the PURE formula (the same one the signer uses in
 * buildLocalActionRecordAndSig) and the accept/reject decision against a real
 * CanonicalActionRecord, for every action type, with no game engine needed.
 */
package com.tonikelope.coronapoker;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ActionAmountBindingTest {

    private static byte[] fill(int len, byte b) {
        byte[] out = new byte[len];
        Arrays.fill(out, b);
        return out;
    }

    /** Builds a real 92-byte record carrying {@code amountCents}, then reads it back. */
    private static long roundTripAmount(long amountCents, boolean isAllin) {
        byte[] record = CanonicalActionRecord.encode(
                fill(32, (byte) 0x11), fill(16, (byte) 0x22), fill(32, (byte) 0x33),
                CanonicalActionRecord.STREET_PREFLOP,
                CanonicalActionRecord.ACTION_BET,
                amountCents, isAllin, true);
        return CanonicalActionRecord.readAmountCents(record);
    }

    // ---- the pure formula, per action type -------------------------------

    @Test
    public void foldIsAlwaysZero() {
        // No money moves on a fold, whatever the state.
        assertEquals(0L, Crupier.expectedActionAmountCents(Player.FOLD, 99.0, 40.0, 60.0, 12.34));
    }

    @Test
    public void checkOrCallBindsToApuestaActual() {
        // CHECK covers both a true check and a call; the amount is the current
        // bet to match, NOT the plaintext bet field (which is 0 on the wire).
        assertEquals(1234L, Crupier.expectedActionAmountCents(Player.CHECK, 0.0, 5.0, 100.0, 12.34));
        assertEquals(0L, Crupier.expectedActionAmountCents(Player.CHECK, 0.0, 0.0, 100.0, 0.0));
    }

    @Test
    public void betBindsToTheAbsoluteWireTarget() {
        // BET is the only type whose amount comes from the plaintext bet
        // (partes[5]); the receiver must derive the record amount from that same
        // value so a signed-vs-played mismatch is caught.
        assertEquals(5000L, Crupier.expectedActionAmountCents(Player.BET, 50.0, 5.0, 100.0, 10.0));
        assertEquals(1234L, Crupier.expectedActionAmountCents(Player.BET, 12.34, 0.0, 0.0, 0.0));
    }

    @Test
    public void allInBindsToBetPlusStack() {
        // ALLIN moves the whole stack into the bet: amount == bet + stack, a
        // pre/post-action invariant, independent of the plaintext bet field.
        assertEquals(10000L, Crupier.expectedActionAmountCents(Player.ALLIN, 0.0, 40.0, 60.0, 10.0));
        assertEquals(6050L, Crupier.expectedActionAmountCents(Player.ALLIN, 0.0, 12.5, 48.0, 999.0));
    }

    @Test
    public void unknownDecisionIsZero() {
        assertEquals(0L, Crupier.expectedActionAmountCents(-999, 50.0, 5.0, 100.0, 10.0));
    }

    // ---- the accept / reject decision against a real record --------------

    @Test
    public void honestRecordAmountMatchesExpected() {
        // Signer and receiver run the SAME formula over the SAME pre-action
        // state, so an honest record's amount equals the receiver's expectation.
        long expectedBet = Crupier.expectedActionAmountCents(Player.BET, 37.5, 0.0, 0.0, 0.0);
        assertEquals(expectedBet, roundTripAmount(expectedBet, false), "honest BET must bind");

        long expectedCall = Crupier.expectedActionAmountCents(Player.CHECK, 0.0, 0.0, 0.0, 20.0);
        assertEquals(expectedCall, roundTripAmount(expectedCall, false), "honest call must bind");

        long expectedAllin = Crupier.expectedActionAmountCents(Player.ALLIN, 0.0, 15.0, 85.0, 0.0);
        assertEquals(expectedAllin, roundTripAmount(expectedAllin, true), "honest all-in must bind");
    }

    @Test
    public void tamperedRecordAmountIsDetected() {
        // A modified client signs amount Y while the played decision implies X:
        // the receiver's expectation never equals the tampered figure.
        long expected = Crupier.expectedActionAmountCents(Player.BET, 50.0, 0.0, 0.0, 0.0); // 5000
        long tampered = roundTripAmount(9999L, false);
        assertNotEquals(expected, tampered, "a bet signed at a different amount must NOT bind");

        // Under-signing (claim a smaller amount than played) is caught too.
        long expectedCall = Crupier.expectedActionAmountCents(Player.CHECK, 0.0, 0.0, 0.0, 40.0); // 4000
        assertNotEquals(expectedCall, roundTripAmount(1L, false), "an under-signed call must NOT bind");
    }

    @Test
    public void centsRoundingIsDeterministicNoJitter() {
        // Float money that is exact in cents must land on the exact cents value,
        // with no IEEE-754 drift, so honest actions never fail to bind by 1 cent.
        assertEquals(1005L, Crupier.expectedActionAmountCents(Player.BET, 10.05, 0.0, 0.0, 0.0));
        assertEquals(333L, Crupier.expectedActionAmountCents(Player.BET, 3.33, 0.0, 0.0, 0.0));
        // The all-in sum of two clean cents values is itself clean.
        assertEquals(4567L, Crupier.expectedActionAmountCents(Player.ALLIN, 0.0, 12.34, 33.33, 0.0));
    }

    @Test
    public void unrepresentableBetAmountThrows() {
        // A NaN/Infinite bet is rejected at the cents conversion; the receiver
        // treats that throw as forgery (synth-fold), an honest bet never hits it.
        assertThrows(RuntimeException.class,
                () -> Crupier.expectedActionAmountCents(Player.BET, Double.NaN, 0.0, 0.0, 0.0));
        assertThrows(RuntimeException.class,
                () -> Crupier.expectedActionAmountCents(Player.BET, Double.POSITIVE_INFINITY, 0.0, 0.0, 0.0));
    }

    @Test
    public void recordAmountFieldIsFaithfulAcrossTheEncoder() {
        // Guards the round-trip helper itself: whatever cents we encode is what we read.
        for (long cents : new long[]{0L, 1L, 2500L, 1_000_000L}) {
            assertEquals(cents, roundTripAmount(cents, false));
        }
        assertTrue(roundTripAmount(0L, true) == 0L);
    }

    // ---- full binding: TYPE + PLAYER_ID + HAND_ID + AMOUNT ----------------

    // The identifiers baked into every record built by recordWith().
    private static final byte[] PID = fill(32, (byte) 0x33);
    private static final byte[] HID = fill(16, (byte) 0x22);

    /** Builds a record with a chosen ACTION_TYPE/amount and the canonical PID/HID above. */
    private static byte[] recordWith(int wireActionType, long amountCents, boolean isAllin) {
        return CanonicalActionRecord.encode(
                fill(32, (byte) 0x11), fill(16, (byte) 0x22), fill(32, (byte) 0x33),
                CanonicalActionRecord.STREET_PREFLOP, wireActionType, amountCents, isAllin, true);
    }

    @Test
    public void bindsWhenTypePlayerHandAndAmountAllMatch() {
        assertTrue(Crupier.signedRecordBindsToAction(
                recordWith(CanonicalActionRecord.ACTION_FOLD, 0L, false),
                Player.FOLD, 0.0, 5.0, 100.0, 12.34, PID, HID));
        assertTrue(Crupier.signedRecordBindsToAction(
                recordWith(CanonicalActionRecord.ACTION_CHECK, 2000L, false),
                Player.CHECK, 0.0, 0.0, 0.0, 20.0, PID, HID));
        assertTrue(Crupier.signedRecordBindsToAction(
                recordWith(CanonicalActionRecord.ACTION_BET, 5000L, false),
                Player.BET, 50.0, 0.0, 0.0, 0.0, PID, HID));
        assertTrue(Crupier.signedRecordBindsToAction(
                recordWith(CanonicalActionRecord.ACTION_ALLIN, 10000L, true),
                Player.ALLIN, 0.0, 40.0, 60.0, 0.0, PID, HID));
    }

    @Test
    public void rejectsTypeMismatchEvenWhenAmountMatches() {
        // FOLD and a CHECK at apuesta_actual=0 BOTH carry amount 0; binding the
        // action TYPE tells them apart.
        assertFalse(Crupier.signedRecordBindsToAction(
                recordWith(CanonicalActionRecord.ACTION_CHECK, 0L, false),
                Player.FOLD, 0.0, 5.0, 100.0, 0.0, PID, HID));
        assertFalse(Crupier.signedRecordBindsToAction(
                recordWith(CanonicalActionRecord.ACTION_FOLD, 0L, false),
                Player.CHECK, 0.0, 5.0, 100.0, 0.0, PID, HID));
        assertFalse(Crupier.signedRecordBindsToAction(
                recordWith(CanonicalActionRecord.ACTION_BET, 10000L, false),
                Player.ALLIN, 0.0, 40.0, 60.0, 0.0, PID, HID));
    }

    @Test
    public void rejectsAmountMismatchWithMatchingType() {
        assertFalse(Crupier.signedRecordBindsToAction(
                recordWith(CanonicalActionRecord.ACTION_BET, 9999L, false),
                Player.BET, 50.0, 0.0, 0.0, 0.0, PID, HID));
        assertFalse(Crupier.signedRecordBindsToAction(
                recordWith(CanonicalActionRecord.ACTION_CHECK, 1L, false),
                Player.CHECK, 0.0, 0.0, 0.0, 40.0, PID, HID));
    }

    @Test
    public void rejectsPlayerIdMismatch() {
        // A record that signs a valid action but attributes it to a DIFFERENT
        // player must not bind, even though type + amount match.
        byte[] otherPid = fill(32, (byte) 0x44);
        assertFalse(Crupier.signedRecordBindsToAction(
                recordWith(CanonicalActionRecord.ACTION_BET, 5000L, false),
                Player.BET, 50.0, 0.0, 0.0, 0.0, otherPid, HID));
    }

    @Test
    public void rejectsHandIdMismatch() {
        // A record replayed from a different hand must not bind.
        byte[] otherHid = fill(16, (byte) 0x55);
        assertFalse(Crupier.signedRecordBindsToAction(
                recordWith(CanonicalActionRecord.ACTION_BET, 5000L, false),
                Player.BET, 50.0, 0.0, 0.0, 0.0, PID, otherHid));
    }

    @Test
    public void nullExpectedIdentifiersSkipThatCheckButKeepTheRest() {
        // Null expected PID/HID (e.g., chain not seeded) must not throw and must
        // still enforce type + amount.
        assertTrue(Crupier.signedRecordBindsToAction(
                recordWith(CanonicalActionRecord.ACTION_BET, 5000L, false),
                Player.BET, 50.0, 0.0, 0.0, 0.0, null, null));
        assertFalse(Crupier.signedRecordBindsToAction(
                recordWith(CanonicalActionRecord.ACTION_BET, 9999L, false),
                Player.BET, 50.0, 0.0, 0.0, 0.0, null, null));
    }

    @Test
    public void allInBindingIgnoresTheCinematicStringInBetSlot() {
        // On ALLIN the wire bet slot is overloaded with a cinematic String; the
        // binding must ignore it and use bet+stack, never throw on the String.
        assertTrue(Crupier.signedRecordBindsToAction(
                recordWith(CanonicalActionRecord.ACTION_ALLIN, 6050L, true),
                Player.ALLIN, "cinematic_b64_blob", 12.5, 48.0, 999.0, PID, HID));
    }

    @Test
    public void unmappableDecisionThrowsSoTheReceiverTreatsItAsForgery() {
        // A garbage decision cannot map to a wire type; the pure check throws and
        // the receiver's try/catch turns it into a synth-fold.
        assertThrows(RuntimeException.class, () -> Crupier.signedRecordBindsToAction(
                recordWith(CanonicalActionRecord.ACTION_BET, 5000L, false),
                -999, 50.0, 0.0, 0.0, 0.0, PID, HID));
    }

    @Test
    public void unrepresentableBetAmountThrowsInFullBindingToo() {
        // NaN/Inf bet on a BET must throw (caught upstream as forgery), not bind.
        assertThrows(RuntimeException.class, () -> Crupier.signedRecordBindsToAction(
                recordWith(CanonicalActionRecord.ACTION_BET, 5000L, false),
                Player.BET, Double.NaN, 0.0, 0.0, 0.0, PID, HID));
    }

    // ---- RECOVER replay binding (recoveredActionBindsToRecord) -------------
    // Closes the HIGH: on recovery the client replays its OWN actions from the
    // host's copy and re-signs them; this state-free bind stops a hostile host
    // from serving a forged decision/amount for the victim's own action.

    /** A record carrying nick's real PLAYER_ID and a chosen hand id / type / amount. */
    private static byte[] recoveryRecord(String nick, byte[] handId, int wireActionType, long amountCents) {
        return CanonicalActionRecord.encode(
                fill(32, (byte) 0x11), handId, CanonicalActionRecord.playerIdFromNick(nick),
                CanonicalActionRecord.STREET_PREFLOP, wireActionType, amountCents, false, true);
    }

    @Test
    public void recoveredActionBindsWhenTypePlayerHandAndBetMatch() {
        byte[] hid = fill(16, (byte) 0x77);
        assertTrue(Crupier.recoveredActionBindsToRecord(
                recoveryRecord("alice", hid, CanonicalActionRecord.ACTION_BET, 5000L),
                Player.BET, 50.0, "alice", hid));
        assertTrue(Crupier.recoveredActionBindsToRecord(
                recoveryRecord("alice", hid, CanonicalActionRecord.ACTION_FOLD, 0L),
                Player.FOLD, 0.0, "alice", hid));
        // CHECK/ALLIN amount is game-rule derived (not forgeable via the record here),
        // so only type/player/hand are bound; a nonzero record amount still binds.
        assertTrue(Crupier.recoveredActionBindsToRecord(
                recoveryRecord("alice", hid, CanonicalActionRecord.ACTION_CHECK, 9999L),
                Player.CHECK, 0.0, "alice", hid));
    }

    @Test
    public void recoveredActionRejectsBetAmountForgery() {
        byte[] hid = fill(16, (byte) 0x77);
        assertFalse(Crupier.recoveredActionBindsToRecord(
                recoveryRecord("alice", hid, CanonicalActionRecord.ACTION_BET, 9999L),
                Player.BET, 50.0, "alice", hid));
    }

    @Test
    public void recoveredActionRejectsTypePlayerAndHandForgery() {
        byte[] hid = fill(16, (byte) 0x77);
        // Host serves a FOLD record but replays it as a CHECK (both 0 cents) — type binding catches it.
        assertFalse(Crupier.recoveredActionBindsToRecord(
                recoveryRecord("alice", hid, CanonicalActionRecord.ACTION_FOLD, 0L),
                Player.CHECK, 0.0, "alice", hid));
        // Record signed for alice, replayed under bob's slot — player binding catches it.
        assertFalse(Crupier.recoveredActionBindsToRecord(
                recoveryRecord("alice", hid, CanonicalActionRecord.ACTION_BET, 5000L),
                Player.BET, 50.0, "bob", hid));
        // Record from a different hand replayed into this one — hand binding catches it.
        assertFalse(Crupier.recoveredActionBindsToRecord(
                recoveryRecord("alice", hid, CanonicalActionRecord.ACTION_BET, 5000L),
                Player.BET, 50.0, "alice", fill(16, (byte) 0x88)));
    }

    @Test
    public void recoveredActionNullHandIdSkipsOnlyTheHandCheck() {
        byte[] hid = fill(16, (byte) 0x77);
        assertTrue(Crupier.recoveredActionBindsToRecord(
                recoveryRecord("alice", hid, CanonicalActionRecord.ACTION_BET, 5000L),
                Player.BET, 50.0, "alice", null));
        // type/player still enforced even with a null hand id
        assertFalse(Crupier.recoveredActionBindsToRecord(
                recoveryRecord("alice", hid, CanonicalActionRecord.ACTION_BET, 5000L),
                Player.BET, 50.0, "bob", null));
    }

    @Test
    public void recoveredActionUnmappableDecisionIsRejectedNotThrown() {
        // A garbage decision cannot map to a wire type; the helper returns false (forgery), never throws.
        byte[] hid = fill(16, (byte) 0x77);
        assertFalse(Crupier.recoveredActionBindsToRecord(
                recoveryRecord("alice", hid, CanonicalActionRecord.ACTION_BET, 5000L),
                -999, 50.0, "alice", hid));
    }
}
