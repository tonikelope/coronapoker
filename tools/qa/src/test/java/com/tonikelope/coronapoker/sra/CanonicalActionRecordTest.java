/*
 * EC-Identity v1 (commit 4): unit tests for the canonical 92-byte action
 * encoder. Cover the invariants documented in docs/ec-identity-spec.md §4:
 *
 *   - Fixed layout, exact byte offsets and widths.
 *   - NFC normalization of nicks before PLAYER_ID derivation.
 *   - Float → integer cents conversion (no IEEE-754 jitter).
 *   - Argument validation (out-of-range street, negative amount, etc).
 *
 * Cross-platform reproducibility is implicit: a Java run on Windows / Linux /
 * macOS must produce byte-identical output for the same inputs, otherwise the
 * NFC + UTF-8 + BigEndian discipline is broken.
 */
package com.tonikelope.coronapoker.sra;

import com.tonikelope.coronapoker.CanonicalActionRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CanonicalActionRecordTest {

    private static byte[] hashOf(String s) throws Exception {
        return MessageDigest.getInstance("SHA-256")
                .digest(Normalizer.normalize(s, Normalizer.Form.NFC).getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hash(byte b) {
        byte[] out = new byte[32];
        Arrays.fill(out, b);
        return out;
    }

    private static byte[] handId(byte b) {
        byte[] out = new byte[16];
        Arrays.fill(out, b);
        return out;
    }

    @Test
    public void recordIsExactly92Bytes() {
        byte[] r = CanonicalActionRecord.encode(
                hash((byte) 0xAA), handId((byte) 0xBB), hash((byte) 0xCC),
                CanonicalActionRecord.STREET_PREFLOP,
                CanonicalActionRecord.ACTION_CHECK,
                0L, false, true);
        assertEquals(92, r.length);
        assertEquals(CanonicalActionRecord.RECORD_BYTES, r.length);
    }

    @Test
    public void fieldsLandAtExpectedOffsets() {
        byte[] prevH = hash((byte) 0x11);
        byte[] hid = handId((byte) 0x22);
        byte[] pid = hash((byte) 0x33);

        byte[] r = CanonicalActionRecord.encode(prevH, hid, pid,
                CanonicalActionRecord.STREET_RIVER,
                CanonicalActionRecord.ACTION_RAISE,
                12345L, true, true);

        assertArrayEquals(prevH, Arrays.copyOfRange(r,
                CanonicalActionRecord.OFFSET_PREV_H,
                CanonicalActionRecord.OFFSET_PREV_H + 32));
        assertArrayEquals(hid, Arrays.copyOfRange(r,
                CanonicalActionRecord.OFFSET_HAND_ID,
                CanonicalActionRecord.OFFSET_HAND_ID + 16));
        assertArrayEquals(pid, Arrays.copyOfRange(r,
                CanonicalActionRecord.OFFSET_PLAYER_ID,
                CanonicalActionRecord.OFFSET_PLAYER_ID + 32));
        assertEquals(CanonicalActionRecord.STREET_RIVER, r[CanonicalActionRecord.OFFSET_STREET] & 0xFF);
        assertEquals(CanonicalActionRecord.ACTION_RAISE, r[CanonicalActionRecord.OFFSET_ACTION_TYPE] & 0xFF);
    }

    @Test
    public void amountCentsIsBigEndianInt64() {
        long value = 0x0123456789ABCDEFL;
        byte[] r = CanonicalActionRecord.encode(hash((byte) 0), handId((byte) 0), hash((byte) 0),
                CanonicalActionRecord.STREET_PREFLOP,
                CanonicalActionRecord.ACTION_BET,
                value, false, true);
        byte[] amount = Arrays.copyOfRange(r,
                CanonicalActionRecord.OFFSET_AMOUNT_CENTS,
                CanonicalActionRecord.OFFSET_AMOUNT_CENTS + 8);
        assertArrayEquals(new byte[]{0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF}, amount);
    }

    @Test
    public void flagsBitsAreSetIndependently() {
        // Only is_voluntary set
        byte[] r1 = CanonicalActionRecord.encode(hash((byte) 0), handId((byte) 0), hash((byte) 0),
                CanonicalActionRecord.STREET_PREFLOP,
                CanonicalActionRecord.ACTION_CHECK,
                0L, false, true);
        // bit1 set, bit0 unset → 0x0002 big-endian → 00 02
        assertEquals(0x00, r1[CanonicalActionRecord.OFFSET_FLAGS] & 0xFF);
        assertEquals(0x02, r1[CanonicalActionRecord.OFFSET_FLAGS + 1] & 0xFF);

        // Both set
        byte[] r2 = CanonicalActionRecord.encode(hash((byte) 0), handId((byte) 0), hash((byte) 0),
                CanonicalActionRecord.STREET_PREFLOP,
                CanonicalActionRecord.ACTION_ALLIN,
                100L, true, true);
        assertEquals(0x00, r2[CanonicalActionRecord.OFFSET_FLAGS] & 0xFF);
        assertEquals(0x03, r2[CanonicalActionRecord.OFFSET_FLAGS + 1] & 0xFF);

        // Host-issued auto-fold: bit1=0
        byte[] r3 = CanonicalActionRecord.encode(hash((byte) 0), handId((byte) 0), hash((byte) 0),
                CanonicalActionRecord.STREET_PREFLOP,
                CanonicalActionRecord.ACTION_FOLD,
                0L, false, false);
        assertEquals(0x00, r3[CanonicalActionRecord.OFFSET_FLAGS] & 0xFF);
        assertEquals(0x00, r3[CanonicalActionRecord.OFFSET_FLAGS + 1] & 0xFF);
    }

    @Test
    public void nfcAndNfdNicksProduceSamePlayerId() throws Exception {
        // "café" in NFC (precomposed é) vs NFD (e + combining acute).
        String nfc = "café";
        String nfd = "café";
        // Sanity check that the two encodings actually differ at the byte level.
        assertNotEquals(
                java.util.Arrays.toString(nfc.getBytes(StandardCharsets.UTF_8)),
                java.util.Arrays.toString(nfd.getBytes(StandardCharsets.UTF_8)));
        assertArrayEquals(
                CanonicalActionRecord.playerIdFromNick(nfc),
                CanonicalActionRecord.playerIdFromNick(nfd));
        assertArrayEquals(hashOf("café"), CanonicalActionRecord.playerIdFromNick(nfc));
    }

    @Test
    public void differentNicksProduceDifferentPlayerIds() {
        byte[] a = CanonicalActionRecord.playerIdFromNick("alice");
        byte[] b = CanonicalActionRecord.playerIdFromNick("bob");
        assertEquals(32, a.length);
        assertEquals(32, b.length);
        assertNotEquals(java.util.Arrays.toString(a), java.util.Arrays.toString(b));
    }

    @Test
    public void amountToCentsRoundsHalfWayBugs() {
        // Classic IEEE-754 jitter: 0.1f + 0.2f != 0.3f at float precision.
        // Widening to double before scaling rescues the rounding.
        assertEquals(10L, CanonicalActionRecord.amountToCents(0.1f));
        assertEquals(20L, CanonicalActionRecord.amountToCents(0.2f));
        assertEquals(30L, CanonicalActionRecord.amountToCents(0.1f + 0.2f));
        assertEquals(0L, CanonicalActionRecord.amountToCents(0f));
        assertEquals(100L, CanonicalActionRecord.amountToCents(1f));
        assertEquals(150L, CanonicalActionRecord.amountToCents(1.5f));
        assertEquals(12345L, CanonicalActionRecord.amountToCents(123.45f));
    }

    @Test
    public void amountToCentsRejectsBadInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalActionRecord.amountToCents(-1f));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalActionRecord.amountToCents(Float.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalActionRecord.amountToCents(Float.POSITIVE_INFINITY));
    }

    @Test
    public void encodeRejectsWrongSizedHashes() {
        byte[] short31 = new byte[31];
        byte[] short15 = new byte[15];
        byte[] ok32 = hash((byte) 0);
        byte[] ok16 = handId((byte) 0);
        assertThrows(IllegalArgumentException.class, () -> CanonicalActionRecord.encode(
                short31, ok16, ok32,
                CanonicalActionRecord.STREET_PREFLOP, CanonicalActionRecord.ACTION_FOLD,
                0L, false, true));
        assertThrows(IllegalArgumentException.class, () -> CanonicalActionRecord.encode(
                ok32, short15, ok32,
                CanonicalActionRecord.STREET_PREFLOP, CanonicalActionRecord.ACTION_FOLD,
                0L, false, true));
        assertThrows(IllegalArgumentException.class, () -> CanonicalActionRecord.encode(
                ok32, ok16, short31,
                CanonicalActionRecord.STREET_PREFLOP, CanonicalActionRecord.ACTION_FOLD,
                0L, false, true));
    }

    @Test
    public void encodeRejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> CanonicalActionRecord.encode(
                hash((byte) 0), handId((byte) 0), hash((byte) 0),
                CanonicalActionRecord.STREET_PREFLOP, CanonicalActionRecord.ACTION_BET,
                -1L, false, true));
    }

    /**
     * Fixed input vector. Hard-codes the expected bytes so a regression that
     * silently changes the layout (e.g. switches to little-endian, reorders
     * fields) fails immediately.
     */
    @Test
    public void knownVector() {
        byte[] prevH = new byte[32];
        byte[] hid = new byte[16];
        byte[] pid = new byte[32];
        for (int i = 0; i < 32; i++) prevH[i] = (byte) (i + 1);
        for (int i = 0; i < 16; i++) hid[i] = (byte) (0x40 + i);
        for (int i = 0; i < 32; i++) pid[i] = (byte) (0x80 + i);

        byte[] r = CanonicalActionRecord.encode(prevH, hid, pid,
                CanonicalActionRecord.STREET_FLOP,
                CanonicalActionRecord.ACTION_BET,
                250L, false, true);

        // Spot-check the most layout-sensitive positions.
        assertEquals(0x01, r[0] & 0xFF);             // PREV_H byte 0
        assertEquals(0x20, r[31] & 0xFF);            // PREV_H byte 31 (decimal 32)
        assertEquals(0x40, r[32] & 0xFF);            // HAND_ID byte 0
        assertEquals(0x4F, r[47] & 0xFF);            // HAND_ID byte 15
        assertEquals(0x80, r[48] & 0xFF);            // PLAYER_ID byte 0
        assertEquals(0x9F, r[79] & 0xFF);            // PLAYER_ID byte 31
        assertEquals(CanonicalActionRecord.STREET_FLOP, r[80] & 0xFF);
        assertEquals(CanonicalActionRecord.ACTION_BET, r[81] & 0xFF);
        // 250 in BE int64 = 00 00 00 00 00 00 00 FA
        assertEquals(0x00, r[82] & 0xFF);
        assertEquals(0xFA, r[89] & 0xFF);
        // FLAGS = bit1 (voluntary) set → 0x0002 BE
        assertEquals(0x00, r[90] & 0xFF);
        assertEquals(0x02, r[91] & 0xFF);
    }

    @Test
    public void encodeIsDeterministic() {
        byte[] prevH = hash((byte) 0x77);
        byte[] hid = handId((byte) 0x33);
        byte[] pid = CanonicalActionRecord.playerIdFromNick("antonio");
        byte[] r1 = CanonicalActionRecord.encode(prevH, hid, pid,
                CanonicalActionRecord.STREET_TURN, CanonicalActionRecord.ACTION_CALL,
                500L, false, true);
        byte[] r2 = CanonicalActionRecord.encode(prevH, hid, pid,
                CanonicalActionRecord.STREET_TURN, CanonicalActionRecord.ACTION_CALL,
                500L, false, true);
        assertArrayEquals(r1, r2);
        assertTrue(r1 != r2, "encode must return a fresh array each call");
    }
}
