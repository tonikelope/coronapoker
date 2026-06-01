/*
 * Phase 4.3 — verifiable COMMUNITY dealing over the chain (design validation).
 *
 * Community dealing is isomorphic to the pocket dealing of 4.2 EXCEPT that every
 * recipient's copy anchors to the SAME megapacket community point[j] (the board is
 * shared), differing only in WHICH community-locks have been stripped. This test
 * drives that flow at the crypto layer to prove, before any production rewrite,
 * that:
 *   (1) the chain reveals the correct board card for a recipient (functionality),
 *   (2) a blinded point is refused at the helper (closes attack 2 — community
 *       cards before time), and
 *   (3) GATE 6 is the right guard: if a helper is tricked into stripping its OWN
 *       community-lock off the "all-but-me" copy, the tail resolves to genesis —
 *       which, with the binding making blinding impossible, can no longer be
 *       evaded, so a genesis tail is a reliable extraction signal.
 *
 * No production code is touched; this pins the math the 4.3 caller will implement.
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommunityChainDealingTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    // Strip every community-lock except the recipient's, over the chain, anchored to
    // the shared megapacket point. Returns the tail (single-locked by the recipient).
    private byte[] dealToRecipientOverChain(byte[] megapacketPoint, String recipient,
                                            String[] ring, Map<String, byte[]> communityLocks,
                                            Map<String, byte[]> commitments) {
        String wire = ""; // empty chain = anchored at the committed megapacket point
        for (String peer : ring) {
            if (peer.equals(recipient)) {
                continue; // recipient keeps its own lock (opens locally)
            }
            DealChain.Extended ext = DealChain.extend(megapacketPoint, wire, commitments, peer, communityLocks.get(peer));
            assertNotNull(ext, "honest peer " + peer + " strips its community lock");
            wire = ext.wire;
        }
        List<DealChain.Entry> chain = DealChain.parse(wire);
        assertNotNull(chain);
        assertTrue(DealChain.verify(megapacketPoint, chain, commitments), "final community chain must verify");
        return DealChain.tail(megapacketPoint, chain);
    }

    @Test
    public void communityChainRevealsBoardRejectsBlindingAndGate6CatchesSelfStrip() {
        String[] ring = {"A", "B", "C"}; // A=host, B/C helpers
        Map<String, byte[]> communityLocks = new HashMap<>();
        Map<String, byte[]> commitments = new HashMap<>();
        for (String p : ring) {
            byte[] lock = RistrettoSRA.generateLockScalar();
            communityLocks.put(p, lock);
            commitments.put(p, RistrettoSRA.commitment(lock));
        }

        // FLOP = 3 shared board cards, each locked by ALL community locks (post-rotation).
        byte[] genesis = RistrettoSRA.getGenesisDeck();
        int[] board = {4, 19, 50};
        byte[][] point = new byte[3][];
        for (int j = 0; j < 3; j++) {
            byte[] p = new byte[32];
            System.arraycopy(genesis, board[j] * 32, p, 0, 32);
            for (String peer : ring) {
                p = RistrettoSRA.applyCommutativeLock(p, communityLocks.get(peer));
            }
            point[j] = p;
        }

        // (1) Functionality: every recipient resolves the SAME board over the chain.
        for (String recipient : ring) {
            for (int j = 0; j < 3; j++) {
                byte[] tail = dealToRecipientOverChain(point[j], recipient, ring, communityLocks, commitments);
                byte[] opened = RistrettoSRA.applyCommutativeLock(tail,
                        RistrettoSRA.getUnlockScalar(communityLocks.get(recipient)));
                assertEquals(board[j], RistrettoSRA.resolveCardIndex(opened),
                        "recipient " + recipient + " opens board card " + j);
            }
        }

        // (2) Attack 2 closed: the host extends first (its unlock + bots), so the helper
        // always receives a NON-empty chain anchored to the real megapacket point. If the
        // host tries to make B anchor that chain to a blinded point, verify fails — exactly
        // as the pocket flow (UnlockChainWireTest). In the real handler B additionally never
        // takes a point from the host: it reads megapacket[offsetBase] locally.
        String aChainFromReal = DealChain.extend(point[0], "", commitments, "A", communityLocks.get("A")).wire;
        byte[] r = RistrettoSRA.generateLockScalar();
        byte[] blinded = RistrettoSRA.applyCommutativeLock(point[0], r);
        assertNull(DealChain.extend(blinded, aChainFromReal, commitments, "B", communityLocks.get("B")),
                "helper refuses: A's committed chain does not anchor to the blinded point");

        // (3) GATE 6 rationale: build the "all-but-B" copy (single-locked by B), then have
        // B strip its OWN lock. The tail resolves to genesis — exactly what GATE 6 flags.
        byte[] allButB = dealToRecipientOverChain(point[0], "B", ring, communityLocks, commitments);
        DealChain.Extended bStripsSelf = DealChain.extend(point[0],
                buildWireFor(point[0], "B", ring, communityLocks, commitments),
                commitments, "B", communityLocks.get("B"));
        assertNotNull(bStripsSelf, "the anchor is valid — only GATE 6 distinguishes this");
        assertEquals(board[0], RistrettoSRA.resolveCardIndex(bStripsSelf.residual),
                "stripping B's own community lock yields genesis → GATE 6 must refuse this in the handler");
        // sanity: the legitimate single-locked copy does NOT resolve to a card
        assertTrue(RistrettoSRA.resolveCardIndex(allButB) < 0,
                "the single-locked board copy must stay opaque until B opens it locally");
    }

    // Rebuild the "all peers except recipient" chain wire (helper input the host would send).
    private String buildWireFor(byte[] megapacketPoint, String recipient, String[] ring,
                                Map<String, byte[]> communityLocks, Map<String, byte[]> commitments) {
        String wire = "";
        List<String> order = new ArrayList<>();
        for (String peer : ring) {
            if (!peer.equals(recipient)) {
                order.add(peer);
            }
        }
        for (String peer : order) {
            DealChain.Extended ext = DealChain.extend(megapacketPoint, wire, commitments, peer, communityLocks.get(peer));
            wire = ext.wire;
        }
        return wire;
    }
}
