package com.tonikelope.coronapoker.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Renderer-neutral visual fingerprint shared by Swing and native frontends. */
public final class IdenticonFingerprint {

    public static final int GRID_SIZE = 7;
    private static final int DIGEST_BYTES = 32;

    private final byte[] digest;

    private IdenticonFingerprint(byte[] digest) {
        this.digest = digest;
    }

    public static IdenticonFingerprint fromSeed(byte[] seed) {
        Objects.requireNonNull(seed, "seed");
        try {
            return new IdenticonFingerprint(MessageDigest.getInstance("SHA-256")
                    .digest(seed));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public static IdenticonFingerprint fromDigest(byte[] digest) {
        Objects.requireNonNull(digest, "digest");
        if (digest.length != DIGEST_BYTES) {
            throw new IllegalArgumentException("A SHA-256 digest must contain 32 bytes");
        }
        return new IdenticonFingerprint(digest.clone());
    }

    public byte[] digest() {
        return digest.clone();
    }

    public int foregroundArgb(int row) {
        int offset = (row & 1) == 0 ? 0 : 4;
        return 0xff000000 | (digest[offset] & 0xff) << 16
                | (digest[offset + 1] & 0xff) << 8
                | digest[offset + 2] & 0xff;
    }

    public boolean filled(int column, int row) {
        if (column < 0 || column >= GRID_SIZE || row < 0 || row >= GRID_SIZE) {
            throw new IndexOutOfBoundsException("Identicon cell outside 7x7 grid");
        }
        int mirroredColumn = column < 4 ? column : 6 - column;
        return ((digest[8 + mirroredColumn] >> row) & 1) == 1;
    }

    /** First 16 digest bytes, grouped exactly like the established Swing UI. */
    public String formatted() {
        String hex = HexFormat.of().formatHex(digest, 0, 16);
        StringBuilder grouped = new StringBuilder(39);
        for (int index = 0; index < hex.length(); index += 4) {
            if (!grouped.isEmpty()) grouped.append(' ');
            grouped.append(hex, index, index + 4);
        }
        return grouped.toString();
    }
}
