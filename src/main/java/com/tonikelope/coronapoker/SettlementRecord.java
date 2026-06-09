/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Canonical serialization of the end-of-hand <em>settlement table</em> — who
 * contributed how much and who was paid how much. This is the {@code H_t}
 * ratchet's terminal record: every peer computes the table independently from
 * the same verified inputs (the revealed showdown cards resolved to genesis and
 * the betting actions already committed in the chain) and absorbs it through
 * {@link HandStateChain#absorbSettlement(byte[])} just before emitting the
 * closing receipt. Two peers that disagree on the money produce a different
 * {@code H_final} and the divergence surfaces in the consensus check.
 *
 * <p>Byte layout (all multi-byte integers big-endian, host-independent):
 *
 * <pre>
 *   Offset  Size            Field             Notes
 *   ------  --------------  ----------------  --------------------------------
 *     0     16              HAND_ID           Random bytes from host at hand start
 *    16      1              N (uint8)         Number of settled participants (1..255)
 *    17      N*48           ENTRIES           Sorted ascending by PLAYER_ID:
 *                                               player_id(32) || bote_cents(i64) || pagar_cents(i64)
 *    17+N*48 8              SOBRANTE_CENTS    Odd-chip remainder not credited to any player
 * </pre>
 *
 * <p>Determinism guarantees, mirroring {@link CanonicalActionRecord}:
 * <ul>
 *   <li>Entries are sorted by {@code player_id} (unsigned), so map iteration
 *       order or join order never changes the bytes.</li>
 *   <li>Chip amounts arrive already converted to integer cents via
 *       {@link CanonicalActionRecord#amountToCents(float)} (double-widened, kills
 *       IEEE-754 float jitter); this class only accepts the integer values.</li>
 *   <li>Big-endian encoding is independent of host byte order.</li>
 * </ul>
 *
 * <p>This class is the single source of truth for the layout; callers MUST NOT
 * recreate it inline.
 */
public final class SettlementRecord {

    /** Length in bytes of {@code HAND_ID}. */
    public static final int HAND_ID_BYTES = CanonicalActionRecord.HAND_ID_BYTES;

    /** Length in bytes of a {@code PLAYER_ID}. */
    public static final int PLAYER_ID_BYTES = CanonicalActionRecord.HASH_BYTES;

    /** Size in bytes of a single settled-participant entry. */
    public static final int ENTRY_BYTES = PLAYER_ID_BYTES + 8 + 8;

    private SettlementRecord() {
        // Utility class — no instances.
    }

    /**
     * One settled participant: a canonical {@code player_id} plus the chips it
     * put into the pot ({@code boteCents}) and the chips it was paid
     * ({@code pagarCents}), both as non-negative integer cents.
     */
    public static final class Entry {

        private final byte[] playerId;
        private final long boteCents;
        private final long pagarCents;

        public Entry(byte[] playerId, long boteCents, long pagarCents) {
            if (playerId == null || playerId.length != PLAYER_ID_BYTES) {
                throw new IllegalArgumentException("playerId must be " + PLAYER_ID_BYTES + " bytes");
            }
            if (boteCents < 0L) {
                throw new IllegalArgumentException("boteCents must be >= 0: " + boteCents);
            }
            if (pagarCents < 0L) {
                throw new IllegalArgumentException("pagarCents must be >= 0: " + pagarCents);
            }
            this.playerId = playerId.clone();
            this.boteCents = boteCents;
            this.pagarCents = pagarCents;
        }

        public byte[] getPlayerId() {
            return playerId.clone();
        }

        public long getBoteCents() {
            return boteCents;
        }

        public long getPagarCents() {
            return pagarCents;
        }
    }

    /**
     * Encodes the settlement table into its canonical byte form.
     *
     * <p>The returned array is fresh on every call and never null. Entries are
     * sorted by {@code player_id} internally, so the caller may pass them in any
     * order. Duplicate {@code player_id}s are rejected (each participant settles
     * exactly once).
     *
     * @param handId        per-hand identifier from the host, exactly 16 bytes
     * @param entries       one {@link Entry} per settled participant, 1..255 of them
     * @param sobranteCents odd-chip remainder not credited to any player, {@code >= 0}
     * @return the canonical settlement-table bytes
     */
    public static byte[] encode(byte[] handId, List<Entry> entries, long sobranteCents) {
        if (handId == null || handId.length != HAND_ID_BYTES) {
            throw new IllegalArgumentException("handId must be " + HAND_ID_BYTES + " bytes");
        }
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("entries required");
        }
        int n = entries.size();
        if (n > 255) {
            throw new IllegalArgumentException("participant count exceeds uint8: " + n);
        }
        if (sobranteCents < 0L) {
            throw new IllegalArgumentException("sobranteCents must be >= 0: " + sobranteCents);
        }

        // Sort by player_id (unsigned). A fresh array leaves the caller's list untouched.
        Entry[] sorted = entries.toArray(new Entry[0]);
        Arrays.sort(sorted, (a, b) -> Arrays.compareUnsigned(a.playerId, b.playerId));

        // Reject duplicate ids: adjacent equality after sorting is enough.
        for (int i = 1; i < sorted.length; i++) {
            if (Arrays.equals(sorted[i - 1].playerId, sorted[i].playerId)) {
                throw new IllegalArgumentException("duplicate player_id in settlement entries");
            }
        }

        int totalLen = HAND_ID_BYTES + 1 + (ENTRY_BYTES * n) + 8;
        byte[] out = new byte[totalLen];
        int p = 0;
        System.arraycopy(handId, 0, out, p, HAND_ID_BYTES);
        p += HAND_ID_BYTES;
        out[p++] = (byte) (n & 0xFF);
        for (Entry e : sorted) {
            System.arraycopy(e.playerId, 0, out, p, PLAYER_ID_BYTES);
            p += PLAYER_ID_BYTES;
            writeInt64BE(out, p, e.boteCents);
            p += 8;
            writeInt64BE(out, p, e.pagarCents);
            p += 8;
        }
        writeInt64BE(out, p, sobranteCents);

        return out;
    }

    /**
     * Verifies the chip-conservation invariant: with no rake, every cent put in
     * is either paid out to a player or left as the odd-chip remainder.
     *
     * <pre>
     *   Σ pagar_cents + sobrante_cents == Σ bote_cents
     * </pre>
     *
     * A peer checks this on its own locally-computed table before signing the
     * receipt; a {@code false} result is a settlement bug in this peer's own
     * accounting, independent of any other peer.
     */
    public static boolean amountsBalance(List<Entry> entries, long sobranteCents) {
        if (entries == null) {
            return false;
        }
        long contributed = 0L;
        long paid = 0L;
        for (Entry e : entries) {
            contributed += e.boteCents;
            paid += e.pagarCents;
        }
        return paid + sobranteCents == contributed;
    }

    /**
     * Decodes the participant count from an encoded table. Exposed so forensic
     * readers (the {@code disputed_hands} blob parser) can walk the entries.
     */
    public static int readParticipantCount(byte[] table) {
        if (table == null || table.length < HAND_ID_BYTES + 1 + 8) {
            throw new IllegalArgumentException("table too short");
        }
        return table[HAND_ID_BYTES] & 0xFF;
    }

    private static void writeInt64BE(byte[] buf, int offset, long v) {
        buf[offset]     = (byte) ((v >>> 56) & 0xFF);
        buf[offset + 1] = (byte) ((v >>> 48) & 0xFF);
        buf[offset + 2] = (byte) ((v >>> 40) & 0xFF);
        buf[offset + 3] = (byte) ((v >>> 32) & 0xFF);
        buf[offset + 4] = (byte) ((v >>> 24) & 0xFF);
        buf[offset + 5] = (byte) ((v >>> 16) & 0xFF);
        buf[offset + 6] = (byte) ((v >>> 8) & 0xFF);
        buf[offset + 7] = (byte) (v & 0xFF);
    }
}
