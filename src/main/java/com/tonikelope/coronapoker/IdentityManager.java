/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * EC-Identity v1: persistent per-installation Ed25519 keypair used for player identity.
 *
 * Pubkey identifies the machine (the installation). Nick identifies the player. Two players
 * sharing one machine have the same pubkey but different nicks; the same player on two
 * machines has the same nick but different pubkeys (seen by peers as a key change via TOFU).
 *
 * Storage:
 *   <user.home>/.coronapoker/identity.ed25519       PKCS#8 encoded private key, FS 0600 on POSIX
 *   <user.home>/.coronapoker/identity.ed25519.pub   32 raw bytes (no header)
 *
 * Domain separators are required for every sign/verify call to prevent cross-protocol
 * signature confusion. Example: "ACTION_V1\0", "JOIN_V1\0", "RECEIPT_V1\0".
 *
 * If the identity cannot be loaded or generated, the manager keeps the load error and
 * isReady() returns false; callers must refuse to start networked games. The integration
 * with the UI to surface this error lives in WaitingRoomFrame (commit 2).
 */
public final class IdentityManager {

    private static final Logger LOGGER = Logger.getLogger(IdentityManager.class.getName());

    private static final String ALGORITHM = "Ed25519";

    /**
     * Standard X.509 SubjectPublicKeyInfo header for an Ed25519 key. Followed by the
     * 32 raw key bytes to make up a 44-byte X.509 encoded form. Used to reconstruct a
     * java.security.PublicKey from the 32 raw bytes stored on disk and transmitted over
     * the wire.
     */
    private static final byte[] X509_ED25519_HEADER = {
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    private static final int RAW_PUBKEY_LEN = 32;
    private static final int X509_PUBKEY_LEN = X509_ED25519_HEADER.length + RAW_PUBKEY_LEN;

    private static final String IDENTITY_FILE = "identity.ed25519";
    private static final String IDENTITY_PUB_FILE = "identity.ed25519.pub";

    /**
     * Resolved independently of Init.CORONA_DIR so this class stays standalone and
     * unit-testable without dragging in the rest of the application's class graph.
     * The path must remain in sync with Init.CORONA_DIR — both compute the same value.
     */
    private static final String CORONA_DIR_PATH = System.getProperty("user.home") + "/.coronapoker";

    private static volatile IdentityManager INSTANCE;

    private final PrivateKey privateKey;
    private final byte[] publicKeyRaw;
    private final String shortFingerprint;
    private final String fullFingerprint;
    private final String loadError;

    /**
     * Returns the singleton. Synchronously loads or generates the identity on first call.
     * Never returns null; if load/generation failed, isReady() returns false and
     * getLoadError() returns the user-facing reason.
     */
    public static synchronized IdentityManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new IdentityManager();
        }
        return INSTANCE;
    }

    private IdentityManager() {
        PrivateKey priv = null;
        byte[] pubRaw = null;
        String error = null;

        try {
            File dir = ensureCoronaDir();
            File privFile = new File(dir, IDENTITY_FILE);
            File pubFile = new File(dir, IDENTITY_PUB_FILE);

            if (privFile.isFile() && pubFile.isFile()) {
                priv = loadPrivateKey(privFile);
                pubRaw = loadPublicKeyRaw(pubFile);
                LOGGER.log(Level.INFO, "Ed25519 identity loaded from {0}", dir.getAbsolutePath());
            } else {
                KeyPair kp = generateKeyPair();
                priv = kp.getPrivate();
                pubRaw = x509PubKeyToRaw(kp.getPublic().getEncoded());
                writeKeypair(privFile, pubFile, priv, pubRaw);
                LOGGER.log(Level.INFO, "Ed25519 identity generated at {0}", dir.getAbsolutePath());
            }
        } catch (IdentityException ex) {
            error = ex.getMessage();
            LOGGER.log(Level.SEVERE, "Identity initialization failed: {0}", error);
        } catch (Exception ex) {
            error = "Unexpected error: " + ex.getMessage();
            LOGGER.log(Level.SEVERE, "Identity initialization failed unexpectedly", ex);
        }

        this.privateKey = priv;
        this.publicKeyRaw = pubRaw;
        this.loadError = error;

        if (pubRaw != null) {
            byte[] hash = sha256(pubRaw);
            this.shortFingerprint = hexShort(hash);
            this.fullFingerprint = hexFull(hash);
            LOGGER.log(Level.INFO, "Ed25519 fingerprint: {0}", this.fullFingerprint);
        } else {
            this.shortFingerprint = null;
            this.fullFingerprint = null;
        }
    }

    public boolean isReady() {
        return loadError == null && privateKey != null && publicKeyRaw != null;
    }

    public String getLoadError() {
        return loadError;
    }

    /**
     * Returns a defensive copy of the 32-byte raw Ed25519 public key.
     */
    public byte[] getPublicKey() {
        if (publicKeyRaw == null) {
            return null;
        }
        return publicKeyRaw.clone();
    }

    /**
     * 32-bit fingerprint as 8 hex chars with a single dash separator (e.g. "a3f9-1c4b").
     * Compact display use; not safe alone against deliberate collisions.
     */
    public String getShortFingerprint() {
        return shortFingerprint;
    }

    /**
     * 128-bit fingerprint as 8 groups of 4 hex chars separated by spaces. For
     * out-of-band verification by voice or chat.
     */
    public String getFullFingerprint() {
        return fullFingerprint;
    }

    /**
     * Signs (domain || data) with this installation's Ed25519 private key. The domain
     * separator is mandatory and must be a unique non-empty byte string per protocol
     * context (e.g. "ACTION_V1\0", "JOIN_V1\0"). Returns a 64-byte signature.
     */
    public byte[] sign(byte[] domain, byte[] data) {
        if (!isReady()) {
            throw new IllegalStateException("Identity not ready: " + loadError);
        }
        if (domain == null || domain.length == 0) {
            throw new IllegalArgumentException("Domain separator must not be empty");
        }
        if (data == null) {
            throw new IllegalArgumentException("Data must not be null");
        }
        try {
            java.security.Signature sig = java.security.Signature.getInstance(ALGORITHM);
            sig.initSign(privateKey);
            sig.update(domain);
            sig.update(data);
            return sig.sign();
        } catch (Exception ex) {
            throw new RuntimeException("Ed25519 sign failed", ex);
        }
    }

    /**
     * Verifies a signature over (domain || data) against the given 32-byte raw Ed25519
     * public key. Returns false on any error or signature mismatch; never throws on
     * invalid signature, throws only on programming errors (null inputs, wrong sizes).
     */
    public static boolean verify(byte[] rawPubKey, byte[] domain, byte[] data, byte[] signature) {
        if (rawPubKey == null || rawPubKey.length != RAW_PUBKEY_LEN) {
            throw new IllegalArgumentException("rawPubKey must be 32 bytes");
        }
        if (domain == null || domain.length == 0) {
            throw new IllegalArgumentException("Domain separator must not be empty");
        }
        if (data == null || signature == null) {
            throw new IllegalArgumentException("Data and signature must not be null");
        }
        try {
            PublicKey pub = rawPubKeyToPublicKey(rawPubKey);
            java.security.Signature sig = java.security.Signature.getInstance(ALGORITHM);
            sig.initVerify(pub);
            sig.update(domain);
            sig.update(data);
            return sig.verify(signature);
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Ed25519 verify failed", ex);
            return false;
        }
    }

    /**
     * Reconstructs a java.security.PublicKey from the 32 raw Ed25519 bytes by prepending
     * the standard X.509 SubjectPublicKeyInfo header.
     */
    public static PublicKey rawPubKeyToPublicKey(byte[] rawPubKey) throws Exception {
        if (rawPubKey == null || rawPubKey.length != RAW_PUBKEY_LEN) {
            throw new IllegalArgumentException("rawPubKey must be 32 bytes");
        }
        byte[] x509 = new byte[X509_PUBKEY_LEN];
        System.arraycopy(X509_ED25519_HEADER, 0, x509, 0, X509_ED25519_HEADER.length);
        System.arraycopy(rawPubKey, 0, x509, X509_ED25519_HEADER.length, RAW_PUBKEY_LEN);
        return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(x509));
    }

    /**
     * Extracts the 32 raw Ed25519 public key bytes from an X.509 encoded form.
     */
    public static byte[] x509PubKeyToRaw(byte[] x509) {
        if (x509 == null || x509.length != X509_PUBKEY_LEN) {
            throw new IllegalArgumentException("X.509 Ed25519 pubkey must be " + X509_PUBKEY_LEN + " bytes");
        }
        byte[] raw = new byte[RAW_PUBKEY_LEN];
        System.arraycopy(x509, X509_ED25519_HEADER.length, raw, 0, RAW_PUBKEY_LEN);
        return raw;
    }

    // ===== Private helpers =====

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(ALGORITHM);
        return kpg.generateKeyPair();
    }

    private static File ensureCoronaDir() throws IdentityException {
        File dir = new File(CORONA_DIR_PATH);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IdentityException("Cannot create directory: " + dir.getAbsolutePath());
        }
        if (!dir.isDirectory()) {
            throw new IdentityException("Path exists but is not a directory: " + dir.getAbsolutePath());
        }
        if (!dir.canWrite()) {
            throw new IdentityException("Directory is not writable: " + dir.getAbsolutePath());
        }
        return dir;
    }

    private static PrivateKey loadPrivateKey(File file) throws IdentityException {
        try {
            byte[] pkcs8 = Files.readAllBytes(file.toPath());
            return KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (Exception ex) {
            throw new IdentityException("Cannot read private key file: " + file.getAbsolutePath() + " (" + ex.getMessage() + ")");
        }
    }

    private static byte[] loadPublicKeyRaw(File file) throws IdentityException {
        try {
            byte[] raw = Files.readAllBytes(file.toPath());
            if (raw.length != RAW_PUBKEY_LEN) {
                throw new IdentityException("Public key file has wrong size: " + raw.length + " bytes (expected " + RAW_PUBKEY_LEN + ")");
            }
            return raw;
        } catch (IdentityException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IdentityException("Cannot read public key file: " + file.getAbsolutePath() + " (" + ex.getMessage() + ")");
        }
    }

    private static void writeKeypair(File privFile, File pubFile, PrivateKey priv, byte[] pubRaw) throws IdentityException {
        try {
            Files.write(privFile.toPath(), priv.getEncoded());
            Files.write(pubFile.toPath(), pubRaw);
        } catch (IOException ex) {
            throw new IdentityException("Cannot write identity keypair: " + ex.getMessage());
        }
        applyOwnerOnlyPermissions(privFile.toPath());
    }

    private static void applyOwnerOnlyPermissions(Path path) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return;
        }
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Could not set 0600 permissions on {0}: {1}",
                    new Object[]{path, ex.getMessage()});
        }
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception ex) {
            throw new RuntimeException("SHA-256 unavailable", ex);
        }
    }

    private static String hexShort(byte[] hash) {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 4; i++) {
            sb.append(String.format("%02x", hash[i] & 0xff));
            if (i == 1) {
                sb.append('-');
            }
        }
        return sb.toString();
    }

    private static String hexFull(byte[] hash) {
        StringBuilder sb = new StringBuilder(39);
        for (int i = 0; i < 16; i++) {
            sb.append(String.format("%02x", hash[i] & 0xff));
            if (i % 2 == 1 && i < 15) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    // ===== Internal checked exception (kept private to avoid leaking implementation detail) =====

    private static final class IdentityException extends Exception {

        private static final long serialVersionUID = 1L;

        IdentityException(String msg) {
            super(msg);
        }
    }

    /**
     * Optional warm-up: triggers identity load/generation. Safe to call any number of
     * times. Used by Init.main() to surface load errors as early as possible in the
     * application lifecycle, so the user sees them at startup rather than mid-game.
     */
    public static void initialize() {
        getInstance();
    }
}
