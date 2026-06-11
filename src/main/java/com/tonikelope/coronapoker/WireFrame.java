/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Transport frame codec that lets newline-delimited text commands and raw
 * binary payloads share a single socket stream without ambiguity.
 *
 * <p>The legacy wire is a text line: {@code *<base64(HMAC||IV||ciphertext)>\n}.
 * Those lines always start with a printable ASCII byte (the {@code '*'} encrypted
 * prefix, or an uppercase verb letter for the rare plaintext command), never with
 * {@code 0x00}. A binary frame is therefore introduced with a {@code 0x00}
 * sentinel followed by a big-endian {@code uint32} length and that many body
 * bytes:
 *
 * <pre>
 *   text line     : &lt;byte != 0x00&gt; ... up to and including '\n'
 *   binary frame  : 0x00 | uint32 len (big-endian) | len bytes
 * </pre>
 *
 * <p>{@link #read(InputStream, int)} peeks the first byte to dispatch: {@code 0x00}
 * reads a length-prefixed binary frame, anything else reads a line with the exact
 * semantics of {@link Helpers#readBoundedLine(java.io.BufferedReader, int)} (CR
 * skipped, LF terminates, {@code cap} bounds the accumulated length, clean EOF with
 * no bytes returns {@code null}). Text bodies are decoded ISO-8859-1, which is a
 * lossless byte→char map and identical to the legacy UTF-8/default decode for the
 * ASCII-only wire.
 *
 * <p>The {@code cap} bound is checked BEFORE allocating the binary body buffer, so a
 * forged huge length cannot trigger an OOM (parity with the line DoS guard).
 */
public final class WireFrame {

    /** First byte of a binary frame. Text lines never begin with this. */
    public static final int BINARY_SENTINEL = 0x00;

    private WireFrame() {
    }

    /** Discriminates the two wire forms a single read can return. */
    public enum Kind {
        TEXT, BINARY
    }

    /**
     * One decoded frame: either a text line ({@link #text()} set) or a binary
     * body ({@link #binary()} set). A {@code null} return from {@link #read}
     * signals clean EOF; a non-null Result is always one kind or the other.
     */
    public static final class Result {

        private final Kind kind;
        private final String text;
        private final byte[] binary;

        private Result(Kind kind, String text, byte[] binary) {
            this.kind = kind;
            this.text = text;
            this.binary = binary;
        }

        static Result text(String text) {
            return new Result(Kind.TEXT, text, null);
        }

        static Result binary(byte[] binary) {
            return new Result(Kind.BINARY, null, binary);
        }

        public Kind kind() {
            return kind;
        }

        public boolean isText() {
            return kind == Kind.TEXT;
        }

        public boolean isBinary() {
            return kind == Kind.BINARY;
        }

        /** The decoded line (no trailing CR/LF) when {@link #isText()}, else null. */
        public String text() {
            return text;
        }

        /** The raw body bytes when {@link #isBinary()}, else null. */
        public byte[] binary() {
            return binary;
        }
    }

    /**
     * Writes a binary frame ({@code 0x00 | uint32 len | body}) to {@code out} as a
     * single {@link OutputStream#write(byte[])} call followed by a flush. The single
     * write keeps the frame atomic relative to other writers; callers that share the
     * stream must still hold their usual per-stream write lock so a binary frame and
     * a text line never interleave.
     */
    public static void writeBinary(OutputStream out, byte[] body) throws IOException {
        if (body == null) {
            throw new IllegalArgumentException("body must not be null");
        }
        int len = body.length;
        byte[] framed = new byte[5 + len];
        framed[0] = (byte) BINARY_SENTINEL;
        framed[1] = (byte) (len >>> 24);
        framed[2] = (byte) (len >>> 16);
        framed[3] = (byte) (len >>> 8);
        framed[4] = (byte) len;
        System.arraycopy(body, 0, framed, 5, len);
        out.write(framed);
        out.flush();
    }

    /**
     * Reads the next frame from {@code in}. Returns a TEXT result for a legacy line,
     * a BINARY result for a {@code 0x00}-prefixed frame, or {@code null} on clean EOF
     * before any byte (matching {@link Helpers#readBoundedLine}'s null contract).
     *
     * @param cap maximum line length / binary body length in bytes; a binary length
     *            outside {@code [0, cap]} or an over-cap line throws {@link IOException}.
     */
    public static Result read(InputStream in, int cap) throws IOException {
        int first = in.read();
        if (first == -1) {
            return null;
        }
        if (first == BINARY_SENTINEL) {
            int len = readBigEndianInt(in);
            if (len < 0 || len > cap) {
                throw new IOException("Binary frame length " + len + " out of bounds [0," + cap + "] (DoS guard tripped)");
            }
            byte[] body = new byte[len];
            readFully(in, body);
            return Result.binary(body);
        }
        // Text line: 'first' is the first byte. Mirror readBoundedLine exactly —
        // LF terminates, CR is skipped, cap bounds the accumulated bytes, and an
        // EOF after at least one byte returns what was accumulated.
        ByteArrayOutputStream sb = new ByteArrayOutputStream(256);
        int c = first;
        while (c != -1) {
            if (c == '\n') {
                return Result.text(new String(sb.toByteArray(), StandardCharsets.ISO_8859_1));
            }
            if (c != '\r') {
                sb.write(c);
                if (sb.size() > cap) {
                    throw new IOException("Line exceeds " + cap + " char cap (DoS guard tripped)");
                }
            }
            c = in.read();
        }
        return Result.text(new String(sb.toByteArray(), StandardCharsets.ISO_8859_1));
    }

    private static int readBigEndianInt(InputStream in) throws IOException {
        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();
        int b4 = in.read();
        if ((b1 | b2 | b3 | b4) < 0) {
            throw new EOFException("Truncated binary frame length");
        }
        return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) {
                throw new EOFException("Truncated binary frame body: got " + off + " of " + buf.length + " bytes");
            }
            off += n;
        }
    }
}
