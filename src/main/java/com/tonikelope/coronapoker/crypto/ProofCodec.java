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

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

/**
 * Deterministic wire codec for the Bayer–Groth {@link ShuffleArgument} proof tree. Centralises the
 * byte format in one tested place so the network layer only ever moves {@code byte[]}. Every point is a
 * 32-byte Ristretto encoding; every scalar is a 32-byte big-endian value (responses are already reduced
 * into {@code [0, L)}); arrays carry a 4-byte length prefix. Decoding is total: any malformed, short, or
 * over-long input yields {@code null} (never an exception), so a network handler can feed untrusted bytes
 * directly. Point/scalar validity is NOT checked here — it is enforced later by {@link ShuffleArgument#verify}.
 */
public final class ProofCodec {

    private static final BigInteger L = EdwardsPoint.L;
    private static final int MAX_COUNT = 4096; // decks are ≤ 52; cap guards against malicious huge counts

    private ProofCodec() {
    }

    // ---------- public entry points ----------

    public static byte[] encodeShuffle(ShuffleArgument.Proof p) {
        if (p == null) {
            return null;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeShuffle(out, p);
            return out.toByteArray();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static ShuffleArgument.Proof decodeShuffle(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            Reader r = new Reader(bytes);
            ShuffleArgument.Proof p = readShuffle(r);
            return r.atEnd() ? p : null; // reject trailing garbage
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---------- ShuffleArgument ----------

    private static void writeShuffle(ByteArrayOutputStream out, ShuffleArgument.Proof p) {
        writePointArray(out, p.cf);
        writePermutation(out, p.perm);
        writePoint(out, p.q);
        writeWeightedSum(out, p.wsum);
        writePoint(out, p.scT);
        writeScalar(out, p.scZ);
    }

    private static ShuffleArgument.Proof readShuffle(Reader r) {
        byte[][] cf = readPointArray(r);
        PermutationArgument.Proof perm = readPermutation(r);
        byte[] q = readPoint(r);
        WeightedSumArgument.Proof wsum = readWeightedSum(r);
        byte[] scT = readPoint(r);
        BigInteger scZ = readScalar(r);
        return new ShuffleArgument.Proof(cf, perm, q, wsum, scT, scZ);
    }

    // ---------- PermutationArgument (wraps a ProductArgument) ----------

    private static void writePermutation(ByteArrayOutputStream out, PermutationArgument.Proof p) {
        writeProduct(out, p.product);
    }

    private static PermutationArgument.Proof readPermutation(Reader r) {
        return new PermutationArgument.Proof(readProduct(r));
    }

    // ---------- ProductArgument ----------

    private static void writeProduct(ByteArrayOutputStream out, ProductArgument.Proof p) {
        writePointArray(out, p.cp);
        writeInt(out, p.steps.length);
        for (MultiplicationProof.Proof step : p.steps) {
            writeMultiplication(out, step);
        }
        writePoint(out, p.openT);
        writeScalar(out, p.openZ);
    }

    private static ProductArgument.Proof readProduct(Reader r) {
        byte[][] cp = readPointArray(r);
        int n = readCount(r);
        MultiplicationProof.Proof[] steps = new MultiplicationProof.Proof[n];
        for (int i = 0; i < n; i++) {
            steps[i] = readMultiplication(r);
        }
        byte[] openT = readPoint(r);
        BigInteger openZ = readScalar(r);
        return new ProductArgument.Proof(cp, steps, openT, openZ);
    }

    // ---------- MultiplicationProof ----------

    private static void writeMultiplication(ByteArrayOutputStream out, MultiplicationProof.Proof p) {
        writePoint(out, p.m1);
        writePoint(out, p.m2);
        writePoint(out, p.m3);
        writeScalar(out, p.z1);
        writeScalar(out, p.z2);
        writeScalar(out, p.z3);
        writeScalar(out, p.z4);
        writeScalar(out, p.z5);
    }

    private static MultiplicationProof.Proof readMultiplication(Reader r) {
        byte[] m1 = readPoint(r);
        byte[] m2 = readPoint(r);
        byte[] m3 = readPoint(r);
        BigInteger z1 = readScalar(r);
        BigInteger z2 = readScalar(r);
        BigInteger z3 = readScalar(r);
        BigInteger z4 = readScalar(r);
        BigInteger z5 = readScalar(r);
        return new MultiplicationProof.Proof(m1, m2, m3, z1, z2, z3, z4, z5);
    }

    // ---------- WeightedSumArgument ----------

    private static void writeWeightedSum(ByteArrayOutputStream out, WeightedSumArgument.Proof p) {
        writePointArray(out, p.t);
        writePoint(out, p.tq);
        writeScalarArray(out, p.z);
        writeScalarArray(out, p.zs);
    }

    private static WeightedSumArgument.Proof readWeightedSum(Reader r) {
        byte[][] t = readPointArray(r);
        byte[] tq = readPoint(r);
        BigInteger[] z = readScalarArray(r);
        BigInteger[] zs = readScalarArray(r);
        return new WeightedSumArgument.Proof(t, tq, z, zs);
    }

    // ---------- primitives ----------

    static byte[] scalarToBytes(BigInteger s) {
        byte[] raw = s.mod(L).toByteArray();
        byte[] fixed = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, fixed, 32 - copy, copy);
        return fixed;
    }

    private static void writeScalar(ByteArrayOutputStream out, BigInteger s) {
        out.write(scalarToBytes(s), 0, 32);
    }

    private static void writePoint(ByteArrayOutputStream out, byte[] p) {
        if (p == null || p.length != 32) {
            throw new IllegalStateException("point must be 32 bytes");
        }
        out.write(p, 0, 32);
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }

    private static void writePointArray(ByteArrayOutputStream out, byte[][] arr) {
        writeInt(out, arr.length);
        for (byte[] p : arr) {
            writePoint(out, p);
        }
    }

    private static void writeScalarArray(ByteArrayOutputStream out, BigInteger[] arr) {
        writeInt(out, arr.length);
        for (BigInteger s : arr) {
            writeScalar(out, s);
        }
    }

    private static byte[] readPoint(Reader r) {
        return r.take(32);
    }

    private static BigInteger readScalar(Reader r) {
        return new BigInteger(1, r.take(32));
    }

    private static int readCount(Reader r) {
        byte[] b = r.take(4);
        int v = ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
        if (v < 0 || v > MAX_COUNT) {
            throw new IllegalStateException("count out of range");
        }
        return v;
    }

    private static byte[][] readPointArray(Reader r) {
        int n = readCount(r);
        byte[][] arr = new byte[n][];
        for (int i = 0; i < n; i++) {
            arr[i] = readPoint(r);
        }
        return arr;
    }

    private static BigInteger[] readScalarArray(Reader r) {
        int n = readCount(r);
        BigInteger[] arr = new BigInteger[n];
        for (int i = 0; i < n; i++) {
            arr[i] = readScalar(r);
        }
        return arr;
    }

    /** Bounds-checked cursor over the input; underflow throws (caught at the public entry points). */
    private static final class Reader {
        private final byte[] buf;
        private int pos;

        Reader(byte[] buf) {
            this.buf = buf;
        }

        byte[] take(int n) {
            if (n < 0 || pos + n > buf.length) {
                throw new IllegalStateException("underflow");
            }
            byte[] out = new byte[n];
            System.arraycopy(buf, pos, out, 0, n);
            pos += n;
            return out;
        }

        boolean atEnd() {
            return pos == buf.length;
        }
    }
}
