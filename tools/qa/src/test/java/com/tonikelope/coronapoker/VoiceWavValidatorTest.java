package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VoiceWavValidatorTest {

    @Test
    void acceptsCurrentMulawVoiceFormat() {
        byte[] wav = mulawWav(1600);

        assertTrue(VoiceWavValidator.isValid(wav));
        assertNull(VoiceWavValidator.validationError(wav));
    }

    @Test
    void rejectsHeaderOnlyAndTooShortNotes() {
        assertFalse(VoiceWavValidator.isValid(mulawWav(0)));
        assertFalse(VoiceWavValidator.isValid(mulawWav(1599)));
    }

    @Test
    void rejectsUnexpectedAudioFormat() {
        byte[] wav = mulawWav(1600);
        wav[20] = 1; // PCM instead of G.711 mu-law

        assertFalse(VoiceWavValidator.isValid(wav));
    }

    @Test
    void rejectsTruncatedOrLengthMismatchedRiff() {
        byte[] wav = mulawWav(1600);
        byte[] truncated = java.util.Arrays.copyOf(wav, wav.length - 1);

        assertFalse(VoiceWavValidator.isValid(truncated));

        wav[4] = 0;
        assertFalse(VoiceWavValidator.isValid(wav));
    }

    static byte[] mulawWav(int data_bytes) {
        byte[] wav = new byte[44 + data_bytes];
        ascii(wav, 0, "RIFF");
        u32le(wav, 4, wav.length - 8);
        ascii(wav, 8, "WAVE");
        ascii(wav, 12, "fmt ");
        u32le(wav, 16, 16);
        u16le(wav, 20, 7);
        u16le(wav, 22, 1);
        u32le(wav, 24, 16000);
        u32le(wav, 28, 16000);
        u16le(wav, 32, 1);
        u16le(wav, 34, 8);
        ascii(wav, 36, "data");
        u32le(wav, 40, data_bytes);
        java.util.Arrays.fill(wav, 44, wav.length, (byte) 0xFF);
        return wav;
    }

    private static void ascii(byte[] target, int offset, String value) {
        for (int i = 0; i < value.length(); i++) {
            target[offset + i] = (byte) value.charAt(i);
        }
    }

    private static void u16le(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
    }

    private static void u32le(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
        target[offset + 2] = (byte) (value >>> 16);
        target[offset + 3] = (byte) (value >>> 24);
    }
}
