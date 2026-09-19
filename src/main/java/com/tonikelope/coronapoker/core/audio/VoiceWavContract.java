/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.audio;

/** Shared voice-note wire contract for Swing, GDX and the network boundary. */
public final class VoiceWavContract {

    public static final int MAX_FILE_BYTES = 320 * 1024;
    public static final int MIN_MILLIS = 100;
    public static final int MAX_SECONDS = 15;
    private static final int MIN_FILE_BYTES = 44;
    private static final int WAVE_FORMAT_MULAW = 0x0007;
    private static final long SAMPLE_RATE = 16_000L;
    private static final long MIN_DATA_BYTES = SAMPLE_RATE * MIN_MILLIS / 1000L;
    private static final long MAX_DATA_BYTES = SAMPLE_RATE * MAX_SECONDS;

    public static boolean isValid(byte[] wav) {
        return validationError(wav) == null;
    }

    public static String validationError(byte[] wav) {
        if (wav == null) return "null payload";
        if (wav.length < MIN_FILE_BYTES || wav.length > MAX_FILE_BYTES) {
            return "file size out of bounds: " + wav.length;
        }
        if (!ascii(wav, 0, "RIFF") || !ascii(wav, 8, "WAVE")) {
            return "missing RIFF/WAVE signature";
        }
        if (u32le(wav, 4) + 8L != wav.length) return "RIFF length mismatch";
        boolean formatSeen = false;
        boolean validFormat = false;
        long dataBytes = -1L;
        int position = 12;
        while (position < wav.length) {
            if (wav.length - position < 8) return "truncated chunk header";
            long chunkSize = u32le(wav, position + 4);
            long dataStart = position + 8L;
            long chunkEnd = dataStart + chunkSize;
            if (chunkEnd > wav.length) return "chunk exceeds RIFF payload";
            if (ascii(wav, position, "fmt ")) {
                if (formatSeen) return "multiple fmt chunks";
                if (chunkSize < 16L) return "short fmt chunk";
                formatSeen = true;
                validFormat = u16le(wav, (int) dataStart) == WAVE_FORMAT_MULAW
                        && u16le(wav, (int) dataStart + 2) == 1
                        && u32le(wav, (int) dataStart + 4) == SAMPLE_RATE
                        && u32le(wav, (int) dataStart + 8) == SAMPLE_RATE
                        && u16le(wav, (int) dataStart + 12) == 1
                        && u16le(wav, (int) dataStart + 14) == 8;
            } else if (ascii(wav, position, "data")) {
                if (dataBytes >= 0L) return "multiple data chunks";
                if (!formatSeen) return "data chunk precedes fmt chunk";
                dataBytes = chunkSize;
            }
            long paddedEnd = chunkEnd + (chunkSize & 1L);
            if (paddedEnd > wav.length) return "missing chunk padding";
            position = (int) paddedEnd;
        }
        if (!validFormat) return "unexpected voice format";
        if (dataBytes < MIN_DATA_BYTES || dataBytes > MAX_DATA_BYTES) {
            return "audio duration out of bounds: " + dataBytes + " data bytes";
        }
        return null;
    }

    private static boolean ascii(byte[] data, int offset, String expected) {
        if (offset < 0 || offset + expected.length() > data.length) return false;
        for (int index = 0; index < expected.length(); index++) {
            if ((data[offset + index] & 0xff) != expected.charAt(index)) return false;
        }
        return true;
    }

    private static int u16le(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
    }

    private static long u32le(byte[] data, int offset) {
        return (data[offset] & 0xffL)
                | ((data[offset + 1] & 0xffL) << 8)
                | ((data[offset + 2] & 0xffL) << 16)
                | ((data[offset + 3] & 0xffL) << 24);
    }

    private VoiceWavContract() {
    }
}
