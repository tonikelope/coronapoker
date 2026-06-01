/*
 * Phase 4.2 — per-point de-locking chain (serialization + verification) tests.
 *
 * Builds a real chain (several peers strip their lock in order, each attaching a
 * DLEQ proof), then checks: honest chain verifies and its tail reveals the card;
 * a blinded start, an uncommitted/altered key, and a tampered proof all fail;
 * serialize/parse round-trips and malformed wire is rejected. This is the wire
 * layer the unlock-batch cabling (host orchestration + peer handler) builds on.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DealChainTest {

    private static final int N = 5;

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    private static class Setup {
        byte[][] locks = new byte[N][];
        Map<String, byte[]> commitments = new HashMap<>();
        byte[] megapacketPoint; // genesis card locked by every peer
        int cardIdx = 21;

        Setup() {
            byte[] genesis = RistrettoSRA.getGenesisDeck();
            byte[] point = new byte[32];
            System.arraycopy(genesis, cardIdx * 32, point, 0, 32);
            for (int i = 0; i < N; i++) {
                locks[i] = RistrettoSRA.generateLockScalar();
                commitments.put("p" + i, RistrettoSRA.commitment(locks[i]));
                point = RistrettoSRA.applyCommutativeLock(point, locks[i]);
            }
            megapacketPoint = point;
        }

        // Honest chain: peers 0..N-1 strip their lock in order, each proving it.
        List<DealChain.Entry> honestChain() {
            List<DealChain.Entry> entries = new ArrayList<>();
            byte[] residual = megapacketPoint;
            for (int i = 0; i < N; i++) {
                VerifiableUnlock.Step step = VerifiableUnlock.unlockWithProof(residual, locks[i]);
                entries.add(new DealChain.Entry("p" + i, step.residual, step.proof));
                residual = step.residual;
            }
            return entries;
        }
    }

    @Test
    public void honestChainVerifiesAndTailRevealsCard() {
        Setup s = new Setup();
        List<DealChain.Entry> chain = s.honestChain();
        assertTrue(DealChain.verify(s.megapacketPoint, chain, s.commitments),
                "honest chain must verify");
        byte[] tail = DealChain.tail(s.megapacketPoint, chain);
        assertEquals(s.cardIdx, RistrettoSRA.resolveCardIndex(tail),
                "fully de-locked tail must reveal the card");
    }

    @Test
    public void serializeParseRoundTrips() {
        Setup s = new Setup();
        List<DealChain.Entry> chain = s.honestChain();
        String wire = DealChain.serialize(chain);
        List<DealChain.Entry> parsed = DealChain.parse(wire);
        assertTrue(parsed != null && DealChain.verify(s.megapacketPoint, parsed, s.commitments),
                "parsed chain must verify identically");
        // Wire must not contain the '#' used by the surrounding command split.
        assertFalse(wire.contains("#"), "chain wire must be '#'-safe");
    }

    @Test
    public void blindedStartIsRejected() {
        Setup s = new Setup();
        List<DealChain.Entry> chain = s.honestChain();
        byte[] r = RistrettoSRA.generateLockScalar();
        byte[] blinded = RistrettoSRA.applyCommutativeLock(s.megapacketPoint, r);
        assertFalse(DealChain.verify(blinded, chain, s.commitments),
                "a chain must not verify against a blinded start point");
    }

    @Test
    public void uncommittedOrAlteredKeyIsRejected() {
        Setup s = new Setup();
        List<DealChain.Entry> chain = s.honestChain();
        // Unknown peer key.
        Map<String, byte[]> missing = new HashMap<>(s.commitments);
        missing.remove("p2");
        assertFalse(DealChain.verify(s.megapacketPoint, chain, missing),
                "chain must not verify when a step's committed key is missing");
        // Altered key.
        Map<String, byte[]> altered = new HashMap<>(s.commitments);
        altered.put("p2", RistrettoSRA.commitment(RistrettoSRA.generateLockScalar()));
        assertFalse(DealChain.verify(s.megapacketPoint, chain, altered),
                "chain must not verify against an altered committed key");
    }

    @Test
    public void tamperedProofIsRejected() {
        Setup s = new Setup();
        List<DealChain.Entry> chain = s.honestChain();
        chain.get(2).proof[0] ^= 0x01;
        assertFalse(DealChain.verify(s.megapacketPoint, chain, s.commitments),
                "a tampered proof must break verification");
    }

    @Test
    public void malformedWireParsesToNull() {
        assertNull(DealChain.parse("not-three-fields"), "wrong field count must parse to null");
        assertNull(DealChain.parse("YQ==:YQ==:YQ=="), "out-of-spec lengths must parse to null");
        assertTrue(DealChain.parse("").isEmpty(), "empty wire is an empty (valid) chain");
    }
}
