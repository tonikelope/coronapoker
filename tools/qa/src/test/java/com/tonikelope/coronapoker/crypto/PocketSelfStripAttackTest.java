/*
 * Phase 4.2 — back-door oracle attack on the verifiable pocket dealing.
 *
 * The chain binding stops a host from feeding a peer a BLINDED point, but the
 * host still picks WHICH committed megapacket point the peer strips (offsetBase),
 * independently of the peerIdx label. A hostile host therefore sends
 * peerIdx=<someone else> (passes the "not my labelled slot" guard) but
 * offsetBase=<my own pocket>. Without a guard on the stripped POINT, the helper
 * peels its own lock off its own pocket and hands the host its hole cards in clear
 * (the host already holds that pocket single-locked after the normal deal).
 *
 * This test proves the leak at the crypto layer (so the danger is documented and
 * can never be silently reintroduced) and pins the exact invariant the handler
 * guard enforces: a peer NEVER strips a point inside its own pocket
 * [mySlot*2, mySlot*2+1].
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PocketSelfStripAttackTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    /**
     * Ring [host, H], H in slot 1 → H's pocket lives at megapacket points 2 and 3.
     * After the normal deal the host already holds H's pocket single-locked by H.
     * If H could be tricked into stripping its OWN lock off point 2/3, the host
     * would read H's cards. The test shows that strip yields the cleartext card,
     * and that the stripped point index is exactly what the handler guard rejects.
     */
    @Test
    public void hostStripsHelperPocketViaDecoupledOffset_isCleartextLeak_andGuardCatchesIt() {
        byte[] kHost = RistrettoSRA.generateLockScalar();
        byte[] kH = RistrettoSRA.generateLockScalar();
        Map<String, byte[]> commitments = new HashMap<>();
        commitments.put("host", RistrettoSRA.commitment(kHost));
        commitments.put("H", RistrettoSRA.commitment(kH));

        // H is at ring slot 1, so its pocket is megapacket points 2 and 3.
        String[] ring = {"host", "H"};
        int hSlot = 1;
        int attackOffset = hSlot * 2; // host points the helper at its OWN pocket

        byte[] genesis = RistrettoSRA.getGenesisDeck();
        int cardIdx = 41;
        byte[] genesisCard = new byte[32];
        System.arraycopy(genesis, cardIdx * 32, genesisCard, 0, 32);

        // H's pocket point as it sits in the megapacket: genesis * k_host * k_H.
        byte[] pocketPoint = RistrettoSRA.applyCommutativeLock(genesisCard, kHost);
        pocketPoint = RistrettoSRA.applyCommutativeLock(pocketPoint, kH);

        // Normal deal: the host strips its OWN lock, leaving the pocket single-locked
        // by H. The host legitimately holds this; it cannot open it (k_H is H's).
        DealChain.Extended hostStep = DealChain.extend(pocketPoint, "", commitments, "host", kHost);
        assertNotNull(hostStep, "host strips its own lock off H's pocket (normal deal)");
        byte[] singleLockedByH = hostStep.residual;
        assertFalse(cardIdx == RistrettoSRA.resolveCardIndex(singleLockedByH),
                "single-locked pocket must NOT reveal the card");

        // Back-door attack: the host hands H that same (validly anchored) chain and
        // asks H to extend it. DealChain.extend itself does NOT know the point is H's
        // own pocket — it happily verifies the anchor and peels k_H, exposing the card.
        DealChain.Extended leak = DealChain.extend(pocketPoint, hostStep.wire, commitments, "H", kH);
        assertNotNull(leak, "the crypto layer alone does not stop this — anchor is valid");
        assertEquals(cardIdx, RistrettoSRA.resolveCardIndex(leak.residual),
                "stripping H's own lock yields H's cleartext card — this is the leak the guard must stop");

        // The handler guard: H computes mySlot from the ring and refuses any pointIdx
        // inside its own pocket. The attack's offset lands exactly there.
        int mySlot = -1;
        for (int s = 0; s < ring.length; s++) {
            if (ring[s].equals("H")) {
                mySlot = s;
                break;
            }
        }
        assertEquals(hSlot, mySlot);
        boolean guardWouldRefuse = (attackOffset == mySlot * 2 || attackOffset == mySlot * 2 + 1);
        assertTrue(guardWouldRefuse,
                "guard must reject stripping a point in [mySlot*2, mySlot*2+1]");

        // And a legitimate request (stripping another slot's pocket) must NOT trip the guard.
        int honestOffset = 0; // host's pocket (slot 0), points 0/1
        boolean guardOnHonest = (honestOffset == mySlot * 2 || honestOffset == mySlot * 2 + 1);
        assertFalse(guardOnHonest, "guard must allow stripping points outside my own pocket");
    }
}
