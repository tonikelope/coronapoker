/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.crypto;

import java.security.SecureRandom;
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
}
