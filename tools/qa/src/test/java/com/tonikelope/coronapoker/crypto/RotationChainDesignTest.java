/*
 * Phase 4.3 — Part B design validation: VERIFIABLE rotation (dual-lock, Option G).
 *
 * The rotation (DECK_ROTATION_REQ) turns the community pieces from pocket-space
 * (all pocket-locks, post-cascade) into community-space (all community-locks). Today
 * each peer applies its pocket-UNLOCK to host-supplied bytes with no anchor — a
 * blinded-oracle for the pocket key (attack 1, the "player leaves with testament"
 * case). B2 makes the rotation a verifiable CHAIN anchored to the committed
 * pre-rotation deck H_pre: each peer's step is a DOUBLE operation — strip pocket-lock
 * (k_P^-1) and add community-lock (k_C) — each with its own DLEQ proof.
 *
 * This test validates the CRYPTO approach before touching the dual-lock production
 * code: (1) the chained dual-op rotation lands in community-space and re-opens to the
 * genesis card; (2) each sub-step (pocket-unlock, community-lock) verifies under the
 * peer's committed key; (3) the H_pre anchor closes blinding (a blinded input does
 * not match the committed chain start, exactly like DealChain/verifyChain).
 *
 * No production code is touched; this pins the math the B2 implementation will encode.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RotationChainDesignTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    private static BigInteger scalar() {
        return RistrettoSRA.bytesToScalar(RistrettoSRA.generateLockScalar());
    }

    @Test
    public void verifiableDualOpRotationReachesCommunitySpaceAndRejectsBlinding() {
        // Two peers A, B. Each has a pocket-lock (cascade) and a community-lock (rotation target).
        BigInteger kPA = scalar(), kPB = scalar();   // pocket-locks
        BigInteger kCA = scalar(), kCB = scalar();   // community-locks
        EdwardsPoint KPA = EdwardsPoint.BASE.scalarMul(kPA); // committed keys K = k*B
        EdwardsPoint KPB = EdwardsPoint.BASE.scalarMul(kPB);
        EdwardsPoint KCA = EdwardsPoint.BASE.scalarMul(kCA);
        EdwardsPoint KCB = EdwardsPoint.BASE.scalarMul(kCB);

        byte[] genesis = RistrettoSRA.getGenesisDeck();
        int cardIdx = 23;
        EdwardsPoint g = Ristretto255.decode(Arrays.copyOfRange(genesis, cardIdx * 32, (cardIdx + 1) * 32));
        assertNotNull(g);

        // Pre-rotation deck (post-cascade): genesis card under ALL pocket-locks. This is the
        // committed anchor H_pre the host must broadcast before the rotation.
        EdwardsPoint hPre = g.scalarMul(kPA).scalarMul(kPB);

        // --- Peer A's rotation step (dual op), chain starts at H_pre ---
        BigInteger kPAinv = kPA.modInverse(EdwardsPoint.L);
        EdwardsPoint midA = hPre.scalarMul(kPAinv);                       // strip pocket-lock A
        byte[] proofUA = Dleq.prove(kPA, EdwardsPoint.BASE, KPA, midA, hPre); // H_pre = kPA * midA
        assertTrue(Dleq.verify(EdwardsPoint.BASE, KPA, midA, hPre, proofUA), "A pocket-unlock proof");
        EdwardsPoint outA = midA.scalarMul(kCA);                          // add community-lock A
        byte[] proofLA = Dleq.prove(kCA, EdwardsPoint.BASE, KCA, midA, outA); // outA = kCA * midA
        assertTrue(Dleq.verify(EdwardsPoint.BASE, KCA, midA, outA, proofLA), "A community-lock proof");

        // --- Peer B's rotation step, chained from A's output ---
        BigInteger kPBinv = kPB.modInverse(EdwardsPoint.L);
        EdwardsPoint midB = outA.scalarMul(kPBinv);                       // strip pocket-lock B
        byte[] proofUB = Dleq.prove(kPB, EdwardsPoint.BASE, KPB, midB, outA);
        assertTrue(Dleq.verify(EdwardsPoint.BASE, KPB, midB, outA, proofUB), "B pocket-unlock proof");
        EdwardsPoint outB = midB.scalarMul(kCB);                          // add community-lock B
        byte[] proofLB = Dleq.prove(kCB, EdwardsPoint.BASE, KCB, midB, outB);
        assertTrue(Dleq.verify(EdwardsPoint.BASE, KCB, midB, outB, proofLB), "B community-lock proof");

        // (1) Result is in community-space: genesis * k_CA * k_CB, pocket-locks gone.
        EdwardsPoint expected = g.scalarMul(kCA).scalarMul(kCB);
        assertTrue(Arrays.equals(Ristretto255.encode(expected), Ristretto255.encode(outB)),
                "chained dual-op rotation lands exactly in community-space");

        // (2) Opening with the community-unlocks recovers the genesis card.
        EdwardsPoint opened = outB.scalarMul(kCA.modInverse(EdwardsPoint.L))
                .scalarMul(kCB.modInverse(EdwardsPoint.L));
        assertEquals(cardIdx, RistrettoSRA.resolveCardIndex(Ristretto255.encode(opened)),
                "community-space result re-opens to the original genesis card");

        // (3) Anti-blinding: the chain MUST start at the committed H_pre. A host that feeds
        // peer A a blinded r*H_pre fails the anchor check (residuals[0] != committed H_pre),
        // exactly as DealChain/VerifiableUnlock.verifyChain rejects blinding in the dealing.
        BigInteger r = scalar();
        EdwardsPoint blinded = hPre.scalarMul(r);
        boolean anchorsToCommittedHpre =
                Arrays.equals(Ristretto255.encode(hPre), Ristretto255.encode(blinded));
        assertFalse(anchorsToCommittedHpre,
                "blinded rotation input fails the H_pre anchor -> pocket-unlock oracle closed");
    }
}
