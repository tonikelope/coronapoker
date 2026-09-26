package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.network.SecureChannelCodec;
import java.io.BufferedReader;
import java.io.IOException;
import java.security.SecureRandom;
import javax.crypto.spec.SecretKeySpec;

/** Shared random source for renderer-independent legacy QA fixtures. */
public final class Helpers {

    public static final int MAX_COMMAND_LINE_CHARS = 16 * 1024 * 1024;
    public static volatile SecureRandom CSPRNG_GENERATOR = new SecureRandom();

    private Helpers() {
    }

    public static String readBoundedLine(BufferedReader reader, int maxChars)
            throws IOException {
        StringBuilder line = new StringBuilder(256);
        int current;
        boolean read = false;
        while ((current = reader.read()) != -1) {
            read = true;
            if (current == '\n') return line.toString();
            if (current == '\r') continue;
            line.append((char) current);
            if (line.length() > maxChars) {
                throw new IOException("Line exceeds " + maxChars
                        + " char cap (DoS guard tripped)");
            }
        }
        return read ? line.toString() : null;
    }

    public static byte[] encryptBytes(byte[] payload, SecretKeySpec aesKey,
            SecretKeySpec hmacKey) {
        return SecureChannelCodec.encryptBytes(payload, aesKey, hmacKey);
    }

    public static byte[] encryptBytes(byte[] payload, SecretKeySpec aesKey,
            byte[] iv, SecretKeySpec hmacKey) {
        return SecureChannelCodec.encryptBytes(payload, aesKey, iv, hmacKey);
    }

    public static byte[] decryptBytes(byte[] payload, SecretKeySpec aesKey,
            SecretKeySpec hmacKey) throws java.security.KeyException {
        return SecureChannelCodec.decryptBytes(payload, aesKey, hmacKey);
    }

    public static String encryptCommand(String command, SecretKeySpec aesKey,
            SecretKeySpec hmacKey) {
        return SecureChannelCodec.encryptCommand(command, aesKey, hmacKey);
    }

    public static String encryptString(String text, SecretKeySpec aesKey,
            byte[] iv, SecretKeySpec hmacKey) {
        return SecureChannelCodec.encryptText(text, aesKey, iv, hmacKey);
    }

    public static String decryptString(String encoded, SecretKeySpec aesKey,
            SecretKeySpec hmacKey) throws java.security.KeyException {
        return SecureChannelCodec.decryptText(encoded, aesKey, hmacKey);
    }

    public static String decryptCommand(String command, SecretKeySpec aesKey,
            SecretKeySpec hmacKey) throws java.security.KeyException {
        return SecureChannelCodec.decryptCommand(command, aesKey, hmacKey);
    }
}
