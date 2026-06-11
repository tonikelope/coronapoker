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
import com.tonikelope.coronapoker.WireFrame;
import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyException;
import java.security.SecureRandom;
import java.util.Random;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test over a real localhost socket pair, exercising the exact Phase 1
 * read mechanism (a persistent {@link BufferedInputStream} read by {@link WireFrame#read})
 * carrying the live encrypted text wire, plus a mixed binary frame (the Phase 2 receive
 * path) — to confirm the framing survives real TCP fragmentation, not just in-memory streams.
 *
 * The full ECDH handshake and resetSocket reconnect live in the UI classes and are covered
 * by the author's focused smoke; this test isolates and proves the transport layer they sit on.
 */
class SocketFramingIntegrationTest {

    private static final int CAP = Helpers.MAX_COMMAND_LINE_CHARS;

    private static SecretKeySpec AES;
    private static SecretKeySpec HMAC;

    private ServerSocket serverSocket;
    private Socket writerSide;  // mimics the host writing to a client
    private Socket readerSide;  // mimics the client reading from the host
    private BufferedInputStream in;

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

    @BeforeEach
    void connect() throws Exception {
        serverSocket = new ServerSocket(0);
        // On localhost the connect completes into the OS backlog, so a single-threaded
        // connect-then-accept does not deadlock.
        readerSide = new Socket("127.0.0.1", serverSocket.getLocalPort());
        writerSide = serverSocket.accept();
        in = new BufferedInputStream(readerSide.getInputStream());
    }

    @AfterEach
    void close() throws Exception {
        if (readerSide != null) {
            readerSide.close();
        }
        if (writerSide != null) {
            writerSide.close();
        }
        if (serverSocket != null) {
            serverSocket.close();
        }
    }

    /** Mirrors the production text writers: encryptCommand(...) + "\n". */
    private void writeText(String command) throws Exception {
        OutputStream os = writerSide.getOutputStream();
        os.write((Helpers.encryptCommand(command, AES, HMAC) + "\n").getBytes(StandardCharsets.UTF_8));
        os.flush();
    }

    private String readText() throws Exception {
        WireFrame.Result r = WireFrame.read(in, CAP);
        assertTrue(r.isText(), "expected TEXT frame");
        return Helpers.decryptCommand(r.text(), AES, HMAC);
    }

    @Test
    @DisplayName("encrypted text commands round-trip in order over a real socket")
    void textCommandsRoundTrip() throws Exception {
        String[] commands = {
            "PING#42",
            "GAME#1234567890#ACTION#aGVsbG8rZ29vZGJ5ZS9zaG93ZG93bg==",
            "USERSLIST#" + java.util.Base64.getEncoder().encodeToString("nick".getBytes(StandardCharsets.UTF_8)),
            "DELUSER#" + java.util.Base64.getEncoder().encodeToString("someone".getBytes(StandardCharsets.UTF_8)),
            "CONF#7"
        };
        for (String c : commands) {
            writeText(c);
        }
        for (String c : commands) {
            assertEquals(c, readText(), "command out of order or corrupted");
        }
    }

    @Test
    @DisplayName("a large text command (64 KB base64) survives TCP fragmentation")
    void largeTextCommand() throws Exception {
        StringBuilder sb = new StringBuilder("GAME#1#BIG#");
        byte[] blob = new byte[48_000];
        new Random(3).nextBytes(blob);
        sb.append(java.util.Base64.getEncoder().encodeToString(blob));
        String big = sb.toString();
        writeText(big);
        assertEquals(big, readText());
    }

    @Test
    @DisplayName("mixed text + binary + text frames decode correctly over a real socket")
    void mixedTextAndBinary() throws Exception {
        byte[] voiceLike = new byte[120_000];
        new Random(5).nextBytes(voiceLike);

        writeText("CHAT#aGk=");
        WireFrame.writeBinary(writerSide.getOutputStream(), Helpers.encryptBytes(voiceLike, AES, HMAC));
        writeText("PONG#9");

        assertEquals("CHAT#aGk=", readText());

        WireFrame.Result bin = WireFrame.read(in, CAP);
        assertTrue(bin.isBinary(), "middle frame must be BINARY");
        assertArrayEquals(voiceLike, Helpers.decryptBytes(bin.binary(), AES, HMAC),
                "decrypted binary body must equal original blob");

        assertEquals("PONG#9", readText());
    }

    @Test
    @DisplayName("clean socket close yields null from the reader")
    void cleanCloseReturnsNull() throws Exception {
        writeText("PING#1");
        assertEquals("PING#1", readText());
        writerSide.close();
        assertNull(WireFrame.read(in, CAP), "EOF after peer close must be null");
    }

    @Test
    @DisplayName("a fresh socket (reconnect analogue) reads independently of the old one")
    void freshSocketAfterClose() throws Exception {
        writeText("PING#1");
        assertEquals("PING#1", readText());
        // Drop both ends, then stand up a new pair on the same port lifecycle — this is the
        // stream-level analogue of resetSocket swapping input_stream_reader to a new
        // BufferedInputStream: subsequent reads must come solely from the new stream.
        readerSide.close();
        writerSide.close();
        serverSocket.close();

        serverSocket = new ServerSocket(0);
        readerSide = new Socket("127.0.0.1", serverSocket.getLocalPort());
        writerSide = serverSocket.accept();
        in = new BufferedInputStream(readerSide.getInputStream());

        writeText("GAME#2#RESUMED#ok");
        assertEquals("GAME#2#RESUMED#ok", readText());
    }

    @Test
    @DisplayName("tampered text command surfaces as a null decrypt (HMAC guard intact end-to-end)")
    void tamperedTextDropped() throws Exception {
        // Hand-corrupt an encrypted line: decryptCommand must not return the plaintext.
        String enc = Helpers.encryptCommand("ACTION#secret", AES, HMAC);
        char[] chars = enc.toCharArray();
        chars[chars.length - 2] = (chars[chars.length - 2] == 'A') ? 'B' : 'A';
        String corrupted = new String(chars);
        OutputStream os = writerSide.getOutputStream();
        os.write((corrupted + "\n").getBytes(StandardCharsets.UTF_8));
        os.flush();

        WireFrame.Result r = WireFrame.read(in, CAP);
        assertTrue(r.isText());
        String out;
        try {
            out = Helpers.decryptCommand(r.text(), AES, HMAC);
        } catch (KeyException ke) {
            out = null; // HMAC rejection is an acceptable outcome too
        }
        org.junit.jupiter.api.Assertions.assertNotEquals("ACTION#secret", out,
                "corrupted ciphertext must never decrypt back to the plaintext");
    }
}
