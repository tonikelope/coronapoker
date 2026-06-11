/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.net;

import com.tonikelope.coronapoker.Helpers;
import java.nio.charset.StandardCharsets;
import java.security.KeyException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AAA tests for {@link Helpers#encryptBytes}/{@link Helpers#decryptBytes}, the raw-bytes
 * siblings of encryptString/decryptString used as binary {@link com.tonikelope.coronapoker.WireFrame}
 * bodies.
 *
 * The load-bearing test is {@link #equivalenceWithStringPath()}: it pins that the new byte
 * path produces, modulo Base64, the EXACT same crypto bytes as the untouched string path,
 * so the in-game channel and the binary path can never silently diverge.
 */
class EncryptBytesTest {

    private static SecretKeySpec AES;
    private static SecretKeySpec HMAC;
    private static byte[] IV;

    @BeforeAll
    static void keys() {
        // The app initialises this CSPRNG at startup; the test JVM must do the same
        // for the auto-IV overload (encryptBytes(payload, aes, hmac)).
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
        SecureRandom rnd = new SecureRandom();
        byte[] aes = new byte[32];
        byte[] hmac = new byte[32];
        IV = new byte[16];
        rnd.nextBytes(aes);
        rnd.nextBytes(hmac);
        rnd.nextBytes(IV);
        AES = new SecretKeySpec(aes, "AES");
        HMAC = new SecretKeySpec(hmac, "HmacSHA256");
    }

    private static byte[] roundTrip(byte[] payload) throws KeyException {
        byte[] enc = Helpers.encryptBytes(payload, AES, IV, HMAC);
        return Helpers.decryptBytes(enc, AES, HMAC);
    }

    @Test
    @DisplayName("roundtrip: empty payload")
    void empty() throws KeyException {
        assertArrayEquals(new byte[0], roundTrip(new byte[0]));
    }

    @Test
    @DisplayName("roundtrip: single byte")
    void single() throws KeyException {
        assertArrayEquals(new byte[]{0x7F}, roundTrip(new byte[]{0x7F}));
    }

    @Test
    @DisplayName("roundtrip: 240 KB random (voice-note sized)")
    void largeBlob() throws KeyException {
        byte[] payload = new byte[240_000];
        new Random(7).nextBytes(payload);
        assertArrayEquals(payload, roundTrip(payload));
    }

    @Test
    @DisplayName("byte path == string path modulo Base64 (same IV → identical crypto bytes)")
    void equivalenceWithStringPath() {
        String s = "VOICEMSG#dGVzdA==#AAECAwQF";
        byte[] payloadBytes = s.getBytes(StandardCharsets.UTF_8);

        byte[] encBytes = Helpers.encryptBytes(payloadBytes, AES, IV, HMAC);
        String encString = Helpers.encryptString(s, AES, IV, HMAC);

        assertEquals(encString, Base64.getEncoder().encodeToString(encBytes),
                "Base64 of encryptBytes must equal encryptString output");
    }

    @Test
    @DisplayName("decryptString can read what encryptBytes wrote (cross-path)")
    void crossPathDecrypt() throws KeyException {
        String s = "hello cross path ñéü";
        byte[] enc = Helpers.encryptBytes(s.getBytes(StandardCharsets.UTF_8), AES, IV, HMAC);
        String back = Helpers.decryptString(Base64.getEncoder().encodeToString(enc), AES, HMAC);
        assertEquals(s, back);
    }

    @Test
    @DisplayName("tampered ciphertext fails HMAC with KeyException")
    void badHmac() {
        byte[] enc = Helpers.encryptBytes(new byte[]{1, 2, 3, 4}, AES, IV, HMAC);
        enc[enc.length - 1] ^= 0x01; // flip a ciphertext bit
        assertThrows(KeyException.class, () -> Helpers.decryptBytes(enc, AES, HMAC));
    }

    @Test
    @DisplayName("tampered HMAC prefix fails with KeyException")
    void tamperedHmacPrefix() {
        byte[] enc = Helpers.encryptBytes(new byte[]{9, 9, 9}, AES, IV, HMAC);
        enc[0] ^= 0x01; // flip an HMAC bit
        assertThrows(KeyException.class, () -> Helpers.decryptBytes(enc, AES, HMAC));
    }

    @Test
    @DisplayName("null payload encrypts to null")
    void nullPayload() {
        assertNull(Helpers.encryptBytes(null, AES, IV, HMAC));
    }

    @Test
    @DisplayName("no-HMAC mode roundtrips")
    void noHmacMode() throws KeyException {
        byte[] payload = {10, 20, 30, 40, 50};
        byte[] enc = Helpers.encryptBytes(payload, AES, IV, null);
        assertArrayEquals(payload, Helpers.decryptBytes(enc, AES, null));
    }

    @Test
    @DisplayName("auto-IV overload roundtrips and varies ciphertext across calls")
    void autoIvOverload() throws KeyException {
        byte[] payload = "same plaintext".getBytes(StandardCharsets.UTF_8);
        byte[] a = Helpers.encryptBytes(payload, AES, HMAC);
        byte[] b = Helpers.encryptBytes(payload, AES, HMAC);
        assertArrayEquals(payload, Helpers.decryptBytes(a, AES, HMAC));
        assertArrayEquals(payload, Helpers.decryptBytes(b, AES, HMAC));
        // Random IV → different ciphertext bytes for the same plaintext.
        org.junit.jupiter.api.Assertions.assertFalse(java.util.Arrays.equals(a, b),
                "auto-IV must randomize ciphertext");
    }
}
