/*
 * Phase 4 (verifiable dealing) — end-to-end harness test.
 *
 * Validates the de-locking-chain logic that closes the blinded decryption oracle,
 * WITHOUT touching the game: an honest chain from the committed deck point verifies
 * and reveals the card; a BLINDED start (the exact PoC attack) is rejected by the
 * chain binding; tampered proofs / uncommitted keys are rejected; and the global
 * index collision check catches a duplicated card.
 *
 * This is the security core of the fix, proven in isolation before it is wired into
 * Crupier (which additionally needs the author's manual smoke).
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VerifiableDealingTest {

    private static final int CARD_BYTES = 32;
    private static final int N = 5;

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    /** Builds one MEGAPACKET slot: genesis card `cardIdx` locked by every peer's lock. */
    private static byte[] committedSlot(byte[][] locks, int cardIdx) {
        byte[] genesis = RistrettoSRA.getGenesisDeck();
        byte[] slot = new byte[CARD_BYTES];
        System.arraycopy(genesis, cardIdx * CARD_BYTES, slot, 0, CARD_BYTES);
        for (byte[] k : locks) {
            slot = RistrettoSRA.applyCommutativeLock(slot, k);
        }
        return slot;
    }

    private static byte[][] freshLocks() {
        byte[][] locks = new byte[N][];
        for (int i = 0; i < N; i++) {
            locks[i] = RistrettoSRA.generateLockScalar();
        }
        return locks;
    }

    private static byte[][] commitments(byte[][] locks) {
        byte[][] ks = new byte[N][];
        for (int i = 0; i < N; i++) {
            ks[i] = RistrettoSRA.commitment(locks[i]);
        }
        return ks;
    }

    @Test
    public void honestChainVerifiesAndRevealsCard() {
        byte[][] locks = freshLocks();
        byte[][] ks = commitments(locks);
        int cardIdx = 17;
        byte[] start = committedSlot(locks, cardIdx);

        byte[][] residuals = new byte[N + 1][];
        byte[][] proofs = new byte[N][];
        residuals[0] = start;
        for (int m = 0; m < N; m++) {
            VerifiableUnlock.Step step = VerifiableUnlock.unlockWithProof(residuals[m], locks[m]);
            assertNotNull(step, "honest unlock must succeed");
            residuals[m + 1] = step.residual;
            proofs[m] = step.proof;
        }
        // Fully unlocked -> resolves to the original card.
        assertEquals(cardIdx, RistrettoSRA.resolveCardIndex(residuals[N]),
                "fully unlocked chain must reveal the card");
        // The whole chain verifies from the committed start.
        assertTrue(VerifiableUnlock.verifyChain(start, residuals, ks, proofs),
                "an honest chain from the committed slot must verify");
    }

    @Test
    public void blindedStartIsRejected() {
        // THE FIX. Models the PoC: a malicious host blinds the committed slot by r and
        // tries to use a peer as an oracle. Each peer's unlock is still honestly proven,
        // but the chain does NOT start at the committed bytes, so verifyChain rejects it
        // -> a peer running verifyChain on the incoming chain refuses to be an oracle.
        byte[][] locks = freshLocks();
        byte[][] ks = commitments(locks);
        int cardIdx = 23;
        byte[] committed = committedSlot(locks, cardIdx);

        byte[] r = RistrettoSRA.generateLockScalar();
        byte[] blindedStart = RistrettoSRA.applyCommutativeLock(committed, r); // r * committed

        byte[][] residuals = new byte[N + 1][];
        byte[][] proofs = new byte[N][];
        residuals[0] = blindedStart;
        for (int m = 0; m < N; m++) {
            VerifiableUnlock.Step step = VerifiableUnlock.unlockWithProof(residuals[m], locks[m]);
            residuals[m + 1] = step.residual;
            proofs[m] = step.proof; // each step is individually valid...
        }
        // ...yet the chain is rejected because it does not start at the committed slot.
        assertFalse(VerifiableUnlock.verifyChain(committed, residuals, ks, proofs),
                "a blinded start must be rejected by the chain binding");

        // The host cannot instead present the real committed start and still reach the
        // blinded residuals: residuals[0] would mismatch its own chain. Confirm that
        // swapping in the real start (while keeping blinded proofs) also fails.
        residuals[0] = committed;
        assertFalse(VerifiableUnlock.verifyChain(committed, residuals, ks, proofs),
                "real start with blinded-derived proofs must not verify");
    }

    @Test
    public void tamperedProofIsRejected() {
        byte[][] locks = freshLocks();
        byte[][] ks = commitments(locks);
        byte[] start = committedSlot(locks, 5);
        byte[][] residuals = new byte[N + 1][];
        byte[][] proofs = new byte[N][];
        residuals[0] = start;
        for (int m = 0; m < N; m++) {
            VerifiableUnlock.Step step = VerifiableUnlock.unlockWithProof(residuals[m], locks[m]);
            residuals[m + 1] = step.residual;
            proofs[m] = step.proof;
        }
        assertTrue(VerifiableUnlock.verifyChain(start, residuals, ks, proofs));
        // Flip a bit in one proof.
        proofs[2] = proofs[2].clone();
        proofs[2][0] ^= 0x01;
        assertFalse(VerifiableUnlock.verifyChain(start, residuals, ks, proofs),
                "a tampered proof must break chain verification");
    }

    @Test
    public void uncommittedKeyIsRejected() {
        // A peer that unlocks with a key different from its committed K cannot pass.
        byte[][] locks = freshLocks();
        byte[][] ks = commitments(locks);
        byte[] start = committedSlot(locks, 9);
        byte[][] residuals = new byte[N + 1][];
        byte[][] proofs = new byte[N][];
        residuals[0] = start;
        for (int m = 0; m < N; m++) {
            VerifiableUnlock.Step step = VerifiableUnlock.unlockWithProof(residuals[m], locks[m]);
            residuals[m + 1] = step.residual;
            proofs[m] = step.proof;
        }
        // Replace one peer's committed key with an unrelated one.
        ks[3] = RistrettoSRA.commitment(RistrettoSRA.generateLockScalar());
        assertFalse(VerifiableUnlock.verifyChain(start, residuals, ks, proofs),
                "a step must not verify against an uncommitted key");
    }

    @Test
    public void globalCollisionCheckCatchesDuplicateCard() {
        // The Phase 4b deck-integrity gate: resolved indices accumulate in one Set;
        // a duplicate (e.g. a peer that blindly duplicated a point in its shuffle)
        // is caught before settlement.
        byte[][] locks = freshLocks();
        Set<Integer> dealt = new HashSet<>();
        boolean collision = false;
        // Deal two slots that (by construction) reveal the SAME card to simulate a dup.
        for (int cardIdx : new int[]{14, 14, 30}) {
            byte[] slot = committedSlot(locks, cardIdx);
            byte[] residual = slot;
            for (byte[] k : locks) {
                residual = RistrettoSRA.applyCommutativeLock(residual, RistrettoSRA.getUnlockScalar(k));
            }
            int idx = RistrettoSRA.resolveCardIndex(residual);
            assertTrue(idx >= 0, "must resolve");
            if (!dealt.add(idx)) {
                collision = true;
            }
        }
        assertTrue(collision, "the global index Set must catch a duplicated card");
        assertEquals(2, dealt.size(), "only two distinct cards among {14,14,30}");
    }
}
