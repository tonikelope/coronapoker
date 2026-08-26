/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

/**
 * Strict validator for the one current voice-note format: RIFF/WAVE, G.711
 * mu-law, 16 kHz, 8-bit mono, from 100 ms through 15 seconds. Both the local
 * encoder and the network boundary use this contract so a non-empty header or
 * malformed payload can never become a clickable voice note.
 */
final class VoiceWavValidator {

    private static final int MIN_FILE_BYTES = 44;
    private static final int MAX_FILE_BYTES = 320 * 1024;
    private static final int WAVE_FORMAT_MULAW = 0x0007;
    private static final int CHANNELS = 1;
    private static final long SAMPLE_RATE = 16000L;
    private static final long BYTE_RATE = 16000L;
    private static final int BLOCK_ALIGN = 1;
    private static final int BITS_PER_SAMPLE = 8;
    private static final long MIN_DATA_BYTES = SAMPLE_RATE * VoiceRecorder.MIN_MILLIS / 1000L;
    private static final long MAX_DATA_BYTES = SAMPLE_RATE * VoiceRecorder.MAX_SECONDS;

    static boolean isValid(byte[] wav) {
        return validationError(wav) == null;
    }

    /**
     * @return {@code null} when valid; otherwise a stable diagnostic reason.
     */
    static String validationError(byte[] wav) {
        if (wav == null) {
            return "null payload";
        }
        if (wav.length < MIN_FILE_BYTES || wav.length > MAX_FILE_BYTES) {
            return "file size out of bounds: " + wav.length;
        }
        if (!ascii(wav, 0, "RIFF") || !ascii(wav, 8, "WAVE")) {
            return "missing RIFF/WAVE signature";
        }

        long riff_size = u32le(wav, 4);
        if (riff_size + 8L != wav.length) {
            return "RIFF length mismatch";
        }

        boolean format_seen = false;
        boolean valid_format = false;
        long data_bytes = -1L;
        int position = 12;

        while (position < wav.length) {
            if (wav.length - position < 8) {
                return "truncated chunk header";
            }

            long chunk_size = u32le(wav, position + 4);
            long chunk_data = position + 8L;
            long chunk_end = chunk_data + chunk_size;

            if (chunk_end > wav.length) {
                return "chunk exceeds RIFF payload";
            }

            if (ascii(wav, position, "fmt ")) {
                if (format_seen) {
                    return "multiple fmt chunks";
                }
                if (chunk_size < 16L) {
                    return "short fmt chunk";
                }
                format_seen = true;
                valid_format = u16le(wav, (int) chunk_data) == WAVE_FORMAT_MULAW
                        && u16le(wav, (int) chunk_data + 2) == CHANNELS
                        && u32le(wav, (int) chunk_data + 4) == SAMPLE_RATE
                        && u32le(wav, (int) chunk_data + 8) == BYTE_RATE
                        && u16le(wav, (int) chunk_data + 12) == BLOCK_ALIGN
                        && u16le(wav, (int) chunk_data + 14) == BITS_PER_SAMPLE;
            } else if (ascii(wav, position, "data")) {
                if (data_bytes >= 0L) {
                    return "multiple data chunks";
                }
                if (!format_seen) {
                    return "data chunk precedes fmt chunk";
                }
                data_bytes = chunk_size;
            }

            long padded_end = chunk_end + (chunk_size & 1L);
            if (padded_end > wav.length) {
                return "missing chunk padding";
            }
            position = (int) padded_end;
        }

        if (!valid_format) {
            return "unexpected voice format";
        }
        if (data_bytes < MIN_DATA_BYTES || data_bytes > MAX_DATA_BYTES) {
            return "audio duration out of bounds: " + data_bytes + " data bytes";
        }
        return null;
    }

    private static boolean ascii(byte[] data, int offset, String expected) {
        if (offset < 0 || offset + expected.length() > data.length) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if ((data[offset + i] & 0xFF) != expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int u16le(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static long u32le(byte[] data, int offset) {
        return (data[offset] & 0xFFL)
                | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16)
                | ((data[offset + 3] & 0xFFL) << 24);
    }

    private VoiceWavValidator() {
    }
}
