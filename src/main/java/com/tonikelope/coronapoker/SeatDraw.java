/*
 * Copyright (C) 2020 tonikelope
 _              _ _        _
| |_ ___  _ __ (_) | _____| | ___  _ __   ___
| __/ _ \| '_ \| | |/ / _ \ |/ _ \| '_ \ / _ \
| || (_) | | | | |   <  __/ | (_) | |_) |  __/
 \__\___/|_| |_|_|_|\_\___|_|\___/| .__/ \___|
 ____    ___  ____    ___
|___ \  / _ \|___ \  / _ \
  __) || | | | __) || | | |
 / __/ | |_| |/ __/ | |_| |
|_____| \___/|_____| \___/

https://github.com/tonikelope/coronapoker
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure, network-free crypto for the verifiable seat draw (commit-reveal randomness beacon).
 *
 * <p>The host used to shuffle the seats locally with its own CSPRNG and broadcast the result,
 * which every client accepted blindly, so a hostile host could hand-pick the seating (and, by
 * extension, the first dealer/blinds, both derived from the permutation's head). This class is
 * the deterministic core of the replacement: every human peer contributes a secret, all secrets
 * are committed before any is revealed, and the final permutation is a fixed function of the
 * combined reveals. No single participant (host included) can bias the outcome, and every peer
 * derives the exact same order independently — the host stops being trusted for the draw.
 *
 * <p>Everything here is deterministic and side-effect free so it can be unit-tested without a
 * table: {@link #commit} binds a reveal, {@link #verifyCommit} checks a reveal against its
 * commit, {@link #deriveSeed} folds every reveal into a 48-byte seed, and {@link #deriveOrder}
 * turns that seed into the seated order via {@link DeterministicShuffle#shufflePermutation}.
 */
public class SeatDraw {

    // Domain separators keep these hashes from ever colliding with any other digest in the
    // protocol (deck seeds, HMACs, HAND_ID derivation, ...). A trailing NUL mirrors the
    // convention already used by the Ed25519 signing domains in IdentityManager.
    private static final byte[] COMMIT_DOMAIN = "SEATCOMMIT\0".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SEED_DOMAIN = "SEATSEED\0".getBytes(StandardCharsets.UTF_8);

    public static final int NONCE_BYTES = 32;
    public static final int REVEAL_BYTES = 32;
    public static final int COMMIT_BYTES = 32; // SHA-256
    // DeterministicShuffle wants 32-byte AES key + 16-byte CTR IV.
    public static final int SEED_BYTES = 48;

    private SeatDraw() {
    }

    /**
     * Commitment to a per-peer reveal: {@code SHA-256(COMMIT_DOMAIN || nonce || len(nick) || nick || r)}.
     * A peer broadcasts this in the commit phase; it hides {@code r} (pre-image resistance) yet binds
     * it (collision resistance), so no one can pick their reveal after seeing anyone else's.
     */
    public static byte[] commit(byte[] nonce, String nick, byte[] reveal) {
        requireNonce(nonce);
        requireNick(nick);
        requireReveal(reveal);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(COMMIT_DOMAIN);
            md.update(nonce);
            byte[] nickBytes = nick.getBytes(StandardCharsets.UTF_8);
            writeLenPrefixed(md, nickBytes);
            md.update(reveal);
            return md.digest();
        } catch (Exception e) {
            throw new RuntimeException("Seat commit hashing failed", e);
        }
    }

    /**
     * Constant-time check that {@code reveal} is the pre-image behind {@code expectedCommit} for
     * this {@code (nonce, nick)}. false on any malformed input or mismatch.
     */
    public static boolean verifyCommit(byte[] nonce, String nick, byte[] reveal, byte[] expectedCommit) {
        if (nonce == null || nick == null || nick.isEmpty() || reveal == null
                || reveal.length != REVEAL_BYTES || expectedCommit == null
                || expectedCommit.length != COMMIT_BYTES || nonce.length != NONCE_BYTES) {
            return false;
        }
        return MessageDigest.isEqual(commit(nonce, nick, reveal), expectedCommit);
    }

    /**
     * Folds every contributor's reveal into a 48-byte seed:
     * {@code SHA-512(SEED_DOMAIN || nonce || for each nick asc: len(nick) || nick || r)[0..48]}.
     *
     * <p>Contributors are processed in ascending nick order so every peer, given the same reveal
     * set, derives byte-identical material regardless of arrival order. Each nick is length-prefixed
     * so the concatenation is unambiguous. The seed depends on <em>all</em> reveals, so a single
     * honest contributor is enough to make the result unpredictable to everyone else.
     *
     * @param reveals nick -&gt; 32-byte reveal, one entry per contributor (must be non-empty)
     */
    public static byte[] deriveSeed(byte[] nonce, Map<String, byte[]> reveals) {
        requireNonce(nonce);
        if (reveals == null || reveals.isEmpty()) {
            throw new IllegalArgumentException("At least one reveal is required");
        }
        // TreeMap gives a deterministic ascending-by-nick iteration independent of the caller's map.
        TreeMap<String, byte[]> ordered = new TreeMap<>(reveals);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(SEED_DOMAIN);
            md.update(nonce);
            for (Map.Entry<String, byte[]> e : ordered.entrySet()) {
                String nick = e.getKey();
                byte[] r = e.getValue();
                requireNick(nick);
                requireReveal(r);
                writeLenPrefixed(md, nick.getBytes(StandardCharsets.UTF_8));
                md.update(r);
            }
            byte[] full = md.digest(); // 64 bytes
            byte[] seed = new byte[SEED_BYTES];
            System.arraycopy(full, 0, seed, 0, SEED_BYTES);
            return seed;
        } catch (Exception e) {
            throw new RuntimeException("Seat seed hashing failed", e);
        }
    }

    /**
     * Deterministically seats the {@code roster} using {@code seed}: the roster is first sorted into
     * a canonical ascending order (so the input never depends on the order the host announced it in),
     * then permuted by {@link DeterministicShuffle#shufflePermutation}. Every peer that feeds the same
     * roster and seed gets the exact same array back.
     *
     * @return the seated order (index 0 = first seat), a fresh array; never mutates {@code roster}
     */
    public static String[] deriveOrder(List<String> roster, byte[] seed) {
        if (roster == null || roster.isEmpty()) {
            throw new IllegalArgumentException("roster must be non-empty");
        }
        if (seed == null || seed.length != SEED_BYTES) {
            throw new IllegalArgumentException("seed must be " + SEED_BYTES + " bytes");
        }
        List<String> canonical = new ArrayList<>(roster);
        Collections.sort(canonical);
        int n = canonical.size();
        int[] perm = DeterministicShuffle.shufflePermutation(n, seed);
        String[] seated = new String[n];
        for (int i = 0; i < n; i++) {
            seated[i] = canonical.get(perm[i]);
        }
        return seated;
    }

    private static void writeLenPrefixed(MessageDigest md, byte[] data) {
        // 2-byte big-endian length prefix. Nicks are bounded well under 65535 bytes.
        md.update((byte) ((data.length >>> 8) & 0xFF));
        md.update((byte) (data.length & 0xFF));
        md.update(data);
    }

    private static void requireNonce(byte[] nonce) {
        if (nonce == null || nonce.length != NONCE_BYTES) {
            throw new IllegalArgumentException("nonce must be " + NONCE_BYTES + " bytes");
        }
    }

    private static void requireReveal(byte[] reveal) {
        if (reveal == null || reveal.length != REVEAL_BYTES) {
            throw new IllegalArgumentException("reveal must be " + REVEAL_BYTES + " bytes");
        }
    }

    private static void requireNick(String nick) {
        if (nick == null || nick.isEmpty()) {
            throw new IllegalArgumentException("nick must be non-empty");
        }
    }
}
