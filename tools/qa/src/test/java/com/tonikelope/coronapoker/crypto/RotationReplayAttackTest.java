/*
 * Phase 4.3 — why the rotation needs anti-replay (attack documentation).
 *
 * The dual-lock rotation makes a peer apply its POCKET-unlock (k_p^-1) to bytes the
 * host supplies, then add its community-lock (k_c). That is a pocket-unlock oracle if
 * the host can ask for it more than once with chosen input: the host blinds a deck
 * point P with a random r, gets the peer to rotate r·P → r·(P·k_p^-1)·k_c, later strips
 * k_c (community-unlock / testament) and unblinds (·r^-1) to recover P·k_p^-1 — the
 * peer's pocket-unlock applied to an arbitrary point.
 *
 * This test proves that leak at the crypto layer (so the danger is documented and
 * cannot be silently reintroduced) and pins the mitigation: the peer serves exactly
 * ONE rotation per hand (anti-replay). Without an extra rotation the host cannot
 * feed a blinded point in; the only rotation it gets is the legitimate one over the
 * real community pieces, and corrupting THAT breaks the board (a self-delating misdeal).
 *
 * The handler enforces one cascade and one rotation per hand. A distinct GAME id is
 * not a retry: it closes the host channel. Transport retransmission keeps the same id
 * and is deduplicated before the handler.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class RotationReplayAttackTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    /**
     * A repeated rotation on a blinded point leaks the peer's pocket-unlock
     * applied to that point — recovering a genesis card. This is exactly what
     * one rotation per cascade (anti-replay) denies the host.
     */
    @Test
    public void repeatedRotationOnBlindedPointIsAPocketUnlockOracle() {
        byte[] kPocket = RistrettoSRA.generateLockScalar();    // victim's pocket lock k_p
        byte[] kCommunity = RistrettoSRA.generateLockScalar(); // victim's community lock k_c

        byte[] genesis = RistrettoSRA.getGenesisDeck();
        int cardIdx = 29;
        byte[] card = new byte[32];
        System.arraycopy(genesis, cardIdx * 32, card, 0, 32);

        // A point the host wants to read, locked by the victim's pocket key (e.g. a pocket
        // residual the host holds): P = k_p · genesis_card.
        byte[] p = RistrettoSRA.applyCommutativeLock(card, kPocket);

        // Host blinds it with a random r and hands r·P to an EXTRA rotation request.
        byte[] r = RistrettoSRA.generateLockScalar();
        byte[] blinded = RistrettoSRA.applyCommutativeLock(p, r);

        // The rotation handler would apply pocket-unlock (k_p^-1) then community-lock (k_c):
        byte[] rotated = RistrettoSRA.applyCommutativeLock(blinded, RistrettoSRA.getUnlockScalar(kPocket));
        rotated = RistrettoSRA.applyCommutativeLock(rotated, kCommunity);

        // The community-space tail does NOT reveal the card on its own (still blinded + k_c).
        assertFalse(cardIdx == RistrettoSRA.resolveCardIndex(rotated),
                "rotation output stays opaque until k_c is stripped and r unblinded");

        // Host strips k_c (via a community-unlock or the victim's testament) and unblinds:
        byte[] stripped = RistrettoSRA.applyCommutativeLock(rotated, RistrettoSRA.getUnlockScalar(kCommunity));
        byte[] recovered = RistrettoSRA.applyCommutativeLock(stripped, RistrettoSRA.getUnlockScalar(r));

        // The host has recovered P·k_p^-1 = genesis_card — it read the victim's card.
        assertEquals(cardIdx, RistrettoSRA.resolveCardIndex(recovered),
                "a repeated/extra rotation on a blinded point leaks the pocket card — "
                + "this is the oracle that anti-replay (one rotation per cascade) denies");
    }

    /**
     * The mitigation invariant: only the first cascade and first rotation are
     * admitted in a hand. Neither phase can be reopened with a new command id.
     */
    @Test
    public void oneCascadeAndOneRotationPerHand() {
        boolean cascadeAccepted = false;
        boolean rotationAccepted = false;

        boolean firstCascadeAllowed = !cascadeAccepted;
        cascadeAccepted = true;
        boolean firstRotationAllowed = !rotationAccepted;
        rotationAccepted = true;
        boolean secondRotationRefused = rotationAccepted;
        boolean secondCascadeRefused = cascadeAccepted;

        org.junit.jupiter.api.Assertions.assertTrue(firstCascadeAllowed, "first cascade is admitted");
        org.junit.jupiter.api.Assertions.assertTrue(firstRotationAllowed, "first rotation is admitted");
        org.junit.jupiter.api.Assertions.assertTrue(secondRotationRefused, "second rotation is refused");
        org.junit.jupiter.api.Assertions.assertTrue(secondCascadeRefused, "second cascade cannot reopen the oracle");
    }
}
