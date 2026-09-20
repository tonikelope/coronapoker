package com.tonikelope.coronapoker.gdx;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Decodes the two original About easter-egg images without Swing. */
final class GdxAboutEasterEgg {

    private GdxAboutEasterEgg() {
    }

    static byte[] decode(InputStream splash, InputStream encrypted)
            throws IOException, GeneralSecurityException {
        Objects.requireNonNull(splash, "splash");
        Objects.requireNonNull(encrypted, "encrypted");
        byte[] key = MessageDigest.getInstance("MD5")
                .digest(splash.readAllBytes());
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new IvParameterSpec(new byte[16]));
        return cipher.doFinal(encrypted.readAllBytes());
    }
}
