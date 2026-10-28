package com.tonikelope.coronapoker.core.network;

import com.tonikelope.coronapoker.core.AvatarImageValidator;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class NetworkAvatarCapacityContractTest {

    @Test
    void encryptedJoinFrameCanCarryTheMaximumAvatar() {
        String avatar = Base64.getEncoder().encodeToString(
                new byte[AvatarImageValidator.MAX_FILE_BYTES]);
        String join = Base64.getEncoder().encodeToString(
                "Jugador1234567".getBytes(StandardCharsets.UTF_8))
                + "#26.71#" + avatar + "#JOIN#"
                + Base64.getEncoder().encodeToString(new byte[91]) + "#"
                + Base64.getEncoder().encodeToString(new byte[72]);
        SecretKeySpec aes = new SecretKeySpec(new byte[32], "AES");
        SecretKeySpec hmac = new SecretKeySpec(new byte[32], "HmacSHA256");

        String encrypted = SecureChannelCodec.encryptCommand(join, aes,
                new byte[SecureChannelCodec.IV_BYTES], hmac);

        assertTrue(encrypted.getBytes(StandardCharsets.ISO_8859_1).length
                <= NetworkLobbyGateway.MAX_HANDSHAKE_FRAME_BYTES);
    }
}
