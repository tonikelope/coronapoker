/*
 * Unit tests for the canonical end-of-hand settlement table (SettlementRecord).
 *
 *   - Encoding is deterministic and reproducible across peers.
 *   - Entry input order is irrelevant (sorted by player_id internally).
 *   - The bytes are sensitive to every amount, the hand id and the remainder.
 *   - One-cent differences are detectable (no float collapse).
 *   - Duplicate participants and malformed inputs are rejected loudly.
 *   - The chip-conservation invariant holds exactly.
 */
package com.tonikelope.coronapoker.sra;

import com.tonikelope.coronapoker.CanonicalActionRecord;
import com.tonikelope.coronapoker.SettlementRecord;
import com.tonikelope.coronapoker.SettlementRecord.Entry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SettlementRecordTest {

    private static byte[] handId(int seed) {
        byte[] out = new byte[16];
        for (int i = 0; i < 16; i++) out[i] = (byte) (seed + i);
        return out;
    }

    private static Entry entry(String nick, long bote, long pagar) {
        return new Entry(CanonicalActionRecord.playerIdFromNick(nick), bote, pagar);
    }

    @Test
    public void encodeIsDeterministic() {
        byte[] hid = handId(0x10);
        List<Entry> e1 = Arrays.asList(entry("alice", 100L, 0L), entry("bob", 100L, 200L));
        List<Entry> e2 = Arrays.asList(entry("alice", 100L, 0L), entry("bob", 100L, 200L));
        assertArrayEquals(SettlementRecord.encode(hid, e1, 0L), SettlementRecord.encode(hid, e2, 0L));
    }

    @Test
    public void entryOrderDoesNotAffectBytes() {
        byte[] hid = handId(0x22);
        List<Entry> forward = Arrays.asList(
                entry("alice", 100L, 0L), entry("bob", 100L, 150L), entry("charlie", 50L, 100L));
        List<Entry> shuffled = Arrays.asList(
                entry("charlie", 50L, 100L), entry("alice", 100L, 0L), entry("bob", 100L, 150L));
        assertArrayEquals(
                SettlementRecord.encode(hid, forward, 0L),
                SettlementRecord.encode(hid, shuffled, 0L));
    }

    @Test
    public void oneCentDifferenceChangesBytes() {
        byte[] hid = handId(0x33);
        List<Entry> base = Arrays.asList(entry("alice", 100L, 0L), entry("bob", 100L, 200L));
        List<Entry> off = Arrays.asList(entry("alice", 100L, 0L), entry("bob", 100L, 201L));
        assertNotEquals(
                Arrays.toString(SettlementRecord.encode(hid, base, 0L)),
                Arrays.toString(SettlementRecord.encode(hid, off, 0L)));
    }

    @Test
    public void differentHandIdChangesBytes() {
        List<Entry> e = Arrays.asList(entry("alice", 100L, 0L), entry("bob", 100L, 200L));
        assertNotEquals(
                Arrays.toString(SettlementRecord.encode(handId(0x01), e, 0L)),
                Arrays.toString(SettlementRecord.encode(handId(0x02), e, 0L)));
    }

    @Test
    public void sobranteChangesBytes() {
        byte[] hid = handId(0x44);
        List<Entry> e = Arrays.asList(entry("alice", 100L, 95L), entry("bob", 100L, 100L));
        assertNotEquals(
                Arrays.toString(SettlementRecord.encode(hid, e, 0L)),
                Arrays.toString(SettlementRecord.encode(hid, e, 5L)));
    }

    @Test
    public void encodedLengthMatchesLayout() {
        byte[] hid = handId(0x55);
        List<Entry> e = Arrays.asList(
                entry("alice", 100L, 0L), entry("bob", 100L, 150L), entry("charlie", 50L, 100L));
        byte[] table = SettlementRecord.encode(hid, e, 0L);
        // HAND_ID(16) + N(1) + N*48 + SOBRANTE(8)
        assertEquals(16 + 1 + 3 * SettlementRecord.ENTRY_BYTES + 8, table.length);
        assertEquals(3, SettlementRecord.readParticipantCount(table));
    }

    @Test
    public void rejectsDuplicatePlayerId() {
        byte[] hid = handId(0x66);
        List<Entry> dup = Arrays.asList(entry("alice", 100L, 0L), entry("alice", 50L, 200L));
        assertThrows(IllegalArgumentException.class, () -> SettlementRecord.encode(hid, dup, 0L));
    }

    @Test
    public void rejectsMalformedInputs() {
        List<Entry> ok = Arrays.asList(entry("alice", 100L, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> SettlementRecord.encode(null, ok, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> SettlementRecord.encode(new byte[15], ok, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> SettlementRecord.encode(handId(0), Collections.emptyList(), 0L));
        assertThrows(IllegalArgumentException.class,
                () -> SettlementRecord.encode(handId(0), ok, -1L));
    }

    @Test
    public void entryRejectsMalformedInputs() {
        byte[] goodId = CanonicalActionRecord.playerIdFromNick("alice");
        assertThrows(IllegalArgumentException.class, () -> new Entry(null, 0L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new Entry(new byte[31], 0L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new Entry(goodId, -1L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new Entry(goodId, 0L, -1L));
    }

    @Test
    public void amountsBalanceHoldsForConservedChips() {
        // alice + bob each put 100; bob wins the 200 pot, no remainder.
        List<Entry> e = Arrays.asList(entry("alice", 100L, 0L), entry("bob", 100L, 200L));
        assertTrue(SettlementRecord.amountsBalance(e, 0L));
    }

    @Test
    public void amountsBalanceHoldsWithRemainder() {
        // Three-way split of a 100 pot: 33 + 33 + 33 paid, 1 cent remainder.
        List<Entry> e = Arrays.asList(
                entry("alice", 34L, 33L), entry("bob", 33L, 33L), entry("charlie", 33L, 33L));
        assertTrue(SettlementRecord.amountsBalance(e, 1L));
    }

    @Test
    public void amountsBalanceFailsOnImbalance() {
        // bob is over-paid by 50 with no remainder to explain it -> bug.
        List<Entry> e = Arrays.asList(entry("alice", 100L, 0L), entry("bob", 100L, 250L));
        assertFalse(SettlementRecord.amountsBalance(e, 0L));
    }
}
