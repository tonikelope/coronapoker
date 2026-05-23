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
 * EC-Identity v1: persistent per-nick Ed25519 keypair used for player identity.
 *
 * The keypair is bound to the NFC-canonicalized nick: each nick used on this machine
 * gets its own keypair on disk, so two test instances launched with different nicks on
 * the same machine end up with distinct identities. Switching to a previously used
 * nick reloads the existing keypair instead of generating a new one.
 *
 * Storage (per nick):
 *   <user.home>/.coronapoker/identity_<player_id_hex>.ed25519       PKCS#8 private key (0600 on POSIX)
 *   <user.home>/.coronapoker/identity_<player_id_hex>.ed25519.pub   32 raw bytes (no header)
 *
 *   player_id_hex = first 16 hex chars (8 bytes / 64 bits) of SHA-256(NFC(nick) UTF-8).
 *
 * Lifecycle:
 *   - {@link #initializeForNick(String)} is called once the nick is committed (from
 *     NewGameDialog before opening the waiting room). It loads or generates the keypair
 *     synchronously and surfaces any I/O error via {@link #getLoadError()}.
 *   - {@link #getInstance()} returns the current singleton. If never initialized it
 *     returns an "uninitialized" instance with isReady()==false; callers in the
 *     networked code path must check isReady().
 *   - Re-initialization with a different nick swaps the keypair (testing scenario).
 *
 * Domain separators are required for every sign/verify call to prevent cross-protocol
 * signature confusion. Example: "ACTION_V1\0", "JOIN_V1\0", "RECEIPT_V1\0".
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

    private static final String IDENTITY_FILE_PREFIX = "identity_";
    private static final String IDENTITY_FILE_SUFFIX = ".ed25519";
    private static final String IDENTITY_PUB_FILE_SUFFIX = ".ed25519.pub";

    /**
     * Length in hex chars of the player-id slug appended to the identity filename.
     * 16 hex chars == 8 bytes == 64 bits, derived from SHA-256(NFC(nick) UTF-8). Wide
     * enough that accidental nick collisions on the same machine are astronomically
     * unlikely; not so wide that it makes filenames unreadable.
     */
    private static final int PLAYER_ID_HEX_LEN = 16;

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
    private final String boundNick;

    /**
     * Loads or generates the Ed25519 keypair bound to {@code nick} and installs it as
     * the current singleton. If a singleton already exists for the same nick it is
     * returned unchanged. If the nick differs, the singleton is replaced.
     *
     * Synchronous; surface any storage error via {@link #getLoadError()} on the returned
     * instance and refuse to enter networked games when {@link #isReady()} is false.
     */
    public static synchronized IdentityManager initializeForNick(String nick) {
        if (nick == null || nick.trim().isEmpty()) {
            throw new IllegalArgumentException("nick required");
        }
        String canonical = canonicalNick(nick);
        if (INSTANCE != null && canonical.equals(INSTANCE.boundNick)) {
            return INSTANCE;
        }
        INSTANCE = new IdentityManager(canonical);
        return INSTANCE;
    }

    /**
     * Returns the singleton previously installed by {@link #initializeForNick(String)}.
     * If no initialization has happened yet, returns an "uninitialized" instance whose
     * {@link #isReady()} returns false; callers must check before using.
     */
    public static synchronized IdentityManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new IdentityManager(null);
        }
        return INSTANCE;
    }

    private IdentityManager(String canonicalNick) {
        PrivateKey priv = null;
        byte[] pubRaw = null;
        String error = null;

        if (canonicalNick == null) {
            error = "Identity not bound to a nick yet";
        } else {
            try {
                File dir = ensureCoronaDir();
                String slug = playerIdHex(canonicalNick);
                File privFile = new File(dir, IDENTITY_FILE_PREFIX + slug + IDENTITY_FILE_SUFFIX);
                File pubFile = new File(dir, IDENTITY_FILE_PREFIX + slug + IDENTITY_PUB_FILE_SUFFIX);

                if (privFile.isFile() && pubFile.isFile()) {
                    priv = loadPrivateKey(privFile);
                    pubRaw = loadPublicKeyRaw(pubFile);
                    LOGGER.log(Level.INFO, "Ed25519 identity loaded for nick=\"{0}\" from {1}",
                            new Object[]{canonicalNick, privFile.getAbsolutePath()});
                } else {
                    KeyPair kp = generateKeyPair();
                    priv = kp.getPrivate();
                    pubRaw = x509PubKeyToRaw(kp.getPublic().getEncoded());
                    writeKeypair(privFile, pubFile, priv, pubRaw);
                    LOGGER.log(Level.INFO, "Ed25519 identity generated for nick=\"{0}\" at {1}",
                            new Object[]{canonicalNick, privFile.getAbsolutePath()});
                }
            } catch (IdentityException ex) {
                error = ex.getMessage();
                LOGGER.log(Level.SEVERE, "Identity initialization failed: {0}", error);
            } catch (Exception ex) {
                error = "Unexpected error: " + ex.getMessage();
                LOGGER.log(Level.SEVERE, "Identity initialization failed unexpectedly", ex);
            }
        }

        this.boundNick = canonicalNick;
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

    /**
     * NFC-canonicalize a nick to the same form used everywhere else in the identity
     * layer (joinPayload, PLAYER_ID hash). Trims surrounding whitespace because the
     * nick may arrive from a text field.
     */
    private static String canonicalNick(String nick) {
        return java.text.Normalizer.normalize(nick.trim(), java.text.Normalizer.Form.NFC);
    }

    /**
     * Derives the per-nick filename slug: the first PLAYER_ID_HEX_LEN hex chars of
     * SHA-256(NFC(nick) UTF-8). Same nick on two machines yields the same slug; two
     * nicks with different NFC bytes yield different slugs with overwhelming
     * probability (64 bits).
     */
    private static String playerIdHex(String canonicalNick) {
        byte[] hash = sha256(canonicalNick.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(PLAYER_ID_HEX_LEN);
        int nbytes = PLAYER_ID_HEX_LEN / 2;
        for (int i = 0; i < nbytes; i++) {
            sb.append(String.format("%02x", hash[i] & 0xff));
        }
        return sb.toString();
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

    // ===== ACTION_V1 helpers (commit 5) =====

    /**
     * Domain separator for per-action signatures (spec §4.4). Bound to the 92-byte
     * CanonicalActionRecord so a signature cannot be replayed in any other protocol
     * context.
     */
    private static final byte[] ACTION_DOMAIN = "ACTION_V1\0".getBytes(StandardCharsets.UTF_8);

    /**
     * Signs a CanonicalActionRecord with this installation's privkey under the
     * ACTION_V1 domain. The host uses this both for its own player actions and for
     * actions it issues on behalf of others (auto-folds with voluntary=0, bot
     * actions). The caller is responsible for building the record with the correct
     * PLAYER_ID and FLAGS bits.
     */
    public byte[] signAction(byte[] record) {
        if (record == null || record.length == 0) {
            throw new IllegalArgumentException("record required");
        }
        return sign(ACTION_DOMAIN, record);
    }

    /**
     * Verifies a per-action signature against the given raw 32-byte Ed25519 pubkey
     * under the ACTION_V1 domain. Returns false on any error or mismatch. Caller
     * is responsible for picking the right pubkey using the §10 consolidated
     * receiver rule (voluntary bit + bot check).
     */
    public static boolean verifyAction(byte[] rawPubKey, byte[] record, byte[] sig) {
        try {
            return verify(rawPubKey, ACTION_DOMAIN, record, sig);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.WARNING, "verifyAction rejected by argument validation: {0}", ex.getMessage());
            return false;
        }
    }

    // ===== RECEIPT_V2 helpers (Opción A: flags byte) =====

    /**
     * Domain separator for end-of-hand consensus receipts (spec §6.2). Bumped
     * to V2 with the addition of the 1-byte flags field (bit0 = the issuer
     * observed an invalid Ed25519 signature on at least one ACTION or
     * COMM_REVEAL during the hand). V1 receipts (no flags) are wire-incompatible
     * — within-session version pinning already prevents mixing.
     */
    private static final byte[] RECEIPT_DOMAIN = "RECEIPT_V2\0".getBytes(StandardCharsets.UTF_8);

    /**
     * Canonical payload for a receipt: {@code HAND_ID || H_final || flags}. The
     * flags byte is part of what gets signed so the host (or any relay) cannot
     * silently strip the "saw_invalid_sig" bit when forwarding a receipt to
     * other peers. The domain separator "RECEIPT_V2\0" is applied by
     * sign/verify, not embedded here.
     */
    public static byte[] receiptPayload(byte[] handId, byte[] hFinal, byte flags) {
        if (handId == null || handId.length != CanonicalActionRecord.HAND_ID_BYTES) {
            throw new IllegalArgumentException("handId must be "
                    + CanonicalActionRecord.HAND_ID_BYTES + " bytes");
        }
        if (hFinal == null || hFinal.length != 32) {
            throw new IllegalArgumentException("hFinal must be 32 bytes");
        }
        byte[] payload = new byte[handId.length + hFinal.length + 1];
        System.arraycopy(handId, 0, payload, 0, handId.length);
        System.arraycopy(hFinal, 0, payload, handId.length, hFinal.length);
        payload[handId.length + hFinal.length] = flags;
        return payload;
    }

    /**
     * Signs an end-of-hand receipt {@code (HAND_ID || H_final || flags)} with
     * this installation's privkey under the RECEIPT_V2 domain. Returns the
     * 64-byte Ed25519 signature. The on-wire receipt is the concatenation
     * {@code HAND_ID || H_final || flags || sig}; the wire encoder lives in
     * {@link Crupier} so the format stays close to its consumer.
     */
    public byte[] signReceipt(byte[] handId, byte[] hFinal, byte flags) {
        return sign(RECEIPT_DOMAIN, receiptPayload(handId, hFinal, flags));
    }

    /**
     * Verifies a receipt signature against the given 32-byte raw Ed25519 pubkey.
     * Returns false on any error.
     */
    public static boolean verifyReceipt(byte[] rawPubKey, byte[] handId, byte[] hFinal, byte flags, byte[] sig) {
        try {
            return verify(rawPubKey, RECEIPT_DOMAIN, receiptPayload(handId, hFinal, flags), sig);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.WARNING, "verifyReceipt rejected by argument validation: {0}", ex.getMessage());
            return false;
        }
    }

    // ===== SHOWDOWN_REVEAL helpers (PHASE A.1: showdown zero-trust + sig) =====

    private static final byte[] SHOWDOWN_DOMAIN = "SHOWDOWN_V1\0".getBytes(StandardCharsets.UTF_8);

    /**
     * Canonical payload signed dentro de un SHOWCARDS / RESP_SHOWDOWN_KEY:
     * {@code HAND_ID || nick_utf8 || pocketKey(32)}. La sig demuestra que la
     * pocket-key específica del nick fue autoriza por su Ed25519 privkey, así
     * un host MitM no puede substituirla ni atribuirla al peer equivocado. El
     * dominio "SHOWDOWN_V1\0" se aplica en sign/verify, no se embebe aquí.
     */
    public static byte[] showdownPayload(byte[] handId, String nick, byte[] pocketKey) {
        if (handId == null || handId.length != CanonicalActionRecord.HAND_ID_BYTES) {
            throw new IllegalArgumentException("handId must be "
                    + CanonicalActionRecord.HAND_ID_BYTES + " bytes");
        }
        if (nick == null || nick.isEmpty()) {
            throw new IllegalArgumentException("nick must be non-empty");
        }
        if (pocketKey == null || pocketKey.length != 32) {
            throw new IllegalArgumentException("pocketKey must be 32 bytes");
        }
        byte[] nickBytes = nick.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[handId.length + nickBytes.length + pocketKey.length];
        System.arraycopy(handId, 0, payload, 0, handId.length);
        System.arraycopy(nickBytes, 0, payload, handId.length, nickBytes.length);
        System.arraycopy(pocketKey, 0, payload, handId.length + nickBytes.length, pocketKey.length);
        return payload;
    }

    /**
     * Firma una SHOWCARDS reveal {@code (HAND_ID || nick || pocketKey)} con la
     * privkey de esta instalación bajo el dominio SHOWDOWN_V1. Devuelve la sig
     * Ed25519 de 64 bytes que viaja en RESP_SHOWDOWN_KEY y SHOWCARDS.
     */
    public byte[] signShowdownReveal(byte[] handId, String nick, byte[] pocketKey) {
        return sign(SHOWDOWN_DOMAIN, showdownPayload(handId, nick, pocketKey));
    }

    /**
     * Verifica una sig de SHOWCARDS contra la pubkey raw Ed25519 del nick
     * propietario. Devuelve false en cualquier fallo.
     */
    public static boolean verifyShowdownReveal(byte[] rawPubKey, byte[] handId, String nick, byte[] pocketKey, byte[] sig) {
        try {
            return verify(rawPubKey, SHOWDOWN_DOMAIN, showdownPayload(handId, nick, pocketKey), sig);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.WARNING, "verifyShowdownReveal rejected by argument validation: {0}", ex.getMessage());
            return false;
        }
    }

    // ===== JOIN_IDENTITY helpers (commit 2b) =====

    private static final byte[] JOIN_DOMAIN = "JOIN_V1\0".getBytes(StandardCharsets.UTF_8);

    /**
     * Canonical payload signed inside a JOIN_IDENTITY self_sig: NFC-normalized nick UTF-8
     * concatenated with session_id and the 32-byte raw pubkey. The domain separator
     * "JOIN_V1\0" is applied by sign/verify, not embedded in this byte string.
     */
    public static byte[] joinPayload(byte[] sessionId, String nick, byte[] rawPubKey) {
        if (sessionId == null || sessionId.length == 0) {
            throw new IllegalArgumentException("sessionId required");
        }
        if (nick == null || nick.isEmpty()) {
            throw new IllegalArgumentException("nick required");
        }
        if (rawPubKey == null || rawPubKey.length != RAW_PUBKEY_LEN) {
            throw new IllegalArgumentException("rawPubKey must be 32 bytes");
        }
        byte[] nickBytes = java.text.Normalizer.normalize(nick, java.text.Normalizer.Form.NFC)
                .getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[sessionId.length + nickBytes.length + rawPubKey.length];
        int o = 0;
        System.arraycopy(sessionId, 0, payload, o, sessionId.length);
        o += sessionId.length;
        System.arraycopy(nickBytes, 0, payload, o, nickBytes.length);
        o += nickBytes.length;
        System.arraycopy(rawPubKey, 0, payload, o, rawPubKey.length);
        return payload;
    }

    /**
     * Signs a JOIN_IDENTITY self-attestation for this installation. The returned 64-byte
     * Ed25519 signature commits to (session_id, nick, own pubkey) under the JOIN_V1 domain.
     */
    public byte[] signJoin(byte[] sessionId, String nick) {
        return sign(JOIN_DOMAIN, joinPayload(sessionId, nick, getPublicKey()));
    }

    /**
     * Verifies a JOIN_IDENTITY self-attestation received from a remote peer.
     */
    public static boolean verifyJoin(byte[] sessionId, String nick, byte[] rawPubKey, byte[] sig) {
        try {
            return verify(rawPubKey, JOIN_DOMAIN, joinPayload(sessionId, nick, rawPubKey), sig);
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "verifyJoin rejected: " + ex.getMessage());
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
            applyWindowsAclOwnerOnly(path);
            return;
        }
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Could not set 0600 permissions on {0}: {1}",
                    new Object[]{path, ex.getMessage()});
        }
    }

    /**
     * Restringir el privkey Ed25519 al usuario actual en Windows usando icacls.
     *   /inheritance:r  → quita las ACEs heredadas del padre (el archivo deja
     *                     de heredar permisos genéricos como "Users:(RX)" o
     *                     "Authenticated Users:(M)").
     *   /grant:r USER:F → otorga FULL control al usuario actual, R reemplaza
     *                     cualquier entrada previa para ese mismo usuario.
     *
     * Resultado: solo el owner puede leer/escribir el fichero. Si la operación
     * falla (icacls no disponible, permisos insuficientes), log WARNING — el
     * fichero queda con permisos por defecto de Windows (peor pero no roto).
     * En POSIX el equivalente es 0600 via setPosixFilePermissions.
     */
    private static void applyWindowsAclOwnerOnly(Path path) {
        String username = System.getProperty("user.name");
        if (username == null || username.trim().isEmpty()) {
            LOGGER.log(Level.WARNING,
                    "applyWindowsAclOwnerOnly: user.name is empty — leaving default ACL on {0}", path);
            return;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "icacls",
                    path.toAbsolutePath().toString(),
                    "/inheritance:r",
                    "/grant:r", username + ":(F)"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            // Drenar stdout/stderr para evitar que el proceso bloquee por buffer lleno.
            // No nos importa el output, solo el exit code.
            try (java.io.InputStream is = p.getInputStream()) {
                byte[] buf = new byte[4096];
                while (is.read(buf) != -1) {
                    // discard
                }
            }
            int exit = p.waitFor();
            if (exit != 0) {
                LOGGER.log(Level.WARNING,
                        "icacls returned exit code {0} on {1} — ACL may not be restricted",
                        new Object[]{exit, path});
            } else {
                LOGGER.log(Level.INFO,
                        "Windows ACL restricted to user \"{0}\" on {1}",
                        new Object[]{username, path});
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Could not apply Windows ACL on {0}: {1}",
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

}
