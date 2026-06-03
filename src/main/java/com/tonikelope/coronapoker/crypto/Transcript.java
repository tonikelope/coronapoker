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
package com.tonikelope.coronapoker.crypto;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Fiat–Shamir transcript for non-interactive proofs (verifiable-shuffle engine; see
 * {@code docs/sra-shuffle-argument-engine.md}). A running SHA-512 state that absorbs every public
 * value the proof depends on and derives challenges from it. Two correctness invariants the whole
 * soundness rests on, and that the test suite pins:
 *
 * <ul>
 *   <li><b>Everything is bound.</b> Each absorb is framed and <b>length-prefixed</b>, so distinct
 *       inputs can never collide by concatenation ambiguity (e.g. {@code absorb("ab")} differs from
 *       {@code absorb("a") + absorb("b")}). An omitted or unframed value is the classic Fiat–Shamir
 *       hole; here every value passes through {@link #absorb}.</li>
 *   <li><b>Challenges advance the state.</b> Producing a challenge folds its output back into the
 *       state, so two challenges in a row are independent and a prover that already saw one
 *       challenge cannot replay it.</li>
 * </ul>
 *
 * Domain-separated by construction. Not thread-safe (a transcript is a single proof's running state).
 */
public final class Transcript {

    private static final byte[] DOMAIN_PREFIX = "CoronaPoker/Transcript/v1/".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TAG_ABSORB = "ABSORB".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TAG_CHALLENGE = "CHALLENGE".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TAG_FOLD = "FOLD".getBytes(StandardCharsets.UTF_8);

    private byte[] state;

    public Transcript(String domain) {
        if (domain == null) {
            domain = "";
        }
        this.state = sha512(DOMAIN_PREFIX, domain.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha512(byte[]... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            for (byte[] p : parts) {
                md.update(p);
            }
            return md.digest();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-512 unavailable", e);
        }
    }

    /** Big-endian 8-byte length prefix + payload, so framed fields are unambiguous. */
    private static byte[] lenPrefixed(byte[] data) {
        byte[] out = new byte[8 + data.length];
        long len = data.length;
        for (int i = 0; i < 8; i++) {
            out[i] = (byte) (len >>> (8 * (7 - i)));
        }
        System.arraycopy(data, 0, out, 8, data.length);
        return out;
    }

    /** Absorb a labelled public value into the transcript (both label and data are length-framed). */
    public void absorb(String label, byte[] data) {
        if (label == null) {
            label = "";
        }
        if (data == null) {
            data = new byte[0];
        }
        state = sha512(state, TAG_ABSORB,
                lenPrefixed(label.getBytes(StandardCharsets.UTF_8)),
                lenPrefixed(data));
    }

    /** Absorb a Ristretto point by its canonical encoding. */
    public void absorbPoint(String label, EdwardsPoint p) {
        absorb(label, Ristretto255.encode(p));
    }

    /** Absorb a scalar (reduced mod L, fixed 32-byte big-endian) so equal scalars frame equally. */
    public void absorbScalar(String label, BigInteger s) {
        byte[] mod = s.mod(EdwardsPoint.L).toByteArray();
        byte[] fixed = new byte[32];
        int copy = Math.min(mod.length, 32);
        System.arraycopy(mod, mod.length - copy, fixed, 32 - copy, copy);
        absorb(label, fixed);
    }

    /** {@code n} pseudo-random challenge bytes bound to the current state, then fold them back in. */
    public byte[] challengeBytes(String label, int n) {
        if (label == null) {
            label = "";
        }
        if (n < 0) {
            throw new IllegalArgumentException("n < 0");
        }
        byte[] lbl = lenPrefixed(label.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[n];
        int produced = 0;
        long counter = 0;
        while (produced < n) {
            byte[] block = sha512(state, TAG_CHALLENGE, lbl, counterBytes(counter));
            int take = Math.min(block.length, n - produced);
            System.arraycopy(block, 0, out, produced, take);
            produced += take;
            counter++;
        }
        state = sha512(state, TAG_FOLD, out);
        return out;
    }

    /** A challenge scalar uniform in {@code [0, L)} (64 bytes reduced mod L; negligible bias). */
    public BigInteger challengeScalar(String label) {
        return new BigInteger(1, challengeBytes(label, 64)).mod(EdwardsPoint.L);
    }

    /** A single challenge bit (used per cut-and-choose round). */
    public boolean challengeBit(String label) {
        return (challengeBytes(label, 1)[0] & 1) == 1;
    }

    private static byte[] counterBytes(long c) {
        byte[] b = new byte[8];
        for (int i = 0; i < 8; i++) {
            b[i] = (byte) (c >>> (8 * (7 - i)));
        }
        return b;
    }
}
