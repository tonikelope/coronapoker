/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.security.SecureRandom;

/** Generates opaque lowercase session identifiers for the strict INIT wire. */
public final class GameSessionIds {
    public static final int LENGTH = 50;
    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private GameSessionIds() { }

    public static String random() {
        SecureRandom random = new SecureRandom();
        char[] value = new char[LENGTH];
        for (int i = 0; i < value.length; i++) {
            value[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(value);
    }
}
