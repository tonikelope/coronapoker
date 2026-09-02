package com.tonikelope.coronapoker.core.network;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * UI-neutral implementation of CoronaPoker's existing encrypted wire format.
 *
 * <p>The format is deliberately unchanged: {@code HMAC-SHA256(IV||ciphertext)
 * || IV || AES-CBC-PKCS5(ciphertext)}. Text commands prefix the Base64 body
 * with {@code *}; only the three historical keepalive verbs may be plaintext.
 */
public final class SecureChannelCodec {

    public static final int IV_BYTES = 16;
    public static final int HMAC_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureChannelCodec() {
    }

    public static byte[] deriveChannelSecret(byte[] sharedSecret, String password) {
        try {
            if (password != null && !password.isEmpty()) {
                Mac mac = Mac.getInstance("HmacSHA512");
                mac.init(new SecretKeySpec(password.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
                return mac.doFinal(sharedSecret);
            }
            return MessageDigest.getInstance("SHA-512").digest(sharedSecret);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Channel secret derivation failed", failure);
        }
    }

    public static String encryptText(String text, SecretKeySpec aesKey,
            SecretKeySpec hmacKey) {
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        return encryptText(text, aesKey, iv, hmacKey);
    }

    public static String encryptText(String text, SecretKeySpec aesKey,
            byte[] iv, SecretKeySpec hmacKey) {
        if (text == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(
                encryptBytes(text.getBytes(StandardCharsets.UTF_8), aesKey, iv, hmacKey));
    }

    public static String decryptText(String encoded, SecretKeySpec aesKey,
            SecretKeySpec hmacKey) throws KeyException {
        if (encoded == null) {
            return null;
        }
        final byte[] body;
        try {
            body = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException failure) {
            throw new KeyException("Undecodable frame body");
        }
        byte[] clear = decryptBytes(body, aesKey, hmacKey);
        return clear == null ? null : new String(clear, StandardCharsets.UTF_8);
    }

    public static byte[] encryptBytes(byte[] payload, SecretKeySpec aesKey,
            SecretKeySpec hmacKey) {
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        return encryptBytes(payload, aesKey, iv, hmacKey);
    }

    public static byte[] encryptBytes(byte[] payload, SecretKeySpec aesKey,
            byte[] iv, SecretKeySpec hmacKey) {
        if (payload == null) {
            return null;
        }
        if (iv == null || iv.length != IV_BYTES) {
            throw new IllegalArgumentException("AES-CBC IV must contain 16 bytes");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(payload);
            byte[] ivAndCiphertext = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, ivAndCiphertext, 0, iv.length);
            System.arraycopy(encrypted, 0, ivAndCiphertext, iv.length, encrypted.length);
            if (hmacKey == null) {
                return ivAndCiphertext;
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            byte[] hmac = mac.doFinal(ivAndCiphertext);
            byte[] result = new byte[hmac.length + ivAndCiphertext.length];
            System.arraycopy(hmac, 0, result, 0, hmac.length);
            System.arraycopy(ivAndCiphertext, 0, result, hmac.length, ivAndCiphertext.length);
            return result;
        } catch (GeneralSecurityException failure) {
            return null;
        }
    }

    public static byte[] decryptBytes(byte[] body, SecretKeySpec aesKey,
            SecretKeySpec hmacKey) throws KeyException {
        if (body == null) {
            return null;
        }
        int offset = hmacKey == null ? 0 : HMAC_BYTES;
        if (body.length < offset + IV_BYTES) {
            throw new KeyException(hmacKey == null
                    ? "Binary frame shorter than IV"
                    : "Binary frame shorter than HMAC and IV");
        }
        if (hmacKey != null) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(hmacKey);
                mac.update(body, HMAC_BYTES, body.length - HMAC_BYTES);
                byte[] actual = mac.doFinal();
                byte[] expected = new byte[HMAC_BYTES];
                System.arraycopy(body, 0, expected, 0, HMAC_BYTES);
                if (!MessageDigest.isEqual(expected, actual)) {
                    throw new KeyException("BAD HMAC or BAD KEY");
                }
            } catch (KeyException failure) {
                throw failure;
            } catch (GeneralSecurityException failure) {
                throw new IllegalStateException("Cannot authenticate channel payload", failure);
            }
        }
        byte[] iv = new byte[IV_BYTES];
        System.arraycopy(body, offset, iv, 0, IV_BYTES);
        int encryptedOffset = offset + IV_BYTES;
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(iv));
            return cipher.doFinal(body, encryptedOffset, body.length - encryptedOffset);
        } catch (BadPaddingException | IllegalBlockSizeException failure) {
            return null;
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Cannot decrypt channel payload", failure);
        }
    }

    public static String encryptCommand(String command, SecretKeySpec aesKey,
            SecretKeySpec hmacKey) {
        String encrypted = encryptText(command, aesKey, hmacKey);
        return encrypted == null ? null : "*" + encrypted;
    }

    public static String encryptCommand(String command, SecretKeySpec aesKey,
            byte[] iv, SecretKeySpec hmacKey) {
        String encrypted = encryptText(command, aesKey, iv, hmacKey);
        return encrypted == null ? null : "*" + encrypted;
    }

    public static String decryptCommand(String command, SecretKeySpec aesKey,
            SecretKeySpec hmacKey) throws KeyException {
        if (command == null) {
            return null;
        }
        String frame = command.trim();
        if (!frame.isEmpty() && frame.charAt(0) == '*') {
            return decryptText(frame.substring(1), aesKey, hmacKey);
        }
        if (isPlaintextControlFrame(frame)) {
            return frame;
        }
        throw new KeyException("Unauthenticated frame rejected on an encrypted channel");
    }

    static boolean isPlaintextControlFrame(String frame) {
        int separator = frame.indexOf('#');
        if (separator <= 0 || separator == frame.length() - 1) {
            return false;
        }
        String verb = frame.substring(0, separator);
        if (!("PING".equals(verb) || "PONG".equals(verb) || "PONG2".equals(verb))) {
            return false;
        }
        String counter = frame.substring(separator + 1);
        if (counter.length() > 11) {
            return false;
        }
        for (int i = 0; i < counter.length(); i++) {
            char c = counter.charAt(i);
            if ((c < '0' || c > '9') && !(i == 0 && c == '-')) {
                return false;
            }
        }
        return !"-".equals(counter);
    }
}
