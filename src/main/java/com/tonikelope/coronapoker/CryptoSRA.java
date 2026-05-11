/*
 * Copyright (C) 2026 tonikelope
 *
 * Zero-Trust Pure Java EC-SRA Cryptographic Engine.
 * his module implements Commutative SRA over Curve25519.
 * It enforces mathematical consensus
 * for the card shuffling and locking phases.
 * 
 * [OPTIMIZED: Radix-16 Core & Multi-Threading]
 */
package com.tonikelope.coronapoker;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

public class CryptoSRA {

    // Prime P for Curve25519: 2^255 - 19
    private static final BigInteger P = new BigInteger("57896044618658097711785492504343953926634992332820282019728792003956564819949");
    
    // Subgroup order L for Curve25519 (Used for Fermat's Little Theorem inversion)
    private static final BigInteger L = new BigInteger("7237005577332262213973186563042994240857116359379907606001950938285454250989");
    
    // Optimizaciones de memoria: Constantes precalculadas
    private static final BigInteger A_MONTGOMERY = new BigInteger("486662");
    private static final BigInteger EXP_EULER = P.subtract(BigInteger.ONE).shiftRight(1);

    private static final SecureRandom CSPRNG = new SecureRandom();
    
    // Cached 52-card base deck
    private static volatile byte[][] genesisDeckCache = null;

    // Al cargar la clase en memoria, un hilo paralelo empieza a calcular la baraja. 
    static {
        ForkJoinPool.commonPool().execute(() -> {
            try { getGenesisDeck(); } catch (Exception e) {}
        });
    }

    /**
     * Generates a secure, 32-byte scalar to be used as a Commutative Lock.
     */
    public static byte[] generateLockScalar() {
        byte[] scalar = new byte[32];
        CSPRNG.nextBytes(scalar);
        
        // Mandatory X25519 clamping
        scalar[0] &= 248;
        scalar[31] &= 127;
        scalar[31] |= 64;
        
        return scalar;
    }

    /**
     * Calculates the multiplicative inverse of the lock scalar modulo L.
     */
    public static byte[] getUnlockScalar(byte[] lockScalar) {
        BigInteger s = decodeLittleEndian(lockScalar);
        BigInteger inverse = s.modInverse(L);
        return encodeLittleEndian(inverse, 32);
    }

    /**
     * Applies a Commutative Lock to an array of 32-byte points.
     * OPTIMIZADO: Usa todos los núcleos de la CPU en paralelo con Aritmética Primitiva.
     */
    public static byte[] applyCommutativeLock(byte[] deck, byte[] scalar) {
        if (deck == null || deck.length % 32 != 0) return null;
        
        int numCards = deck.length / 32;
        byte[] lockedDeck = new byte[deck.length];
        
        IntStream.range(0, numCards).parallel().forEach(i -> {
            byte[] card = new byte[32];
            System.arraycopy(deck, i * 32, card, 0, 32);
            
            // Apply Radix-16 Montgomery Ladder without clamping
            byte[] lockedCard = scalarMultNoClamp(scalar, card);
            
            System.arraycopy(lockedCard, 0, lockedDeck, i * 32, 32);
        });
        
        return lockedDeck;
    }

    /**
     * Shuffles a flat array of 32-byte curve points deterministically.
     */
    public static byte[] shuffleDeck(byte[] deck, byte[] seed) {
        if (deck == null || deck.length % 32 != 0) return null;
        
        byte[] shuffled = new byte[deck.length];
        System.arraycopy(deck, 0, shuffled, 0, deck.length);
        
        try {
            SecureRandom seededRng = SecureRandom.getInstance("SHA1PRNG");
            seededRng.setSeed(seed);
            int numCards = deck.length / 32;
            byte[] temp = new byte[32];
            
            for (int i = numCards - 1; i > 0; i--) {
                int j = seededRng.nextInt(i + 1);
                System.arraycopy(shuffled, i * 32, temp, 0, 32);
                System.arraycopy(shuffled, j * 32, shuffled, i * 32, 32);
                System.arraycopy(temp, 0, shuffled, j * 32, 32);
            }
        } catch (Exception e) {
            throw new RuntimeException("Deterministic shuffle failed", e);
        }
        return shuffled;
    }

    /**
     * Retrieves the base 52-card unencrypted deck.
     */
    public static byte[] getGenesisDeck() {
        if (genesisDeckCache == null) {
            synchronized (CryptoSRA.class) {
                if (genesisDeckCache == null) {
                    byte[][] tempCache = new byte[52][32];
                    try {
                        byte[][] seeds = new byte[52][];
                        for(int i = 0; i < 52; i++) {
                            seeds[i] = ("PANOPTES_CARD_" + i).getBytes("UTF-8");
                        }
                        
                        IntStream.range(0, 52).parallel().forEach(i -> {
                            try {
                                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                                byte[] cofactor = new byte[32]; 
                                cofactor[0] = 8; 
                                
                                BigInteger x = new BigInteger(1, sha256.digest(seeds[i])).mod(P);
                                
                                while (!isOnCurve(x)) {
                                    x = x.add(BigInteger.ONE).mod(P);
                                }
                                
                                byte[] rawPoint = encodeLittleEndian(x, 32);
                                tempCache[i] = scalarMultNoClamp(cofactor, rawPoint);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                        genesisDeckCache = tempCache;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to generate Genesis Deck", e);
                    }
                }
            }
        }
        
        byte[] flatDeck = new byte[52 * 32];
        for (int i = 0; i < 52; i++) {
            System.arraycopy(genesisDeckCache[i], 0, flatDeck, i * 32, 32);
        }
        return flatDeck;
    }

    /**
     * Resolves a fully decrypted curve point back to its numeric card index (0-51).
     */
    public static int resolveCardIndex(byte[] unlockedCard) {
        getGenesisDeck(); // Ensure cache is populated
        for (int i = 0; i < 52; i++) {
            if (Arrays.equals(unlockedCard, genesisDeckCache[i])) {
                return i;
            }
        }
        return -1;
    }

    // =========================================================================
    // RADIX-16 TWEETNACL X25519 CORE (ZERO BIGINTEGER IN HOT PATH)
    // =========================================================================

    private static void unpack(long[] out, byte[] in) {
        for (int i = 0; i < 16; i++) {
            out[i] = (in[2 * i] & 0xff) | ((in[2 * i + 1] & 0xff) << 8);
        }
    }

    private static void pack(byte[] o, long[] a) {
        long[] m = new long[16];
        long[] t = new long[16];
        System.arraycopy(a, 0, t, 0, 16);
        car(t); car(t); car(t);
        for (int j = 0; j < 2; j++) {
            m[0] = t[0] - 0xffed;
            for (int i = 1; i < 15; i++) {
                m[i] = t[i] - 0xffff - ((m[i - 1] >> 16) & 1);
                m[i - 1] &= 0xffff;
            }
            m[15] = t[15] - 0x7fff - ((m[14] >> 16) & 1);
            long carry = (m[15] >> 16) & 1;
            m[14] &= 0xffff;
            m[15] &= 0xffff;
            long mask = 1 - carry;
            for (int i = 0; i < 16; i++) {
                t[i] = (t[i] * (1 - mask)) + (m[i] * mask);
            }
        }
        for (int i = 0; i < 16; i++) {
            o[2 * i] = (byte) t[i];
            o[2 * i + 1] = (byte) (t[i] >> 8);
        }
    }

    private static void car(long[] o) {
        for (int i = 0; i < 15; i++) {
            o[i + 1] += o[i] >> 16;
            o[i] &= 0xffff;
        }
        o[0] += 38 * (o[15] >> 16);
        o[15] &= 0xffff;
    }

    private static void M(long[] o, long[] a, long[] b) {
        long[] t = new long[31];
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) t[i + j] += a[i] * b[j];
        }
        for (int i = 0; i < 15; i++) t[i] += 38 * t[i + 16];
        System.arraycopy(t, 0, o, 0, 16);
        car(o); car(o);
    }

    private static void S(long[] o, long[] a) { M(o, a, a); }

    private static void A(long[] o, long[] a, long[] b) {
        for (int i = 0; i < 16; i++) o[i] = a[i] + b[i];
    }

    private static void Z(long[] o, long[] a, long[] b) {
        for (int i = 0; i < 16; i++) o[i] = a[i] - b[i];
    }

    private static void inv(long[] out, long[] a) {
        long[] c = new long[16];
        System.arraycopy(a, 0, c, 0, 16);
        for (int i = 253; i >= 0; i--) {
            S(c, c);
            if (i != 2 && i != 4) M(c, c, a);
        }
        System.arraycopy(c, 0, out, 0, 16);
    }

    private static void cswap(long swap, long[] a, long[] b) {
        long mask = swap == 1 ? -1L : 0L;
        for (int i = 0; i < 16; i++) {
            long t = mask & (a[i] ^ b[i]);
            a[i] ^= t;
            b[i] ^= t;
        }
    }

    private static byte[] scalarMultNoClamp(byte[] scalar, byte[] point) {
        long[] x1 = new long[16]; unpack(x1, point);
        long[] x2 = new long[16]; x2[0] = 1;
        long[] z2 = new long[16];
        long[] x3 = new long[16]; System.arraycopy(x1, 0, x3, 0, 16);
        long[] z3 = new long[16]; z3[0] = 1;

        long swap = 0;
        long[] A = new long[16], AA = new long[16], B = new long[16], BB = new long[16];
        long[] E = new long[16], C = new long[16], D = new long[16];
        long[] DA = new long[16], CB = new long[16], t1 = new long[16], t2 = new long[16];
        long[] t3_arr = new long[16], t4 = new long[16], a24 = new long[16];
        
        // (486662 - 2) / 4 = 121665 -> Hex: 0x1DB41
        a24[0] = 56129; // 121665 & 0xffff
        a24[1] = 1;     // 121665 >> 16

        for (int t = 254; t >= 0; t--) {
            long bit = (scalar[t / 8] >> (t % 8)) & 1;
            swap ^= bit;
            cswap(swap, x2, x3);
            cswap(swap, z2, z3);
            swap = bit;

            A(A, x2, z2);
            S(AA, A);
            Z(B, x2, z2);
            S(BB, B);
            Z(E, AA, BB);
            A(C, x3, z3);
            Z(D, x3, z3);

            M(DA, D, A);
            M(CB, C, B);

            A(t1, DA, CB);
            S(x3, t1);

            Z(t2, DA, CB);
            S(t3_arr, t2);
            M(z3, t3_arr, x1);

            M(t4, E, a24);
            A(t4, AA, t4);
            M(z2, E, t4);
            System.arraycopy(AA, 0, x2, 0, 16);
            M(x2, x2, BB);
        }
        cswap(swap, x2, x3);
        cswap(swap, z2, z3);

        long[] z2inv = new long[16];
        inv(z2inv, z2);
        long[] res = new long[16];
        M(res, x2, z2inv);

        byte[] out = new byte[32];
        pack(out, res);
        return out;
    }

    // =========================================================================
    // BOOTSTRAP UTILS
    // =========================================================================

    private static boolean isOnCurve(BigInteger x) {
        BigInteger x2 = x.multiply(x).mod(P);
        BigInteger x3 = x2.multiply(x).mod(P);
        BigInteger rhs = x3.add(x2.multiply(A_MONTGOMERY)).add(x).mod(P);
        return rhs.modPow(EXP_EULER, P).equals(BigInteger.ONE);
    }

    private static BigInteger decodeLittleEndian(byte[] b) {
        byte[] reversed = new byte[b.length + 1];
        for (int i = 0; i < b.length; i++) reversed[i + 1] = b[b.length - 1 - i];
        return new BigInteger(1, reversed);
    }

    private static byte[] encodeLittleEndian(BigInteger n, int length) {
        byte[] b = n.toByteArray();
        byte[] result = new byte[length];
        int offset = (b[0] == 0 && b.length > length) ? 1 : 0;
        int len = Math.min(b.length - offset, length);
        for (int i = 0; i < len; i++) result[i] = b[b.length - 1 - i];
        return result;
    }
    
    public static byte[] generateCommitment(byte[] seed) {
        try { return MessageDigest.getInstance("SHA-256").digest(seed); } 
        catch (Exception e) { return new byte[32]; }
    }

    public static byte[] shuffleCardIndices(byte[] seed) {
        byte[] deck52 = new byte[52];
        for (int i = 0; i < 52; i++) deck52[i] = (byte) i;
        try {
            SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
            sr.setSeed(seed);
            for (int i = 51; i > 0; i--) {
                int j = sr.nextInt(i + 1);
                byte temp = deck52[i]; deck52[i] = deck52[j]; deck52[j] = temp;
            }
        } catch (Exception e) {}
        return deck52;
    }
}