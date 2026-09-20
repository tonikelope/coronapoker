package com.tonikelope.coronapoker.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class IdenticonFingerprintTest {

    @Test void isDeterministicSymmetricAndFormatsTheSwingFingerprint() {
        IdenticonFingerprint fingerprint = IdenticonFingerprint.fromSeed(
                "canal-coronapoker".getBytes(StandardCharsets.UTF_8));
        IdenticonFingerprint restored = IdenticonFingerprint.fromDigest(
                fingerprint.digest());

        assertArrayEquals(fingerprint.digest(), restored.digest());
        assertEquals(39, fingerprint.formatted().length());
        for (int row = 0; row < IdenticonFingerprint.GRID_SIZE; row++) {
            for (int column = 0; column < IdenticonFingerprint.GRID_SIZE; column++) {
                assertEquals(fingerprint.filled(column, row),
                        fingerprint.filled(6 - column, row));
            }
        }
        byte[] returned = fingerprint.digest();
        returned[0] ^= 0x7f;
        assertFalse(returned[0] == fingerprint.digest()[0]);
        assertThrows(IllegalArgumentException.class,
                () -> IdenticonFingerprint.fromDigest(new byte[16]));
    }
}
