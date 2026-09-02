package com.tonikelope.coronapoker.core.identity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.Normalizer;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Persistent per-nickname Ed25519 identity shared by Swing and GDX adapters. */
public final class PlayerIdentity {
    private static final Logger LOGGER = Logger.getLogger(PlayerIdentity.class.getName());
    private static final String ALGORITHM = "Ed25519";
    private static final byte[] X509_HEADER = {
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };
    private static final byte[] JOIN_DOMAIN = "JOIN\0".getBytes(StandardCharsets.UTF_8);
    private static final int RAW_PUBLIC_KEY_BYTES = 32;

    private final String nickname;
    private final PrivateKey privateKey;
    private final byte[] publicKey;

    private PlayerIdentity(String nickname, PrivateKey privateKey, byte[] publicKey) {
        this.nickname = nickname;
        this.privateKey = privateKey;
        this.publicKey = publicKey.clone();
    }

    public static PlayerIdentity loadOrCreate(Path coronaDirectory, String nickname) throws IOException {
        Objects.requireNonNull(coronaDirectory, "coronaDirectory");
        String canonical = canonicalNickname(nickname);
        Files.createDirectories(coronaDirectory);
        if (!Files.isDirectory(coronaDirectory) || !Files.isWritable(coronaDirectory)) {
            throw new IOException("Identity directory is not writable: " + coronaDirectory);
        }
        String slug = HexFormat.of().formatHex(sha256(canonical.getBytes(StandardCharsets.UTF_8)), 0, 8);
        Path privatePath = coronaDirectory.resolve("identity_" + slug + ".ed25519");
        Path publicPath = coronaDirectory.resolve("identity_" + slug + ".ed25519.pub");
        try {
            if (Files.isRegularFile(privatePath) && Files.isRegularFile(publicPath)) {
                PrivateKey privateKey = KeyFactory.getInstance(ALGORITHM).generatePrivate(
                        new PKCS8EncodedKeySpec(Files.readAllBytes(privatePath)));
                byte[] publicKey = Files.readAllBytes(publicPath);
                requirePublicKey(publicKey);
                return new PlayerIdentity(canonical, privateKey, publicKey);
            }
            KeyPair pair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
            byte[] rawPublic = rawPublicKey(pair.getPublic().getEncoded());
            Files.deleteIfExists(privatePath);
            Files.createFile(privatePath);
            restrictToOwner(privatePath);
            Files.write(privatePath, pair.getPrivate().getEncoded(), StandardOpenOption.WRITE);
            Files.write(publicPath, rawPublic, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            return new PlayerIdentity(canonical, pair.getPrivate(), rawPublic);
        } catch (IOException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IOException("Cannot initialize player identity", failure);
        }
    }

    public String nickname() { return nickname; }
    public byte[] publicKey() { return publicKey.clone(); }

    public byte[] signJoin(byte[] sessionId) {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(privateKey);
            signature.update(JOIN_DOMAIN);
            signature.update(joinPayload(sessionId, nickname, publicKey));
            return signature.sign();
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot sign JOIN identity", failure);
        }
    }

    public static boolean verifyJoin(byte[] sessionId, String nickname,
            byte[] rawPublicKey, byte[] signatureBytes) {
        try {
            requirePublicKey(rawPublicKey);
            if (signatureBytes == null || signatureBytes.length != 64) return false;
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(toPublicKey(rawPublicKey));
            signature.update(JOIN_DOMAIN);
            signature.update(joinPayload(sessionId, nickname, rawPublicKey));
            return signature.verify(signatureBytes);
        } catch (Exception failure) {
            return false;
        }
    }

    public static byte[] joinPayload(byte[] sessionId, String nickname, byte[] rawPublicKey) {
        if (sessionId == null || sessionId.length == 0) throw new IllegalArgumentException("sessionId required");
        requirePublicKey(rawPublicKey);
        byte[] nick = canonicalNickname(nickname).getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[sessionId.length + nick.length + rawPublicKey.length];
        System.arraycopy(sessionId, 0, result, 0, sessionId.length);
        System.arraycopy(nick, 0, result, sessionId.length, nick.length);
        System.arraycopy(rawPublicKey, 0, result, sessionId.length + nick.length, rawPublicKey.length);
        return result;
    }

    private static String canonicalNickname(String nickname) {
        String value = Normalizer.normalize(Objects.requireNonNull(nickname, "nickname").trim(),
                Normalizer.Form.NFC);
        if (value.isEmpty()) throw new IllegalArgumentException("nickname required");
        return value;
    }

    private static PublicKey toPublicKey(byte[] raw) throws Exception {
        byte[] x509 = new byte[X509_HEADER.length + raw.length];
        System.arraycopy(X509_HEADER, 0, x509, 0, X509_HEADER.length);
        System.arraycopy(raw, 0, x509, X509_HEADER.length, raw.length);
        return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(x509));
    }

    private static byte[] rawPublicKey(byte[] x509) {
        if (x509 == null || x509.length != X509_HEADER.length + RAW_PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("Unexpected Ed25519 public key encoding");
        }
        byte[] raw = new byte[RAW_PUBLIC_KEY_BYTES];
        System.arraycopy(x509, X509_HEADER.length, raw, 0, raw.length);
        return raw;
    }

    private static void requirePublicKey(byte[] key) {
        if (key == null || key.length != RAW_PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("rawPublicKey must be 32 bytes");
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void restrictToOwner(Path path) {
        try {
            if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
                return;
            }
            AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (view != null) {
                AclEntry owner = AclEntry.newBuilder()
                        .setType(AclEntryType.ALLOW)
                        .setPrincipal(Files.getOwner(path))
                        .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                        .build();
                view.setAcl(List.of(owner));
            }
        } catch (Exception failure) {
            LOGGER.log(Level.WARNING, "Could not restrict identity key permissions on " + path, failure);
        }
    }
}
