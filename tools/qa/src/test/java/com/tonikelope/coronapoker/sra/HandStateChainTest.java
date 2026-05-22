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
