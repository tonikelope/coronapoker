/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/** Stateless verification helpers needed by compatibility entry points. */
public final class GameIdentityVerifier {

    private static final byte[] ED25519_X509_HEADER = {
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };
    private static final byte[] ACTION_DOMAIN =
            "ACTION\0".getBytes(StandardCharsets.UTF_8);

    private GameIdentityVerifier() { }

    public static boolean verifyAction(byte[] publicKey, byte[] record, byte[] signature) {
        if (publicKey == null || publicKey.length != 32 || record == null
                || signature == null || signature.length != 64) {
            return false;
        }
        try {
            byte[] encoded = new byte[ED25519_X509_HEADER.length + publicKey.length];
            System.arraycopy(ED25519_X509_HEADER, 0, encoded, 0,
                    ED25519_X509_HEADER.length);
            System.arraycopy(publicKey, 0, encoded, ED25519_X509_HEADER.length,
                    publicKey.length);
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(
                    new X509EncodedKeySpec(encoded)));
            verifier.update(ACTION_DOMAIN);
            verifier.update(record);
            return verifier.verify(signature);
        } catch (Exception invalid) {
            return false;
        }
    }
}
