package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class GdxVolumeSoundCompatibilityTest {

    @Test
    void volumeFeedbackUsesTheCanonicalPcmWaveLayoutAcceptedByLibgdx()
            throws IOException {
        byte[] wav;
        try (InputStream input = getClass().getResourceAsStream(
                "/sounds/misc/volume_change.wav")) {
            assertNotNull(input, "volume feedback resource");
            wav = input.readAllBytes();
        }

        assertEquals("RIFF", ascii(wav, 0, 4));
        assertEquals("WAVE", ascii(wav, 8, 4));
        assertEquals("fmt ", ascii(wav, 12, 4));
        assertEquals(16, littleEndianInt(wav, 16),
                "extended metadata previously crashed libGDX's WAV reader");
        assertEquals(1, littleEndianShort(wav, 20), "PCM encoding");
        assertEquals("data", ascii(wav, 36, 4),
                "the PCM data chunk must follow the canonical fmt chunk");
        assertArrayEquals(new byte[]{0, 0, 0, 0},
                new byte[]{wav[44], wav[45], wav[46], wav[47]},
                "the normalized sound must retain its decoded PCM start");
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    private static int littleEndianShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return littleEndianShort(bytes, offset)
                | (littleEndianShort(bytes, offset + 2) << 16);
    }
}
