package com.tonikelope.coronapoker.core.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.KeyException;
import java.util.Arrays;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class SecureChannelCodecTest {

    private static final SecretKeySpec AES = new SecretKeySpec(sequence(32, 1), "AES");
    private static final SecretKeySpec HMAC = new SecretKeySpec(sequence(32, 41), "HmacSHA256");
    private static final byte[] IV = sequence(16, 81);

    @Test
    void preservesTheExistingDeterministicTextWireAndRoundTripsBinary() throws Exception {
        String frame = SecureChannelCodec.encryptCommand("GAME#7#HELLO", AES, IV, HMAC);
        assertEquals("*DL8FGDhSgvWCDXw1B/QTOUiT4gmRg+cMZglE8FbXMLZRUlNUVVZXWFlaW1xdXl9gJUu85l8Tj3y5LIDfs8Xv6w==", frame);
        assertEquals("GAME#7#HELLO", SecureChannelCodec.decryptCommand(frame, AES, HMAC));

        byte[] payload = "voz\u0000binaria".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = SecureChannelCodec.encryptBytes(payload, AES, IV, HMAC);
        assertArrayEquals(payload, SecureChannelCodec.decryptBytes(encrypted, AES, HMAC));
    }

    @Test
    void acceptsOnlyTheHistoricalPlaintextHeartbeatFrames() throws Exception {
        assertEquals("PING#-2147483648", SecureChannelCodec.decryptCommand("PING#-2147483648", AES, HMAC));
        assertThrows(KeyException.class,
                () -> SecureChannelCodec.decryptCommand("GAME#1#INJECTED", AES, HMAC));
        assertThrows(KeyException.class,
                () -> SecureChannelCodec.decryptCommand("PING#1#EXTRA", AES, HMAC));
    }

    @Test
    void rejectsTamperingAndOvershortFrames() {
        byte[] encrypted = SecureChannelCodec.encryptBytes(new byte[]{1, 2, 3}, AES, IV, HMAC);
        encrypted[encrypted.length - 1] ^= 1;
        assertThrows(KeyException.class, () -> SecureChannelCodec.decryptBytes(encrypted, AES, HMAC));
        assertThrows(KeyException.class, () -> SecureChannelCodec.decryptBytes(new byte[47], AES, HMAC));
    }

    @Test
    void passwordDerivationIsStableAndChangesWithPassword() {
        byte[] secret = sequence(32, 5);
        assertEquals(64, SecureChannelCodec.deriveChannelSecret(secret, "").length);
        byte[] protectedSecret = SecureChannelCodec.deriveChannelSecret(secret, "clave");
        assertEquals(64, protectedSecret.length);
        org.junit.jupiter.api.Assertions.assertFalse(Arrays.equals(
                SecureChannelCodec.deriveChannelSecret(secret, ""), protectedSecret));
    }

    private static byte[] sequence(int length, int start) {
        byte[] value = new byte[length];
        for (int i = 0; i < value.length; i++) {
            value[i] = (byte) (start + i);
        }
        return value;
    }
}
