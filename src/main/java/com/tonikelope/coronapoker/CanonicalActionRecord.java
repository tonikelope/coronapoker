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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;

/**
 * EC-Identity v1 (commit 4): canonical 92-byte serialization of a hand action.
 *
 * Every action that mutates the hand state is encoded here before being absorbed
 * into the {@link HandStateChain} ratchet (and, in commit 5, signed with the
 * actor's Ed25519 key). The byte layout is documented in
 * {@code docs/ec-identity-spec.md} §4.1:
 *
 * <pre>
 *   Offset  Size  Field           Type        Notes
 *   ------  ----  --------------  ----------  ----------------------------------
 *     0     32   PREV_H          byte[32]    H_{t-1}
 *    32     16   HAND_ID         byte[16]    Random bytes from host at hand start
 *    48     32   PLAYER_ID       byte[32]    SHA-256(NFC(nick) UTF-8)
 *    80      1   STREET          uint8       Wire enum (see STREET_* constants)
 *    81      1   ACTION_TYPE     uint8       Wire enum (see ACTION_* constants)
 *    82      8   AMOUNT_CENTS    int64 BE    Bet/raise amount in cents
 *    90      2   FLAGS           uint16 BE   bit0=is_allin, bit1=is_voluntary
 *                                            (Total: 92 bytes)
 * </pre>
 *
 * <p>This encoder is the <em>single source of truth</em>. Other parts of the
 * codebase MUST NOT recreate this layout inline. Cross-platform reproducibility
 * (Windows / Linux / macOS) is guaranteed by:
 *
 * <ul>
 *   <li>NFC normalization of the nick before UTF-8 encoding (defends against
 *       precomposed vs decomposed Unicode differences between filesystems).</li>
 *   <li>Float → integer cents conversion through {@link #amountToCents(float)}
 *       (widens to {@code double} before scaling to eliminate float jitter such
 *       as {@code 0.1f + 0.2f != 0.3f}).</li>
 *   <li>Big-endian for all multi-byte integers, independent of host byte order.</li>
 * </ul>
 */
public final class CanonicalActionRecord {

    /** Total size of an encoded record in bytes. */
    public static final int RECORD_BYTES = 92;

    /** Offset (within the record) where each field begins. Exposed for tests. */
    public static final int OFFSET_PREV_H = 0;
    public static final int OFFSET_HAND_ID = 32;
    public static final int OFFSET_PLAYER_ID = 48;
    public static final int OFFSET_STREET = 80;
    public static final int OFFSET_ACTION_TYPE = 81;
    public static final int OFFSET_AMOUNT_CENTS = 82;
    public static final int OFFSET_FLAGS = 90;

    /** Length in bytes of {@code PREV_H} and {@code PLAYER_ID}. */
    public static final int HASH_BYTES = 32;

    /** Length in bytes of {@code HAND_ID}. */
    public static final int HAND_ID_BYTES = 16;

    /** Wire enum: street identifiers. Stable across releases. */
    public static final int STREET_PREFLOP = 0;
    public static final int STREET_FLOP = 1;
    public static final int STREET_TURN = 2;
    public static final int STREET_RIVER = 3;
    public static final int STREET_SHOWDOWN = 4;
    /**
     * Run-it-twice SIDE-B street identifiers. The second board re-deals the
     * remaining community streets a second time; its community-reveal records
     * carry these dedicated codes so they are unambiguous in the H_t ratchet
     * (a SIDE-A turn reveal and a SIDE-B turn reveal must hash to different
     * records or host and clients would disagree on the chain). Stable enum,
     * separate range from the live-board streets.
     */
    public static final int STREET_RIT2_FLOP = 11;
    public static final int STREET_RIT2_TURN = 12;
    public static final int STREET_RIT2_RIVER = 13;

    /**
     * Wire enum: action identifiers. Note the distinction between {@code BET}
     * and {@code RAISE} — the wire encoding keeps them separate even if Java
     * collapses both into {@code Player.BET} internally. The translation
     * Java↔wire is the caller's responsibility; this class only accepts the
     * already-translated wire constants.
     */
    public static final int ACTION_FOLD = 0;
    public static final int ACTION_CHECK = 1;
    public static final int ACTION_CALL = 2;
    public static final int ACTION_BET = 3;
    public static final int ACTION_RAISE = 4;
    public static final int ACTION_ALLIN = 5;
    /**
     * EC-Identity v1 (Phase 3): host-signed announcement of the community cards
     * revealed at a street boundary (flop, turn, river). PLAYER_ID is the host's
     * canonical player id (the signer), STREET is the street being revealed,
     * AMOUNT_CENTS packs the card indices (see {@link #packCommunityCards} /
     * {@link #unpackCommunityCards}). Recipients verify the signature with the
     * host's pubkey, compare the announced indices against the locally-decoded
     * PIECE bytes (mismatch ⇒ security lockdown), and absorb the record+sig
     * into H_t. This closes the cross-recipient fork attack: a host that
     * announces different cards to different peers, or announces correctly but
     * ships a divergent PIECE, is caught the moment any peer reaches the
     * verify-or-compare step.
     */
    public static final int ACTION_COMMUNITY = 6;

    /** {@code FLAGS} bit positions. */
    public static final int FLAG_BIT_ALLIN = 0;
    public static final int FLAG_BIT_VOLUNTARY = 1;

    private CanonicalActionRecord() {
        // Utility class — no instances.
    }

    /**
     * Computes a canonical {@code PLAYER_ID} from a player nick.
     *
     * <p>The nick is NFC-normalized and UTF-8 encoded before hashing. Two nicks
     * that look the same on screen but use different Unicode normalization
     * forms collapse to the same {@code PLAYER_ID}, while genuinely different
     * nicks always produce different ids (with overwhelming probability).
     *
     * @param nick non-{@code null}, non-empty nickname
     * @return 32-byte SHA-256 digest
     */
    public static byte[] playerIdFromNick(String nick) {
        if (nick == null || nick.isEmpty()) {
            throw new IllegalArgumentException("nick required");
        }
        byte[] nfc = Normalizer.normalize(nick, Normalizer.Form.NFC).getBytes(StandardCharsets.UTF_8);
        return sha256(nfc);
    }

    /**
     * Converts a float amount (in CoronaPoker chips, two decimals) into the
     * 64-bit integer cents value stored in the record. Defends against IEEE-754
     * jitter: float arithmetic is widened to {@code double} before scaling and
     * rounded via {@link Math#round(double)} (banker's rounding to nearest
     * long). Negative amounts are rejected — actions never have negative bets.
     */
    public static long amountToCents(float amount) {
        if (Float.isNaN(amount) || Float.isInfinite(amount)) {
            throw new IllegalArgumentException("amount must be finite: " + amount);
        }
        if (amount < 0f) {
            throw new IllegalArgumentException("amount cannot be negative: " + amount);
        }
        return Math.round((double) amount * 100.0);
    }

    /**
     * Encodes one action into the canonical 92-byte record.
     *
     * <p>All arguments are validated. The returned array is fresh on every
     * call, never null, and always exactly {@link #RECORD_BYTES} bytes long.
     *
     * @param prevH           previous chain hash {@code H_{t-1}}, exactly 32 bytes
     * @param handId          per-hand identifier from the host, exactly 16 bytes
     * @param playerId        canonical player id, exactly 32 bytes (use
     *                        {@link #playerIdFromNick(String)})
     * @param street          one of the {@code STREET_*} constants
     * @param actionType      one of the {@code ACTION_*} constants
     * @param amountCents     bet/raise amount in cents; must be {@code >= 0} and
     *                        is {@code 0} for FOLD/CHECK
     * @param isAllin         {@code true} if this action puts the player all-in
     * @param isVoluntary     {@code true} for player-initiated actions;
     *                        {@code false} only for host-issued auto-folds
     *                        (timeouts; see spec §4.5)
     * @return the 92-byte encoded record
     */
    public static byte[] encode(byte[] prevH, byte[] handId, byte[] playerId,
            int street, int actionType, long amountCents,
            boolean isAllin, boolean isVoluntary) {
        if (prevH == null || prevH.length != HASH_BYTES) {
            throw new IllegalArgumentException("prevH must be " + HASH_BYTES + " bytes");
        }
        if (handId == null || handId.length != HAND_ID_BYTES) {
            throw new IllegalArgumentException("handId must be " + HAND_ID_BYTES + " bytes");
        }
        if (playerId == null || playerId.length != HASH_BYTES) {
            throw new IllegalArgumentException("playerId must be " + HASH_BYTES + " bytes");
        }
        if (street < 0 || street > 0xFF) {
            throw new IllegalArgumentException("street out of uint8 range: " + street);
        }
        if (actionType < 0 || actionType > 0xFF) {
            throw new IllegalArgumentException("actionType out of uint8 range: " + actionType);
        }
        if (amountCents < 0L) {
            throw new IllegalArgumentException("amountCents must be >= 0: " + amountCents);
        }

        byte[] out = new byte[RECORD_BYTES];
        System.arraycopy(prevH, 0, out, OFFSET_PREV_H, HASH_BYTES);
        System.arraycopy(handId, 0, out, OFFSET_HAND_ID, HAND_ID_BYTES);
        System.arraycopy(playerId, 0, out, OFFSET_PLAYER_ID, HASH_BYTES);
        out[OFFSET_STREET] = (byte) street;
        out[OFFSET_ACTION_TYPE] = (byte) actionType;
        writeInt64BE(out, OFFSET_AMOUNT_CENTS, amountCents);

        int flags = 0;
        if (isAllin) {
            flags |= (1 << FLAG_BIT_ALLIN);
        }
        if (isVoluntary) {
            flags |= (1 << FLAG_BIT_VOLUNTARY);
        }
        out[OFFSET_FLAGS] = (byte) ((flags >> 8) & 0xFF);
        out[OFFSET_FLAGS + 1] = (byte) (flags & 0xFF);

        return out;
    }

    /**
     * EC-Identity v1 (Phase 3): packs 1..3 community card indices (0..51 each)
     * into the AMOUNT_CENTS field of a community-reveal record. Layout is
     * little-endian within the 8-byte slot but card-major (first card in the
     * lowest byte of the packed value), so unpacking byte-by-byte yields the
     * cards in announce order regardless of host endianness.
     */
    public static long packCommunityCards(int[] cards) {
        if (cards == null || cards.length == 0 || cards.length > 3) {
            throw new IllegalArgumentException("cards must have 1..3 entries");
        }
        long packed = 0L;
        for (int i = 0; i < cards.length; i++) {
            int c = cards[i];
            if (c < 0 || c > 51) {
                throw new IllegalArgumentException("card index out of range: " + c);
            }
            packed |= ((long) (c & 0xFF)) << (i * 8);
        }
        return packed;
    }

    /**
     * EC-Identity v1 (Phase 3): inverse of {@link #packCommunityCards}.
     * {@code numCards} is implied by the street (3 for FLOP, 1 for TURN/RIVER).
     */
    public static int[] unpackCommunityCards(long packed, int numCards) {
        if (numCards <= 0 || numCards > 3) {
            throw new IllegalArgumentException("numCards must be 1..3");
        }
        int[] out = new int[numCards];
        for (int i = 0; i < numCards; i++) {
            out[i] = (int) ((packed >> (i * 8)) & 0xFF);
        }
        return out;
    }

    /**
     * EC-Identity v1: reads the {@code AMOUNT_CENTS} field (8 bytes, big-endian)
     * from an encoded record. Exposed for receivers that need the raw amount
     * (e.g., the Phase 3 community-reveal flow unpacks card indices from it).
     */
    public static long readAmountCents(byte[] record) {
        if (record == null || record.length != RECORD_BYTES) {
            throw new IllegalArgumentException("record must be " + RECORD_BYTES + " bytes");
        }
        return readInt64BE(record, OFFSET_AMOUNT_CENTS);
    }

    /**
     * EC-Identity v1: reads the {@code STREET} byte (uint8) from an encoded record.
     */
    public static int readStreet(byte[] record) {
        if (record == null || record.length != RECORD_BYTES) {
            throw new IllegalArgumentException("record must be " + RECORD_BYTES + " bytes");
        }
        return record[OFFSET_STREET] & 0xFF;
    }

    /**
     * EC-Identity v1: reads the {@code ACTION_TYPE} byte (uint8) from an encoded
     * record. Used by receivers to dispatch community-reveal records to the
     * Phase 3 verification flow instead of the player-action flow.
     */
    public static int readActionType(byte[] record) {
        if (record == null || record.length != RECORD_BYTES) {
            throw new IllegalArgumentException("record must be " + RECORD_BYTES + " bytes");
        }
        return record[OFFSET_ACTION_TYPE] & 0xFF;
    }

    private static long readInt64BE(byte[] buf, int offset) {
        return ((long) (buf[offset] & 0xFF) << 56)
                | ((long) (buf[offset + 1] & 0xFF) << 48)
                | ((long) (buf[offset + 2] & 0xFF) << 40)
                | ((long) (buf[offset + 3] & 0xFF) << 32)
                | ((long) (buf[offset + 4] & 0xFF) << 24)
                | ((long) (buf[offset + 5] & 0xFF) << 16)
                | ((long) (buf[offset + 6] & 0xFF) << 8)
                | ((long) (buf[offset + 7] & 0xFF));
    }

    /**
     * Writes a {@code long} in big-endian order at the given offset.
     */
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

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("SHA-256 unavailable", ex);
        }
    }
}
