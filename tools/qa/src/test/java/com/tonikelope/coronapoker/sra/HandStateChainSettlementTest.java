/*
 * Unit tests for the terminal settlement absorb on the hand-state chain.
 *
 *   - absorbSettlement advances H_t to H_final and is terminal (once per hand).
 *   - H_final binds the prior action history: same table on a different chain
 *     state yields a different H_final.
 *   - Two peers that replayed the same actions AND computed the same settlement
 *     table converge on the same H_final.
 *   - A divergent settlement table produces a divergent H_final (the property
 *     the consensus check relies on to catch payout disagreements).
 */
package com.tonikelope.coronapoker.sra;

import com.tonikelope.coronapoker.CanonicalActionRecord;
import com.tonikelope.coronapoker.HandStateChain;
import com.tonikelope.coronapoker.SettlementRecord;
import com.tonikelope.coronapoker.SettlementRecord.Entry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HandStateChainSettlementTest {

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

    private static HandStateChain startFor(byte[] hid, byte[] dck, String... nicks) {
        List<byte[]> ids = new ArrayList<>();
        for (String n : nicks) ids.add(CanonicalActionRecord.playerIdFromNick(n));
        return HandStateChain.start(hid, ids, commits(1, ids.size()), commits(100, ids.size()), dck);
    }

    private static Entry entry(String nick, long bote, long pagar) {
        return new Entry(CanonicalActionRecord.playerIdFromNick(nick), bote, pagar);
    }

    private static void absorbOneAction(HandStateChain chain, String nick, int action, long cents) {
        byte[] pid = CanonicalActionRecord.playerIdFromNick(nick);
        byte[] record = CanonicalActionRecord.encode(chain.getCurrentHash(), chain.getHandId(), pid,
                CanonicalActionRecord.STREET_PREFLOP, action, cents, false, true);
        chain.absorb(record);
    }

    @Test
    public void absorbSettlementAdvancesAndIsTerminal() {
        HandStateChain chain = startFor(handId(0x10), deck(0x01, 256), "alice", "bob");
        byte[] before = chain.getCurrentHash();

        byte[] table = SettlementRecord.encode(chain.getHandId(),
                Arrays.asList(entry("alice", 100L, 0L), entry("bob", 100L, 200L)), 0L);
        byte[] hFinal = chain.absorbSettlement(table);

        assertTrue(chain.isSettlementAbsorbed());
        assertEquals32(hFinal);
        assertNotEquals(Arrays.toString(before), Arrays.toString(hFinal));
        assertArrayEquals(hFinal, chain.getCurrentHash());

        // Terminal: a hand settles exactly once.
        assertThrows(IllegalStateException.class, () -> chain.absorbSettlement(table));
    }

    @Test
    public void rejectsNullOrEmptyTable() {
        HandStateChain chain = startFor(handId(0x11), deck(0x01, 256), "alice", "bob");
        assertThrows(IllegalArgumentException.class, () -> chain.absorbSettlement(null));
        assertThrows(IllegalArgumentException.class, () -> chain.absorbSettlement(new byte[0]));
    }

    @Test
    public void hFinalBindsPriorActionHistory() {
        // Same settlement table, but two chains whose action histories differ:
        // binding H_t means H_final must still differ.
        byte[] hid = handId(0x20);
        byte[] dck = deck(0x05, 256);

        HandStateChain a = startFor(hid, dck, "alice", "bob");
        HandStateChain b = startFor(hid, dck, "alice", "bob");
        absorbOneAction(a, "alice", CanonicalActionRecord.ACTION_BET, 100L);
        absorbOneAction(b, "alice", CanonicalActionRecord.ACTION_BET, 200L); // divergent history

        byte[] table = SettlementRecord.encode(hid,
                Arrays.asList(entry("alice", 100L, 0L), entry("bob", 100L, 200L)), 0L);
        byte[] hfA = a.absorbSettlement(table);
        byte[] hfB = b.absorbSettlement(table);

        assertNotEquals(Arrays.toString(hfA), Arrays.toString(hfB));
    }

    @Test
    public void identicalHandsConvergeOnHFinal() {
        byte[] hid = handId(0x30);
        byte[] dck = deck(0x07, 256);

        HandStateChain a = startFor(hid, dck, "alice", "bob");
        HandStateChain b = startFor(hid, dck, "alice", "bob");
        absorbOneAction(a, "alice", CanonicalActionRecord.ACTION_BET, 100L);
        absorbOneAction(b, "alice", CanonicalActionRecord.ACTION_BET, 100L);

        byte[] tableA = SettlementRecord.encode(hid,
                Arrays.asList(entry("alice", 100L, 200L), entry("bob", 100L, 0L)), 0L);
        byte[] tableB = SettlementRecord.encode(hid,
                Arrays.asList(entry("bob", 100L, 0L), entry("alice", 100L, 200L)), 0L);

        assertArrayEquals(a.absorbSettlement(tableA), b.absorbSettlement(tableB));
    }

    @Test
    public void divergentSettlementProducesDivergentHFinal() {
        // Same action history on both peers, but they disagree on who got paid.
        byte[] hid = handId(0x40);
        byte[] dck = deck(0x09, 256);

        HandStateChain a = startFor(hid, dck, "alice", "bob");
        HandStateChain b = startFor(hid, dck, "alice", "bob");
        absorbOneAction(a, "alice", CanonicalActionRecord.ACTION_CHECK, 0L);
        absorbOneAction(b, "alice", CanonicalActionRecord.ACTION_CHECK, 0L);

        byte[] tableA = SettlementRecord.encode(hid,
                Arrays.asList(entry("alice", 100L, 200L), entry("bob", 100L, 0L)), 0L);
        byte[] tableB = SettlementRecord.encode(hid,
                Arrays.asList(entry("alice", 100L, 0L), entry("bob", 100L, 200L)), 0L);

        assertNotEquals(
                Arrays.toString(a.absorbSettlement(tableA)),
                Arrays.toString(b.absorbSettlement(tableB)));
    }

    private static void assertEquals32(byte[] h) {
        assertTrue(h != null && h.length == 32, "H_final must be 32 bytes");
    }
}
