package com.tonikelope.coronapoker.core.network;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** UI-neutral text/binary framing used by every CoronaPoker frontend. */
public final class WireFrameCodec {
    public static final int BINARY_SENTINEL = 0;
    private WireFrameCodec() { }

    public enum Kind { TEXT, BINARY }

    public record Frame(Kind kind, String text, byte[] binary) {
        public Frame {
            if (kind == Kind.TEXT && (text == null || binary != null)) {
                throw new IllegalArgumentException("Text frame requires only text");
            }
            if (kind == Kind.BINARY && (text != null || binary == null)) {
                throw new IllegalArgumentException("Binary frame requires only bytes");
            }
            binary = binary == null ? null : Arrays.copyOf(binary, binary.length);
        }
        @Override public byte[] binary() {
            return binary == null ? null : Arrays.copyOf(binary, binary.length);
        }
        public boolean isText() { return kind == Kind.TEXT; }
        public boolean isBinary() { return kind == Kind.BINARY; }
    }

    public static void writeBinary(OutputStream output, byte[] body) throws IOException {
        if (body == null) throw new IllegalArgumentException("body must not be null");
        byte[] framed = new byte[5 + body.length];
        framed[0] = (byte) BINARY_SENTINEL;
        framed[1] = (byte) (body.length >>> 24);
        framed[2] = (byte) (body.length >>> 16);
        framed[3] = (byte) (body.length >>> 8);
        framed[4] = (byte) body.length;
        System.arraycopy(body, 0, framed, 5, body.length);
        output.write(framed);
        output.flush();
    }

    public static Frame read(InputStream input, int cap) throws IOException {
        if (cap < 0) throw new IllegalArgumentException("cap must not be negative");
        int first = input.read();
        if (first == -1) return null;
        if (first == BINARY_SENTINEL) {
            int length = readBigEndianInt(input);
            if (length < 0 || length > cap) {
                throw new IOException("Binary frame length " + length
                        + " out of bounds [0," + cap + "] (DoS guard tripped)");
            }
            byte[] body = new byte[length];
            readFully(input, body);
            return new Frame(Kind.BINARY, null, body);
        }
        ByteArrayOutputStream line = new ByteArrayOutputStream(256);
        int current = first;
        while (current != -1) {
            if (current == '\n') {
                return new Frame(Kind.TEXT, line.toString(StandardCharsets.ISO_8859_1), null);
            }
            if (current != '\r') {
                line.write(current);
                if (line.size() > cap) {
                    throw new IOException("Line exceeds " + cap + " char cap (DoS guard tripped)");
                }
            }
            current = input.read();
        }
        return new Frame(Kind.TEXT, line.toString(StandardCharsets.ISO_8859_1), null);
    }

    private static int readBigEndianInt(InputStream input) throws IOException {
        int b1 = input.read(), b2 = input.read(), b3 = input.read(), b4 = input.read();
        if ((b1 | b2 | b3 | b4) < 0) throw new EOFException("Truncated binary frame length");
        return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
    }

    private static void readFully(InputStream input, byte[] target) throws IOException {
        int offset = 0;
        while (offset < target.length) {
            int count = input.read(target, offset, target.length - offset);
            if (count < 0) {
                throw new EOFException("Truncated binary frame body: got "
                        + offset + " of " + target.length + " bytes");
            }
            offset += count;
        }
    }
}
