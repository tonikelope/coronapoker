/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ZERO-TRUST: an SRA scalar that reaches us from another peer must be usable
 * before it is stored.
 *
 * A leaving peer hands over its community half as a "testament", and a revealed
 * showdown key travels the same way. Both used to be accepted on length alone,
 * and 32 zero bytes clear that bar: the value has no inverse, so the moment
 * anything derived the matching half from it the dealer thread died — and an
 * exception there does not stay contained, it closes the process. One crafted
 * EXIT killed the host, and with it the table.
 *
 * These pin the guard both ways: nothing outside [1, L) is ever accepted, and
 * every scalar the engine itself produces still is.
 */
public class ScalarValidationTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    /** The scalar L itself (the order), little-endian — the first value out of range. */
    private static byte[] scalarL() {
        byte[] be = RistrettoSRA.L.toByteArray();
        byte[] le = new byte[32];
        // toByteArray is big-endian and may carry a leading sign byte.
        int len = Math.min(32, be.length);
        for (int i = 0; i < len; i++) {
            le[i] = be[be.length - 1 - i];
        }
        return le;
    }

    @Test
    @DisplayName("Zero is rejected: it is the crafted testament that killed the host")
    void zeroScalarIsRejected() {
        assertFalse(RistrettoSRA.isValidScalar(new byte[32]),
                "32 zero bytes clear a length check but have no inverse");
        assertThrows(IllegalArgumentException.class,
                () -> RistrettoSRA.getUnlockScalar(new byte[32]),
                "inverting it must fail as a rejected argument, not as a bare arithmetic blow-up");
    }

    @Test
    @DisplayName("Wrong shapes are rejected")
    void malformedScalarsAreRejected() {
        assertFalse(RistrettoSRA.isValidScalar(null));
        assertFalse(RistrettoSRA.isValidScalar(new byte[0]));
        assertFalse(RistrettoSRA.isValidScalar(new byte[31]));
        assertFalse(RistrettoSRA.isValidScalar(new byte[33]));
        assertThrows(IllegalArgumentException.class, () -> RistrettoSRA.getUnlockScalar(null));
        assertThrows(IllegalArgumentException.class, () -> RistrettoSRA.getUnlockScalar(new byte[31]));
    }

    @Test
    @DisplayName("Values at or above the group order are rejected")
    void outOfRangeScalarsAreRejected() {
        assertFalse(RistrettoSRA.isValidScalar(scalarL()), "L itself is out of range: [1, L)");

        byte[] allOnes = new byte[32];
        Arrays.fill(allOnes, (byte) 0xff);
        assertFalse(RistrettoSRA.isValidScalar(allOnes), "2^256-1 is far above L");
    }

    @Test
    @DisplayName("One below the order is the largest scalar still accepted")
    void theLargestInRangeScalarIsAccepted() {
        BigInteger lMinusOne = RistrettoSRA.L.subtract(BigInteger.ONE);
        byte[] be = lMinusOne.toByteArray();
        byte[] le = new byte[32];
        for (int i = 0; i < Math.min(32, be.length); i++) {
            le[i] = be[be.length - 1 - i];
        }
        assertTrue(RistrettoSRA.isValidScalar(le), "L-1 is in range and invertible");
    }

    @Test
    @DisplayName("Every scalar the engine generates is accepted and round-trips")
    void generatedScalarsStayValid() {
        for (int i = 0; i < 64; i++) {
            byte[] lock = RistrettoSRA.generateLockScalar();
            assertTrue(RistrettoSRA.isValidScalar(lock), "a generated lock must always be usable");

            byte[] unlock = RistrettoSRA.getUnlockScalar(lock);
            assertTrue(RistrettoSRA.isValidScalar(unlock), "so must its matching half");
            // Inverting twice returns the original: the guard does not disturb the pair.
            assertArrayEquals(lock, RistrettoSRA.getUnlockScalar(unlock));
        }
    }

    @Test
    @DisplayName("The smallest usable scalar (1) is its own inverse")
    void oneIsAcceptedAndSelfInverse() {
        byte[] one = new byte[32];
        one[0] = 1;
        assertTrue(RistrettoSRA.isValidScalar(one));
        assertArrayEquals(one, RistrettoSRA.getUnlockScalar(one));
    }
}
