/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Canonical authenticated AES command encoder shared by both frontends. */
public final class AuthenticatedCommandCodec {

    private static final Logger LOGGER = Logger.getLogger(AuthenticatedCommandCodec.class.getName());

    private AuthenticatedCommandCodec() {
    }

    public static String encrypt(String command, SecretKeySpec aesKey, byte[] iv,
            SecretKeySpec hmacKey) {
        if (command == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(command.getBytes(StandardCharsets.UTF_8));
            byte[] ivAndCiphertext = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, ivAndCiphertext, 0, iv.length);
            System.arraycopy(encrypted, 0, ivAndCiphertext, iv.length, encrypted.length);

            byte[] frame = ivAndCiphertext;
            if (hmacKey != null) {
                Mac hmac = Mac.getInstance("HmacSHA256");
                hmac.init(hmacKey);
                byte[] signature = hmac.doFinal(ivAndCiphertext);
                frame = new byte[signature.length + ivAndCiphertext.length];
                System.arraycopy(signature, 0, frame, 0, signature.length);
                System.arraycopy(ivAndCiphertext, 0, frame, signature.length,
                        ivAndCiphertext.length);
            }
            return "*" + Base64.getEncoder().encodeToString(frame);
        } catch (GeneralSecurityException failure) {
            LOGGER.log(Level.SEVERE, "Could not encrypt game command", failure);
            return null;
        }
    }
}
