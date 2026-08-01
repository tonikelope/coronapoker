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
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    /**
     * Mirrors the production reader loop (Participant.readCommandFromClient and
     * NetClient.readCommand): binary frames are handled inline, a frame that fails the
     * channel is dropped and reading continues, and only a real EOF returns null.
     */
    private String readLikeProduction() throws Exception {
        while (true) {
            WireFrame.Result r = WireFrame.read(in, CAP);
            if (r == null) {
                return null;
            }
            if (r.isBinary()) {
                continue;
            }
            try {
                return Helpers.decryptCommand(r.text(), AES, HMAC);
            } catch (KeyException dropped) {
                continue;
            }
        }
    }

    /** Writes a raw line exactly as the plaintext keepalive senders do. */
    private void writeRaw(String line) throws Exception {
        OutputStream os = writerSide.getOutputStream();
        os.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        os.flush();
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

    @Test
    @DisplayName("an injected plaintext frame is rejected, never handed back as a command")
    void plaintextFrameRejected() throws Exception {
        // An on-path attacker injects an unencrypted line into the already established
        // socket. Every writer goes through encryptCommand, which always prepends '*', so
        // once the channel keys exist no legitimate frame is plaintext: it must be rejected
        // instead of being returned verbatim to the command dispatcher, which would bypass
        // both the AES layer and the HMAC without knowing any key.
        OutputStream os = writerSide.getOutputStream();
        os.write("GAME#1#ACTION#injected\n".getBytes(StandardCharsets.UTF_8));
        os.flush();

        WireFrame.Result r = WireFrame.read(in, CAP);
        assertTrue(r.isText(), "expected TEXT frame");
        assertThrows(KeyException.class, () -> Helpers.decryptCommand(r.text(), AES, HMAC),
                "an unauthenticated plaintext frame must never reach the dispatcher");
    }

    @Test
    @DisplayName("the plaintext keepalive (PING/PONG/PONG2) still goes through untouched")
    void plaintextKeepaliveStillAccepted() throws Exception {
        // The keepalive is written WITHOUT encryption on purpose: the transport writers
        // dump the raw string and the PING/PONG senders do not wrap it, unlike every game
        // command. Rejecting it would drop the connection on the first ping, so the reader
        // must let this closed set of verbs through exactly as it arrives.
        String[] control = {"PING#42", "PONG#43", "PONG2#44", "PING#-2147483648"};
        OutputStream os = writerSide.getOutputStream();
        for (String c : control) {
            os.write((c + "\n").getBytes(StandardCharsets.UTF_8));
        }
        os.flush();

        for (String c : control) {
            WireFrame.Result r = WireFrame.read(in, CAP);
            assertTrue(r.isText(), "expected TEXT frame");
            assertEquals(c, Helpers.decryptCommand(r.text(), AES, HMAC),
                    "the keepalive must survive the authenticated-channel check");
        }
    }

    @Test
    @DisplayName("a full session (plaintext keepalive + encrypted game traffic) survives end to end")
    void fullSessionSurvivesTheChannelCheck() throws Exception {
        // This is the automated stand-in for "connect two instances and leave them idle":
        // six PING cycles is thirty seconds of table time at the real 5 s interval. The
        // keepalive goes out in the clear and the game commands encrypted, interleaved
        // exactly as production writes them. If the channel check got this wrong, the
        // reader would return null here and a real session would drop into a reconnect
        // loop instead of playing.
        for (int i = 0; i < 6; i++) {
            writeRaw("PING#" + i);
            writeText("GAME#" + i + "#ACTION#x");
            writeRaw("PONG#" + (i + 1));
            writeRaw("PONG2#" + (i + 2));
        }

        for (int i = 0; i < 6; i++) {
            assertEquals("PING#" + i, readLikeProduction(), "keepalive PING lost on cycle " + i);
            assertEquals("GAME#" + i + "#ACTION#x", readLikeProduction(), "game command lost on cycle " + i);
            assertEquals("PONG#" + (i + 1), readLikeProduction(), "keepalive PONG lost on cycle " + i);
            assertEquals("PONG2#" + (i + 2), readLikeProduction(), "keepalive PONG2 lost on cycle " + i);
        }
    }

    @Test
    @DisplayName("an injected frame is dropped without breaking the session around it")
    void injectedFrameDoesNotBreakTheSession() throws Exception {
        // The whole point of dropping the frame instead of closing: an on-path attacker
        // injecting a line must lose that line, not the connection. The commands before
        // and after it have to arrive untouched and in order.
        writeText("GAME#1#ACTION#before");
        writeRaw("GAME#666#ACTION#injected");
        writeRaw("PING#7");
        writeText("GAME#2#ACTION#after");

        assertEquals("GAME#1#ACTION#before", readLikeProduction());
        // The injected line is swallowed by the reader, so the next thing that surfaces is
        // the legitimate keepalive, never the attacker's command.
        assertEquals("PING#7", readLikeProduction(), "the injected command must not surface");
        assertEquals("GAME#2#ACTION#after", readLikeProduction(), "the session must continue after the drop");
    }

    @Test
    @DisplayName("a command dressed up as keepalive is still rejected")
    void keepaliveLookalikesAreRejected() throws Exception {
        // The plaintext door is only for the exact verbs with an integer counter. Anything
        // that merely resembles them is an injection attempt.
        String[] bogus = {"PING", "PING#", "PING#1#GAME", "PINGS#1", "GAME#1#ACTION#x", "PONG3#1", "#1"};
        OutputStream os = writerSide.getOutputStream();
        for (String c : bogus) {
            os.write((c + "\n").getBytes(StandardCharsets.UTF_8));
        }
        os.flush();

        for (String c : bogus) {
            WireFrame.Result r = WireFrame.read(in, CAP);
            assertTrue(r.isText(), "expected TEXT frame");
            assertThrows(KeyException.class, () -> Helpers.decryptCommand(r.text(), AES, HMAC),
                    "must not accept a lookalike: " + c);
        }
    }

    @Test
    @DisplayName("a malformed encrypted ('*') frame is rejected with KeyException, never a RuntimeException")
    void malformedStarFrameThrowsKeyException() {
        // A '*' frame routes into decryptString. If its body is not valid Base64, or decodes
        // to fewer than HMAC(32)+IV(16) bytes, the decrypt used to escape as an
        // IllegalArgumentException / NegativeArraySizeException instead of KeyException. The
        // reader only treats KeyException as "drop this frame"; anything else falls through to
        // its EOF path and tears the connection down, so these must surface as KeyException.
        String[] malformed = {
            "*",              // empty body -> 0 bytes < HMAC+IV
            "*AA==",          // valid Base64 but 1 byte < HMAC+IV
            "*@@@@",          // characters outside the Base64 alphabet
            "*ABC",           // length not a multiple of 4, no padding
        };
        for (String frame : malformed) {
            assertThrows(KeyException.class, () -> Helpers.decryptCommand(frame, AES, HMAC),
                    "malformed '*' frame must be a droppable KeyException: " + frame);
        }
    }

    @Test
    @DisplayName("an injected malformed '*' frame is dropped without breaking the session")
    void injectedMalformedStarFrameDoesNotBreakTheSession() throws Exception {
        // The on-path attacker the release cites: inject a single crafted '*' frame. It must
        // cost that frame, not the connection. The legitimate commands around it have to arrive
        // untouched and in order, read through the exact production loop (readLikeProduction),
        // which only swallows KeyException — a RuntimeException here would break the read.
        writeText("GAME#1#ACTION#before");
        writeRaw("*@@@notbase64");   // '*' frame with a non-Base64 body
        writeRaw("*AA==");           // '*' frame whose body decodes shorter than HMAC+IV
        writeText("GAME#2#ACTION#after");

        assertEquals("GAME#1#ACTION#before", readLikeProduction());
        assertEquals("GAME#2#ACTION#after", readLikeProduction(),
                "the session must continue after dropping the malformed frames");
    }
}
