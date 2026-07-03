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
}
