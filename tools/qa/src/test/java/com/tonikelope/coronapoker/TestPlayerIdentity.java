package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.identity.GameIdentityProtocol;
import com.tonikelope.coronapoker.core.identity.PlayerIdentity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

/** Test fixture over the production core identity implementation. */
public final class TestPlayerIdentity {

    private final PlayerIdentity identity;
    private final String loadError;

    private TestPlayerIdentity(PlayerIdentity identity, String loadError) {
        this.identity = identity;
        this.loadError = loadError;
    }

    public static TestPlayerIdentity initializeForNick(String nickname) {
        try {
            Path directory = Path.of(System.getProperty("user.home"), ".coronapoker");
            return new TestPlayerIdentity(PlayerIdentity.loadOrCreate(directory, nickname), null);
        } catch (IOException failure) {
            return new TestPlayerIdentity(null, failure.toString());
        }
    }

    public boolean isReady() {
        return identity != null;
    }

    public String getLoadError() {
        return loadError;
    }

    public byte[] getPublicKey() {
        requireReady();
        return identity.getPublicKey();
    }

    public byte[] signAction(byte[] record) {
        requireReady();
        return identity.signAction(record);
    }

    public byte[] signReceipt(byte[] handId, byte[] finalHash, byte flags) {
        requireReady();
        return identity.signReceipt(handId, finalHash, flags);
    }

    public byte[] signShowdownReveal(byte[] handId, String nickname, byte[] pocketKey,
            int firstCard, int secondCard) {
        requireReady();
        return identity.signShowdownReveal(handId, nickname, pocketKey, firstCard, secondCard);
    }

    public byte[] signStraddleDecision(byte[] handId, String nickname, int decision) {
        requireReady();
        return identity.signStraddleDecision(handId, nickname, decision);
    }

    public byte[] signRabbitRequest(byte[] handId, String nickname, byte[] nonce) {
        requireReady();
        return identity.signRabbitRequest(handId, nickname, nonce);
    }

    public byte[] signSeatCommit(byte[] nonce, String nickname, byte[] commitment) {
        requireReady();
        return identity.signSeatCommit(nonce, nickname, commitment);
    }

    public byte[] signJoin(byte[] sessionId, String nickname) {
        requireReady();
        if (!identity.nickname().equals(nickname)) {
            throw new IllegalArgumentException("nickname does not match identity");
        }
        return identity.signJoin(sessionId);
    }

    public static boolean verifyAction(byte[] key, byte[] record, byte[] signature) {
        return GameIdentityProtocol.verifyAction(key, record, signature);
    }

    public static boolean verifyReceipt(byte[] key, byte[] handId, byte[] finalHash,
            byte flags, byte[] signature) {
        return GameIdentityProtocol.verifyReceipt(key, handId, finalHash, flags, signature);
    }

    public static boolean verifyShowdownReveal(byte[] key, byte[] handId, String nickname,
            byte[] pocketKey, int firstCard, int secondCard, byte[] signature) {
        return GameIdentityProtocol.verifyShowdownReveal(key, handId, nickname, pocketKey,
                firstCard, secondCard, signature);
    }

    public static boolean verifyStraddleDecision(byte[] key, byte[] handId, String nickname,
            int decision, byte[] signature) {
        return GameIdentityProtocol.verifyStraddleDecision(key, handId, nickname, decision,
                signature);
    }

    public static boolean verifyRabbitRequest(byte[] key, byte[] handId, String nickname,
            byte[] nonce, byte[] signature) {
        return GameIdentityProtocol.verifyRabbitRequest(key, handId, nickname, nonce, signature);
    }

    public static boolean verifySeatCommit(byte[] key, byte[] nonce, String nickname,
            byte[] commitment, byte[] signature) {
        return GameIdentityProtocol.verifySeatCommit(key, nonce, nickname, commitment, signature);
    }

    public static byte[] joinPayload(byte[] sessionId, String nickname, byte[] key) {
        return PlayerIdentity.joinPayload(sessionId, nickname, key);
    }

    public static boolean verifyJoin(byte[] sessionId, String nickname, byte[] key,
            byte[] signature) {
        return PlayerIdentity.verifyJoin(sessionId, nickname, key, signature);
    }

    public static byte[] x509PubKeyToRaw(byte[] encoded) {
        if (encoded == null || encoded.length < 32) {
            throw new IllegalArgumentException("invalid Ed25519 public key");
        }
        return Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length);
    }

    public static byte[] receiptPayload(byte[] handId, byte[] finalHash, byte flags) {
        requireLength(handId, 16, "handId");
        requireLength(finalHash, 32, "finalHash");
        byte[] payload = new byte[49];
        System.arraycopy(handId, 0, payload, 0, 16);
        System.arraycopy(finalHash, 0, payload, 16, 32);
        payload[48] = flags;
        return payload;
    }

    public static byte[] showdownPayload(byte[] handId, String nickname, byte[] pocketKey,
            int firstCard, int secondCard) {
        requireLength(handId, 16, "handId");
        requireLength(pocketKey, 32, "pocketKey");
        if (nickname == null || nickname.isEmpty() || firstCard < 0 || firstCard > 51
                || secondCard < 0 || secondCard > 51 || firstCard == secondCard) {
            throw new IllegalArgumentException("invalid showdown reveal");
        }
        byte[] nick = nickname.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[16 + nick.length + 34];
        System.arraycopy(handId, 0, payload, 0, 16);
        System.arraycopy(nick, 0, payload, 16, nick.length);
        System.arraycopy(pocketKey, 0, payload, 16 + nick.length, 32);
        payload[payload.length - 2] = (byte) Math.min(firstCard, secondCard);
        payload[payload.length - 1] = (byte) Math.max(firstCard, secondCard);
        return payload;
    }

    private void requireReady() {
        if (identity == null) {
            throw new IllegalStateException(loadError);
        }
    }

    private static void requireLength(byte[] value, int length, String name) {
        if (value == null || value.length != length) {
            throw new IllegalArgumentException(name + " must be " + length + " bytes");
        }
    }
}
