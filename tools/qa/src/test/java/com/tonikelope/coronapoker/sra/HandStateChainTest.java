/*
 * EC-Identity v1 (commit 4): unit tests for the hand-state chain ratchet.
 *
 *   - H_0 from known inputs (regression vector).
 *   - Player-id sorting is order-independent (every peer derives the same H_0
 *     regardless of join order).
 *   - absorb() requires the record's PREV_H to match the chain's current H_t.
 *   - The chain remains deterministic across multiple absorbs.
 *   - Argument validation rejects malformed inputs loudly.
 */
package com.tonikelope.coronapoker.sra;

import com.tonikelope.coronapoker.CanonicalActionRecord;
import com.tonikelope.coronapoker.HandStateChain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HandStateChainTest {

    private static byte[] handId(int seed) {
        byte[] out = new byte[16];
        for (int i = 0; i < 16; i++) out[i] = (byte) (seed + i);
        return out;
    }

    private static byte[] deck(int seed, int len) {
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) out[i] = (byte) (seed + i);
        return out;
    }

    private static List<byte[]> nicksToIds(String... nicks) {
        List<byte[]> out = new ArrayList<>();
        for (String n : nicks) {
            out.add(CanonicalActionRecord.playerIdFromNick(n));
        }
        return out;
    }

    @Test
    public void initialHashIs32Bytes() {
        HandStateChain c = HandStateChain.start(handId(0x10),
                nicksToIds("alice", "bob", "charlie"),
                deck(0x01, 1664));
        assertEquals(32, c.getCurrentHash().length);
        assertEquals(0, c.getAbsorbedActions());
    }

    @Test
    public void playerOrderDoesNotAffectInitialHash() {
        byte[] hid = handId(0x42);
        byte[] dck = deck(0xAA, 256);
        HandStateChain a = HandStateChain.start(hid, nicksToIds("alice", "bob", "charlie"), dck);
        HandStateChain b = HandStateChain.start(hid, nicksToIds("charlie", "alice", "bob"), dck);
        HandStateChain c = HandStateChain.start(hid, nicksToIds("bob", "charlie", "alice"), dck);
        assertArrayEquals(a.getCurrentHash(), b.getCurrentHash());
        assertArrayEquals(b.getCurrentHash(), c.getCurrentHash());
    }

    @Test
    public void differentDecksProduceDifferentInitialHashes() {
        byte[] hid = handId(0x42);
        HandStateChain a = HandStateChain.start(hid, nicksToIds("alice", "bob"), deck(0x01, 1664));
        HandStateChain b = HandStateChain.start(hid, nicksToIds("alice", "bob"), deck(0x02, 1664));
        assertNotEquals(Arrays.toString(a.getCurrentHash()), Arrays.toString(b.getCurrentHash()));
    }

    // ---- HAND_V2 (verifiable dealing: per-peer K_pocket / K_community commitments) ----

    private static byte[] commit(int seed) {
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) out[i] = (byte) (seed * 7 + i);
        return out;
    }

    private static List<byte[]> commits(int base, int n) {
        List<byte[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(commit(base + i));
        return out;
    }

    @Test
    public void startV2IsDeterministicAnd32Bytes() {
        byte[] hid = handId(0x10);
        byte[] dck = deck(0x01, 1664);
        List<byte[]> ids = nicksToIds("alice", "bob", "charlie");
        HandStateChain a = HandStateChain.startV2(hid, ids, commits(1, 3), commits(100, 3), dck);
        HandStateChain b = HandStateChain.startV2(hid, ids, commits(1, 3), commits(100, 3), dck);
        assertEquals(32, a.getCurrentHash().length);
        assertArrayEquals(a.getCurrentHash(), b.getCurrentHash());
    }

    @Test
    public void startV2PeerOrderDoesNotAffectInitialHash() {
        byte[] hid = handId(0x42);
        byte[] dck = deck(0xAA, 256);
        byte[] aId = CanonicalActionRecord.playerIdFromNick("alice");
        byte[] bId = CanonicalActionRecord.playerIdFromNick("bob");
        byte[] cId = CanonicalActionRecord.playerIdFromNick("charlie");
        byte[] aKp = commit(1), aKc = commit(100);
        byte[] bKp = commit(2), bKc = commit(101);
        byte[] cKp = commit(3), cKc = commit(102);

        HandStateChain x = HandStateChain.startV2(hid,
                Arrays.asList(aId, bId, cId), Arrays.asList(aKp, bKp, cKp), Arrays.asList(aKc, bKc, cKc), dck);
        // Same peers, different input order, association preserved -> same H_0.
        HandStateChain y = HandStateChain.startV2(hid,
                Arrays.asList(cId, aId, bId), Arrays.asList(cKp, aKp, bKp), Arrays.asList(cKc, aKc, bKc), dck);
        assertArrayEquals(x.getCurrentHash(), y.getCurrentHash());
    }

    @Test
    public void startV2IsSensitiveToCommitments() {
        byte[] hid = handId(0x10);
        byte[] dck = deck(0x01, 1664);
        List<byte[]> ids = nicksToIds("alice", "bob", "charlie");
        HandStateChain base = HandStateChain.startV2(hid, ids, commits(1, 3), commits(100, 3), dck);
        List<byte[]> kp2 = commits(1, 3);
        kp2.set(1, commit(999));
        HandStateChain changed = HandStateChain.startV2(hid, ids, kp2, commits(100, 3), dck);
        assertNotEquals(Arrays.toString(base.getCurrentHash()), Arrays.toString(changed.getCurrentHash()));
    }

    @Test
    public void startV2DiffersFromV1() {
        byte[] hid = handId(0x10);
        byte[] dck = deck(0x01, 1664);
        List<byte[]> ids = nicksToIds("alice", "bob", "charlie");
        HandStateChain v1 = HandStateChain.start(hid, ids, dck);
        HandStateChain v2 = HandStateChain.startV2(hid, ids, commits(1, 3), commits(100, 3), dck);
        assertNotEquals(Arrays.toString(v1.getCurrentHash()), Arrays.toString(v2.getCurrentHash()),
                "HAND_V2 must not collide with HAND_V1 (distinct domain + extra commitments)");
    }

    @Test
    public void startV2RejectsMismatchedListSizes() {
        byte[] hid = handId(0x10);
        byte[] dck = deck(0x01, 1664);
        List<byte[]> ids = nicksToIds("alice", "bob", "charlie");
        assertThrows(IllegalArgumentException.class,
                () -> HandStateChain.startV2(hid, ids, commits(1, 2), commits(100, 3), dck));
        assertThrows(IllegalArgumentException.class,
                () -> HandStateChain.startV2(hid, ids, commits(1, 3), commits(100, 2), dck));
    }

    @Test
    public void differentHandIdsProduceDifferentInitialHashes() {
        HandStateChain a = HandStateChain.start(handId(0x10),
                nicksToIds("alice", "bob"), deck(0x01, 256));
        HandStateChain b = HandStateChain.start(handId(0x11),
                nicksToIds("alice", "bob"), deck(0x01, 256));
        assertNotEquals(Arrays.toString(a.getCurrentHash()), Arrays.toString(b.getCurrentHash()));
    }

    @Test
    public void absorbAdvancesTheChain() {
        HandStateChain chain = HandStateChain.start(handId(0x10),
                nicksToIds("alice", "bob"), deck(0x01, 256));

        byte[] h0 = chain.getCurrentHash();
        byte[] pid = CanonicalActionRecord.playerIdFromNick("alice");

        byte[] r1 = CanonicalActionRecord.encode(h0, chain.getHandId(), pid,
                CanonicalActionRecord.STREET_PREFLOP,
                CanonicalActionRecord.ACTION_CHECK,
                0L, false, true);
        byte[] h1 = chain.absorb(r1);

        assertEquals(1, chain.getAbsorbedActions());
        assertNotEquals(Arrays.toString(h0), Arrays.toString(h1));
        assertArrayEquals(h1, chain.getCurrentHash());

        byte[] r2 = CanonicalActionRecord.encode(h1, chain.getHandId(), pid,
                CanonicalActionRecord.STREET_PREFLOP,
                CanonicalActionRecord.ACTION_BET,
                200L, false, true);
        byte[] h2 = chain.absorb(r2);

        assertEquals(2, chain.getAbsorbedActions());
        assertNotEquals(Arrays.toString(h1), Arrays.toString(h2));
    }

    @Test
    public void absorbRejectsRecordWithMismatchedPrevH() {
        HandStateChain chain = HandStateChain.start(handId(0x10),
                nicksToIds("alice", "bob"), deck(0x01, 256));
        byte[] wrongPrev = new byte[32]; // zeros, not the chain's H_0
        byte[] pid = CanonicalActionRecord.playerIdFromNick("alice");
        byte[] record = CanonicalActionRecord.encode(wrongPrev, chain.getHandId(), pid,
                CanonicalActionRecord.STREET_PREFLOP,
                CanonicalActionRecord.ACTION_CHECK,
                0L, false, true);
        assertThrows(IllegalStateException.class, () -> chain.absorb(record));
    }

    @Test
    public void twoIndependentChainsMatchIfActionsMatch() {
        // Simulates the same hand replayed identically on two peers — H_final
        // must come out byte-identical.
        byte[] hid = handId(0x55);
        byte[] dck = deck(0xCC, 1664);
        List<byte[]> ring = nicksToIds("alice", "bob", "charlie");

        HandStateChain peerA = HandStateChain.start(hid, ring, dck);
        HandStateChain peerB = HandStateChain.start(hid, ring, dck);

        // Three actions, same on both sides.
        Object[][] actions = {
            {"alice", CanonicalActionRecord.ACTION_CHECK, 0L},
            {"bob",   CanonicalActionRecord.ACTION_BET,   200L},
            {"charlie", CanonicalActionRecord.ACTION_FOLD, 0L},
        };
        for (Object[] act : actions) {
            byte[] pid = CanonicalActionRecord.playerIdFromNick((String) act[0]);
            byte[] rA = CanonicalActionRecord.encode(peerA.getCurrentHash(), peerA.getHandId(), pid,
                    CanonicalActionRecord.STREET_PREFLOP, (int) act[1],
                    (long) act[2], false, true);
            byte[] rB = CanonicalActionRecord.encode(peerB.getCurrentHash(), peerB.getHandId(), pid,
                    CanonicalActionRecord.STREET_PREFLOP, (int) act[1],
                    (long) act[2], false, true);
            assertArrayEquals(rA, rB, "records must encode identically");
            peerA.absorb(rA);
            peerB.absorb(rB);
        }
        assertArrayEquals(peerA.getCurrentHash(), peerB.getCurrentHash());
    }

    @Test
    public void divergentActionsProduceDivergentChains() {
        byte[] hid = handId(0x77);
        byte[] dck = deck(0x42, 256);
        List<byte[]> ring = nicksToIds("alice", "bob");

        HandStateChain peerA = HandStateChain.start(hid, ring, dck);
        HandStateChain peerB = HandStateChain.start(hid, ring, dck);

        byte[] pidAlice = CanonicalActionRecord.playerIdFromNick("alice");

        byte[] rA = CanonicalActionRecord.encode(peerA.getCurrentHash(), peerA.getHandId(),
                pidAlice, CanonicalActionRecord.STREET_PREFLOP,
                CanonicalActionRecord.ACTION_BET, 100L, false, true);
        // Peer B sees a different amount for the same alleged action.
        byte[] rB = CanonicalActionRecord.encode(peerB.getCurrentHash(), peerB.getHandId(),
                pidAlice, CanonicalActionRecord.STREET_PREFLOP,
                CanonicalActionRecord.ACTION_BET, 200L, false, true);

        peerA.absorb(rA);
        peerB.absorb(rB);

        assertNotEquals(Arrays.toString(peerA.getCurrentHash()),
                Arrays.toString(peerB.getCurrentHash()));
    }

    @Test
    public void startRejectsMalformedInputs() {
        byte[] dck = deck(0x01, 32);
        List<byte[]> ring = nicksToIds("alice");

        assertThrows(IllegalArgumentException.class,
                () -> HandStateChain.start(null, ring, dck));
        assertThrows(IllegalArgumentException.class,
                () -> HandStateChain.start(new byte[15], ring, dck));
        assertThrows(IllegalArgumentException.class,
                () -> HandStateChain.start(handId(0), Collections.emptyList(), dck));
        assertThrows(IllegalArgumentException.class,
                () -> HandStateChain.start(handId(0), ring, new byte[0]));

        List<byte[]> badRing = new ArrayList<>();
        badRing.add(new byte[31]);
        assertThrows(IllegalArgumentException.class,
                () -> HandStateChain.start(handId(0), badRing, dck));
    }
}
