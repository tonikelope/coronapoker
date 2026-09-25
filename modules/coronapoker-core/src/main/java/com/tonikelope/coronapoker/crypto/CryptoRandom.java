/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.crypto;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Process-wide CSPRNG used by the frontend-independent cryptographic engine. */
public final class CryptoRandom {

    private static volatile SecureRandom generator = new SecureRandom();

    private CryptoRandom() { }

    public static void configure(SecureRandom secureRandom) {
        generator = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    static SecureRandom generator() {
        return generator;
    }

    public static void fill(byte[] target) {
        generator.nextBytes(target);
    }

    public static int nextInt(int bound) {
        return generator.nextInt(bound);
    }

    public static double nextDouble() {
        return generator.nextDouble();
    }

    public static void shuffle(List<?> values) {
        Collections.shuffle(values, generator);
    }
}
