/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.net;

import com.tonikelope.coronapoker.BinaryWire;
import com.tonikelope.coronapoker.Helpers;
import com.tonikelope.coronapoker.WireFrame;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyException;
import java.security.SecureRandom;
import java.util.Random;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AAA tests for {@link BinaryWire} (the voice/avatar binary payload layout) and the full
 * voice wire pipeline: encodeVoice → encryptBytes → writeBinary → read → decryptBytes →
 * decode, which is exactly what the send/receive sites do for a note. Includes a size
 * assertion proving the binary path is ~240 KB, not the ~427 KB of the double-Base64 path.
 */
class BinaryVoiceWireTest {

    private static final int CAP = Helpers.MAX_COMMAND_LINE_CHARS;

    private static SecretKeySpec AES;
    private static SecretKeySpec HMAC;

    @BeforeAll
    static void keys() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
        SecureRandom rnd = new SecureRandom();
        byte[] aes = new byte[32];
        byte[] hmac = new byte[32];
        rnd.nextBytes(aes);
        rnd.nextBytes(hmac);
        AES = new SecretKeySpec(aes, "AES");
        HMAC = new SecretKeySpec(hmac, "HmacSHA256");
    }

    @Test
    @DisplayName("encodeVoice/decode round-trips nick and audio")
    void voiceRoundTrip() {
        byte[] audio = new byte[5000];
        new Random(1).nextBytes(audio);
        BinaryWire.Decoded d = BinaryWire.decode(BinaryWire.encodeVoice("alice", audio));
        assertEquals(BinaryWire.TYPE_VOICE, d.type);
        assertEquals("alice", d.nick);
        assertArrayEquals(audio, d.payload);
    }

    @Test
    @DisplayName("unicode nick and empty audio survive the codec")
    void unicodeNickEmptyAudio() {
        BinaryWire.Decoded d = BinaryWire.decode(BinaryWire.encodeVoice("ñoño☺", new byte[0]));
        assertEquals("ñoño☺", d.nick);
        assertEquals(0, d.payload.length);
    }

    @Test
    @DisplayName("empty nick survives the codec")
    void emptyNick() {
        BinaryWire.Decoded d = BinaryWire.decode(BinaryWire.encodeVoice("", new byte[]{1, 2, 3}));
        assertEquals("", d.nick);
        assertArrayEquals(new byte[]{1, 2, 3}, d.payload);
    }

    @Test
    @DisplayName("decode rejects a too-short buffer")
    void decodeTooShort() {
        assertThrows(IllegalArgumentException.class, () -> BinaryWire.decode(new byte[]{'V', 0}));
    }

    @Test
    @DisplayName("decode rejects an out-of-bounds nick length")
    void decodeNicklenOutOfBounds() {
        // type 'V', nicklen = 0xFFFF but no nick bytes follow
        byte[] bad = {'V', (byte) 0xFF, (byte) 0xFF};
        assertThrows(IllegalArgumentException.class, () -> BinaryWire.decode(bad));
    }

    @Test
    @DisplayName("full voice pipeline over a frame: encode→encrypt→frame→read→decrypt→decode")
    void fullVoicePipeline() throws KeyException {
        // 15 s of µ-law audio at 16 kHz, 8-bit mono ≈ 240 KB.
        byte[] audio = new byte[240_000];
        new Random(42).nextBytes(audio);

        byte[] frameBody = Helpers.encryptBytes(BinaryWire.encodeVoice("bob", audio), AES, HMAC);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            WireFrame.writeBinary(out, frameBody);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        byte[] onWire = out.toByteArray();

        // The whole wire frame must be near the raw audio size, not the ~427 KB the
        // double-Base64 VOICEMSG text command produced for the same note.
        assertEquals(true, onWire.length < 260_000,
                "binary frame on the wire (" + onWire.length + " B) must be ~240 KB, not ~427 KB");

        WireFrame.Result r;
        try {
            r = WireFrame.read(new ByteArrayInputStream(onWire), CAP);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        org.junit.jupiter.api.Assertions.assertTrue(r.isBinary());

        byte[] payload = Helpers.decryptBytes(r.binary(), AES, HMAC);
        BinaryWire.Decoded d = BinaryWire.decode(payload);
        assertEquals(BinaryWire.TYPE_VOICE, d.type);
        assertEquals("bob", d.nick);
        assertArrayEquals(audio, d.payload);
    }
}
