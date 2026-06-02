/*
 * Phase 4.3 Part B — RotationChain engine test.
 *
 * Drives the verifiable dual-lock rotation the way production will: peers chain their
 * rotation steps (pocket-unlock + community-lock, both proven) starting from the
 * committed pre-rotation point H_pre; the final tail is the piece in community-space,
 * which re-opens to the genesis card with the community-unlocks. Confirms the chain
 * verifies, the round-trip reveals the card, and a chain anchored to the real H_pre is
 * refused when re-anchored to a blinded point (closes the pocket-unlock oracle).
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RotationChainTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    @Test
    public void chainedRotationReachesCommunitySpaceRevealsCardAndRejectsBlinding() {
        byte[] kPA = RistrettoSRA.generateLockScalar(), kPB = RistrettoSRA.generateLockScalar();
        byte[] kCA = RistrettoSRA.generateLockScalar(), kCB = RistrettoSRA.generateLockScalar();
        Map<String, byte[]> pocketK = new HashMap<>();
        pocketK.put("A", RistrettoSRA.commitment(kPA));
        pocketK.put("B", RistrettoSRA.commitment(kPB));
        Map<String, byte[]> communityK = new HashMap<>();
        communityK.put("A", RistrettoSRA.commitment(kCA));
        communityK.put("B", RistrettoSRA.commitment(kCB));

        byte[] genesis = RistrettoSRA.getGenesisDeck();
        int cardIdx = 38;
        byte[] card = new byte[32];
        System.arraycopy(genesis, cardIdx * 32, card, 0, 32);

        // Pre-rotation point (post-cascade): card under both pocket-locks. Committed H_pre.
        byte[] hPre = RistrettoSRA.applyCommutativeLock(card, kPA);
        hPre = RistrettoSRA.applyCommutativeLock(hPre, kPB);

        // A rotates from H_pre, then B chains from A's output.
        RotationChain.Extended extA = RotationChain.extend(hPre, "", pocketK, communityK, "A", kPA, kCA);
        assertNotNull(extA, "A's rotation step must succeed");
        RotationChain.Extended extB = RotationChain.extend(hPre, extA.wire, pocketK, communityK, "B", kPB, kCB);
        assertNotNull(extB, "B's chained rotation step must succeed");

        List<RotationChain.Entry> chain = RotationChain.parse(extB.wire);
        assertNotNull(chain);
        assertEquals(2, chain.size());
        assertTrue(RotationChain.verify(hPre, chain, pocketK, communityK), "final rotation chain must verify");

        // Tail is in community-space: opening with the community-unlocks recovers the card.
        byte[] tail = RotationChain.tail(hPre, chain);
        byte[] opened = RistrettoSRA.applyCommutativeLock(tail, RistrettoSRA.getUnlockScalar(kCA));
        opened = RistrettoSRA.applyCommutativeLock(opened, RistrettoSRA.getUnlockScalar(kCB));
        assertEquals(cardIdx, RistrettoSRA.resolveCardIndex(opened),
                "rotated piece re-opens to the genesis card with the community-unlocks");

        // The single-locked-by-pocket tail must NOT reveal the card (still encrypted).
        assertFalse(cardIdx == RistrettoSRA.resolveCardIndex(tail),
                "community-space tail stays opaque without the community-unlocks");

        // Blinding closed: A's chain (anchored to real H_pre) fails against a blinded H_pre.
        byte[] r = RistrettoSRA.generateLockScalar();
        byte[] blinded = RistrettoSRA.applyCommutativeLock(hPre, r);
        assertNull(RotationChain.extend(blinded, extA.wire, pocketK, communityK, "B", kPB, kCB),
                "rotation refuses: A's committed chain does not anchor to the blinded H_pre");
    }
}
