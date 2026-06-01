/*
 * Phase 4.2 — unlock-batch wire (de)serialization + full host<->peer flow test.
 *
 * Round-trips REQ/RESP item lists (multi-item, multi-point, empty chains), and
 * drives the exact flow the cabling will run: the host packs per-slot chains, a
 * peer parses them, verifies + extends each point with its proof, packs the RESP,
 * and the host parses it back. Confirms the de-locked tails reveal the cards and
 * that a blinded chain is refused at the peer. This nails the fragile parsing so
 * only socket I/O + orchestration remain for manual smoke.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnlockChainWireTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    @Test
    public void reqRoundTrip() {
        List<UnlockChainWire.ReqItem> items = new ArrayList<>();
        items.add(new UnlockChainWire.ReqItem(2, 4, Arrays.asList("aGk6YmU6eW8=", "")));
        items.add(new UnlockChainWire.ReqItem(0, 0, Arrays.asList("")));
        String wire = UnlockChainWire.serializeReq(items);
        List<UnlockChainWire.ReqItem> back = UnlockChainWire.parseReq(wire);
        assertNotNull(back);
        assertEquals(2, back.size());
        assertEquals(2, back.get(0).peerIdx);
        assertEquals(4, back.get(0).offsetBase);
        assertEquals(2, back.get(0).chains.size());
        assertEquals("aGk6YmU6eW8=", back.get(0).chains.get(0));
        assertEquals("", back.get(0).chains.get(1));
        assertEquals(0, back.get(1).peerIdx);
        assertEquals(1, back.get(1).chains.size());
    }

    @Test
    public void respRoundTrip() {
        List<UnlockChainWire.RespItem> items = new ArrayList<>();
        items.add(new UnlockChainWire.RespItem(3, Arrays.asList("x", "y")));
        String wire = UnlockChainWire.serializeResp(items);
        List<UnlockChainWire.RespItem> back = UnlockChainWire.parseResp(wire);
        assertNotNull(back);
        assertEquals(1, back.size());
        assertEquals(3, back.get(0).peerIdx);
        assertEquals(Arrays.asList("x", "y"), back.get(0).chains);
    }

    @Test
    public void emptyAndMalformed() {
        assertTrue(UnlockChainWire.parseReq(UnlockChainWire.serializeReq(new ArrayList<>())).isEmpty());
        assertNull(UnlockChainWire.parseReq("@@not-base64@@"));
    }

    @Test
    public void fullHostPeerFlowRevealsCardsAndRejectsBlinding() {
        // Two peers (host A + helper B) lock a 2-point slot. The host packs A's
        // chains, B extends them over the wire, host collects, tails reveal cards.
        byte[] lockA = RistrettoSRA.generateLockScalar();
        byte[] lockB = RistrettoSRA.generateLockScalar();
        Map<String, byte[]> commitments = new HashMap<>();
        commitments.put("A", RistrettoSRA.commitment(lockA));
        commitments.put("B", RistrettoSRA.commitment(lockB));

        byte[] genesis = RistrettoSRA.getGenesisDeck();
        int[] cardIdx = {7, 33};
        byte[][] point = new byte[2][];
        for (int j = 0; j < 2; j++) {
            byte[] p = new byte[32];
            System.arraycopy(genesis, cardIdx[j] * 32, p, 0, 32);
            p = RistrettoSRA.applyCommutativeLock(p, lockA);
            p = RistrettoSRA.applyCommutativeLock(p, lockB);
            point[j] = p;
        }

        // Host (A) extends each point's chain from empty.
        List<String> aChains = new ArrayList<>();
        for (int j = 0; j < 2; j++) {
            DealChain.Extended ext = DealChain.extend(point[j], "", commitments, "A", lockA);
            assertNotNull(ext, "host A extend must succeed");
            aChains.add(ext.wire);
        }

        // Pack REQ for helper B (slot 0, points at offset 0..1), send over the wire.
        List<UnlockChainWire.ReqItem> req = new ArrayList<>();
        req.add(new UnlockChainWire.ReqItem(0, 0, aChains));
        List<UnlockChainWire.ReqItem> reqRecv = UnlockChainWire.parseReq(UnlockChainWire.serializeReq(req));
        assertNotNull(reqRecv);

        // Helper B: verify + extend each point's chain against ITS local megapacket point.
        List<UnlockChainWire.RespItem> resp = new ArrayList<>();
        for (UnlockChainWire.ReqItem it : reqRecv) {
            List<String> outChains = new ArrayList<>();
            for (int j = 0; j < it.chains.size(); j++) {
                byte[] localPoint = point[it.offsetBase + j]; // B uses its own committed point
                DealChain.Extended ext = DealChain.extend(localPoint, it.chains.get(j), commitments, "B", lockB);
                assertNotNull(ext, "helper B extend must succeed for an honest chain");
                outChains.add(ext.wire);
            }
            resp.add(new UnlockChainWire.RespItem(it.peerIdx, outChains));
        }

        // Host collects RESP, tails (A+B stripped) reveal the original cards.
        List<UnlockChainWire.RespItem> respRecv = UnlockChainWire.parseResp(UnlockChainWire.serializeResp(resp));
        assertNotNull(respRecv);
        for (int j = 0; j < 2; j++) {
            List<DealChain.Entry> chain = DealChain.parse(respRecv.get(0).chains.get(j));
            assertNotNull(chain);
            assertTrue(DealChain.verify(point[j], chain, commitments), "final chain must verify");
            byte[] tail = DealChain.tail(point[j], chain);
            assertEquals(cardIdx[j], RistrettoSRA.resolveCardIndex(tail), "tail reveals card " + j);
        }

        // Blinding: a host that hands B a chain not anchored to B's local point is refused.
        byte[] r = RistrettoSRA.generateLockScalar();
        byte[] blinded = RistrettoSRA.applyCommutativeLock(point[0], r);
        assertNull(DealChain.extend(blinded, aChains.get(0), commitments, "B", lockB),
                "helper must refuse a chain not anchored to its committed point");
    }
}
