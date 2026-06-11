/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.net;

import com.tonikelope.coronapoker.WireFrame;
import com.tonikelope.coronapoker.Helpers;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AAA tests for {@link WireFrame}: the binary frame codec and the combined
 * text-line / binary-frame reader that lets both forms share one socket stream.
 *
 * Covers: binary roundtrip across edge sizes and adversarial byte content,
 * length-cap / truncation / negative-length DoS guards, back-to-back and mixed
 * frames, read fragmentation (one byte per read), atomicity under concurrent
 * writers, and byte-for-byte equivalence of the text branch with the legacy
 * {@link Helpers#readBoundedLine}.
 */
class WireFrameTest {

    private static final int CAP = Helpers.MAX_COMMAND_LINE_CHARS;

    // ---- binary roundtrip ----

    private static byte[] roundTripBinary(byte[] body) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WireFrame.writeBinary(out, body);
        WireFrame.Result r = WireFrame.read(new ByteArrayInputStream(out.toByteArray()), CAP);
        assertTrue(r.isBinary(), "must decode as BINARY");
        return r.binary();
    }

    @Test
    @DisplayName("binary roundtrip: empty body")
    void binaryEmpty() throws IOException {
        assertArrayEquals(new byte[0], roundTripBinary(new byte[0]));
    }

    @Test
    @DisplayName("binary roundtrip: single byte")
    void binarySingle() throws IOException {
        assertArrayEquals(new byte[]{0x42}, roundTripBinary(new byte[]{0x42}));
    }

    @Test
    @DisplayName("binary roundtrip: body full of control/newline/sentinel bytes")
    void binaryAdversarialBytes() throws IOException {
        byte[] body = {0x00, 0x0A, 0x0D, (byte) 0xFF, 0x00, 0x2A /* '*' */, 0x23 /* '#' */, 0x0A};
        assertArrayEquals(body, roundTripBinary(body));
    }

    @Test
    @DisplayName("binary roundtrip: 240 KB random (voice-note sized)")
    void binaryLargeBlob() throws IOException {
        byte[] body = new byte[240_000];
        new Random(1234).nextBytes(body);
        assertArrayEquals(body, roundTripBinary(body));
    }

    // ---- DoS / truncation guards ----

    @Test
    @DisplayName("declared length over cap is rejected before allocation")
    void lengthOverCapRejected() {
        // 0x00 sentinel + big-endian length way over a tiny cap, no body bytes.
        byte[] framed = {0x00, 0x00, 0x10, 0x00, 0x00}; // len = 0x00100000 = 1 MiB
        IOException ex = assertThrows(IOException.class,
                () -> WireFrame.read(new ByteArrayInputStream(framed), 1024));
        assertTrue(ex.getMessage().contains("DoS guard"), ex.getMessage());
    }

    @Test
    @DisplayName("negative declared length (high bit set) is rejected")
    void negativeLengthRejected() {
        byte[] framed = {0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        IOException ex = assertThrows(IOException.class,
                () -> WireFrame.read(new ByteArrayInputStream(framed), CAP));
        assertTrue(ex.getMessage().contains("DoS guard"), ex.getMessage());
    }

    @Test
    @DisplayName("truncated length field throws")
    void truncatedLength() {
        byte[] framed = {0x00, 0x00, 0x10}; // only 2 of 4 length bytes
        assertThrows(IOException.class,
                () -> WireFrame.read(new ByteArrayInputStream(framed), CAP));
    }

    @Test
    @DisplayName("truncated body throws")
    void truncatedBody() {
        // len says 10 but only 5 body bytes follow
        byte[] framed = {0x00, 0x00, 0x00, 0x00, 0x0A, 1, 2, 3, 4, 5};
        assertThrows(IOException.class,
                () -> WireFrame.read(new ByteArrayInputStream(framed), CAP));
    }

    // ---- back-to-back and mixed framing ----

    @Test
    @DisplayName("two binary frames back to back read independently")
    void twoBinaryFrames() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WireFrame.writeBinary(out, new byte[]{1, 2, 3});
        WireFrame.writeBinary(out, new byte[]{4, 5, 6, 7});
        InputStream in = new ByteArrayInputStream(out.toByteArray());
        assertArrayEquals(new byte[]{1, 2, 3}, WireFrame.read(in, CAP).binary());
        assertArrayEquals(new byte[]{4, 5, 6, 7}, WireFrame.read(in, CAP).binary());
        assertNull(WireFrame.read(in, CAP), "clean EOF after last frame");
    }

    @Test
    @DisplayName("binary, then text line, then binary — all decode in order")
    void mixedFraming() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WireFrame.writeBinary(out, new byte[]{(byte) 0xAB, (byte) 0xCD});
        out.write("*SGVsbG8=\n".getBytes(StandardCharsets.ISO_8859_1));
        WireFrame.writeBinary(out, new byte[]{(byte) 0xEF});
        InputStream in = new ByteArrayInputStream(out.toByteArray());

        WireFrame.Result a = WireFrame.read(in, CAP);
        assertTrue(a.isBinary());
        assertArrayEquals(new byte[]{(byte) 0xAB, (byte) 0xCD}, a.binary());

        WireFrame.Result b = WireFrame.read(in, CAP);
        assertTrue(b.isText());
        assertEquals("*SGVsbG8=", b.text());

        WireFrame.Result c = WireFrame.read(in, CAP);
        assertTrue(c.isBinary());
        assertArrayEquals(new byte[]{(byte) 0xEF}, c.binary());
    }

    // ---- fragmentation ----

    /** Returns exactly one byte per read() / read(b,off,len) call to force reassembly loops. */
    private static final class DripInputStream extends InputStream {

        private final byte[] data;
        private int pos;

        DripInputStream(byte[] data) {
            this.data = data;
        }

        @Override
        public int read() {
            return pos < data.length ? (data[pos++] & 0xFF) : -1;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (pos >= data.length) {
                return -1;
            }
            b[off] = data[pos++];
            return 1;
        }
    }

    @Test
    @DisplayName("binary frame reassembles when the stream drips one byte per read")
    void fragmentedBinary() throws IOException {
        byte[] body = new byte[5000];
        new Random(99).nextBytes(body);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WireFrame.writeBinary(out, body);
        WireFrame.Result r = WireFrame.read(new DripInputStream(out.toByteArray()), CAP);
        assertArrayEquals(body, r.binary());
    }

    // ---- concurrent writers (atomicity) ----

    @Test
    @DisplayName("concurrent writers to a synchronized stream produce no interleaved frames")
    void concurrentWritersAtomic() throws Exception {
        final int writers = 8;
        final int perWriter = 50;
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Thread> threads = new ArrayList<>();
        for (int w = 0; w < writers; w++) {
            final byte tag = (byte) w;
            Thread t = new Thread(() -> {
                for (int i = 0; i < perWriter; i++) {
                    byte[] body = new byte[64];
                    for (int j = 0; j < body.length; j++) {
                        body[j] = tag;
                    }
                    try {
                        // Callers hold the per-stream write lock; mirror that here.
                        synchronized (out) {
                            WireFrame.writeBinary(out, body);
                        }
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            });
            threads.add(t);
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        // Every frame must be a clean 64-byte run of a single tag.
        InputStream in = new ByteArrayInputStream(out.toByteArray());
        int[] counts = new int[writers];
        WireFrame.Result r;
        while ((r = WireFrame.read(in, CAP)) != null) {
            byte[] body = r.binary();
            assertEquals(64, body.length, "frame body length intact");
            byte tag = body[0];
            for (byte b : body) {
                assertEquals(tag, b, "frame body not interleaved");
            }
            counts[tag & 0xFF]++;
        }
        for (int w = 0; w < writers; w++) {
            assertEquals(perWriter, counts[w], "all frames of writer " + w + " present");
        }
    }

    // ---- text branch equivalence with readBoundedLine ----

    private static String legacyLine(String wire, int cap) throws IOException {
        BufferedReader r = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(wire.getBytes(StandardCharsets.ISO_8859_1)),
                        StandardCharsets.ISO_8859_1));
        return Helpers.readBoundedLine(r, cap);
    }

    private static String frameLine(String wire, int cap) throws IOException {
        WireFrame.Result r = WireFrame.read(
                new ByteArrayInputStream(wire.getBytes(StandardCharsets.ISO_8859_1)), cap);
        return r == null ? null : r.text();
    }

    @Test
    @DisplayName("text branch matches readBoundedLine byte-for-byte across the wire corpus")
    void textEquivalence() throws IOException {
        String[] corpus = {
            "hola mundo\n",
            "hola\r\n",
            "",
            "foo",
            "*aGVsbG8rZ29vZGJ5ZS9zaG93ZG93bg==\n",
            "GAME#1234567890#ACTION#aGVsbG8=\r\n",
            "trailing-no-newline-content",
            "\n",
            "a\n"
        };
        for (String wire : corpus) {
            assertEquals(legacyLine(wire, CAP), frameLine(wire, CAP),
                    "mismatch for corpus entry: " + wire.replace("\n", "\\n").replace("\r", "\\r"));
        }
    }

    @Test
    @DisplayName("empty stream returns null in both readers")
    void textEmptyNull() throws IOException {
        assertNull(legacyLine("", CAP));
        assertNull(frameLine("", CAP));
    }

    @Test
    @DisplayName("over-cap line throws in both readers")
    void textOverCapBothThrow() {
        String wire = "AAAAAA\n"; // 6 chars before \n
        assertThrows(IOException.class, () -> legacyLine(wire, 4));
        assertThrows(IOException.class, () -> frameLine(wire, 4));
    }

    @Test
    @DisplayName("line of exactly cap chars passes in both readers")
    void textExactCapBoth() throws IOException {
        assertEquals("ABCD", legacyLine("ABCD\n", 4));
        assertEquals("ABCD", frameLine("ABCD\n", 4));
    }

    @Test
    @DisplayName("multiple text lines read sequentially match between readers")
    void textMultiLineSequential() throws IOException {
        String wire = "*line1==\n*line2==\nPING#3\n";
        InputStream legacyIn = new ByteArrayInputStream(wire.getBytes(StandardCharsets.ISO_8859_1));
        BufferedReader legacy = new BufferedReader(new InputStreamReader(legacyIn, StandardCharsets.ISO_8859_1));
        InputStream frameIn = new ByteArrayInputStream(wire.getBytes(StandardCharsets.ISO_8859_1));
        for (int i = 0; i < 3; i++) {
            String a = Helpers.readBoundedLine(legacy, CAP);
            WireFrame.Result b = WireFrame.read(frameIn, CAP);
            assertEquals(a, b.text(), "line " + i);
            assertTrue(b.isText());
        }
        assertNull(WireFrame.read(frameIn, CAP), "EOF after 3 lines");
    }

    @Test
    @DisplayName("a real binary body containing 0x0A is NOT split as a line")
    void binaryNotSplitByNewline() throws IOException {
        // Body is all 0x0A — a line reader would have stopped at the first one.
        byte[] body = new byte[100];
        java.util.Arrays.fill(body, (byte) 0x0A);
        byte[] got = roundTripBinary(body);
        assertEquals(100, got.length, "full body survived despite embedded newlines");
        for (byte b : got) {
            assertEquals(0x0A, b);
        }
    }
}
