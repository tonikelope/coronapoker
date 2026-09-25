/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.identity;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/** Canonical Ed25519 domains and payloads shared by both frontends. */
public final class GameIdentityProtocol {

    private static final int HAND_ID_BYTES = 16;
    private static final int RAW_PUBLIC_KEY_BYTES = 32;
    private static final int SIGNATURE_BYTES = 64;
    private static final byte[] X509_HEADER = {
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };
    private static final byte[] ACTION = domain("ACTION");
    private static final byte[] RECEIPT = domain("RECEIPT");
    private static final byte[] SHOWDOWN = domain("SHOWDOWN");
    private static final byte[] STRADDLE = domain("STRADDLE");
    private static final byte[] RABBIT = domain("RABBIT");
    private static final byte[] SEAT_DRAW = domain("SEATDRAW");

    private GameIdentityProtocol() { }

    public static byte[] signAction(PrivateKey key, byte[] record) {
        if (record == null || record.length == 0) {
            throw new IllegalArgumentException("record required");
        }
        return sign(key, ACTION, record);
    }

    public static boolean verifyAction(byte[] publicKey, byte[] record, byte[] signature) {
        return record != null && record.length > 0
                && verify(publicKey, ACTION, record, signature);
    }

    public static byte[] signReceipt(PrivateKey key, byte[] handId, byte[] finalHash,
            byte flags) {
        return sign(key, RECEIPT, receiptPayload(handId, finalHash, flags));
    }

    public static boolean verifyReceipt(byte[] publicKey, byte[] handId, byte[] finalHash,
            byte flags, byte[] signature) {
        try {
            return verify(publicKey, RECEIPT,
                    receiptPayload(handId, finalHash, flags), signature);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public static byte[] signShowdownReveal(PrivateKey key, byte[] handId,
            String nickname, byte[] pocketKey, int firstCard, int secondCard) {
        return sign(key, SHOWDOWN, showdownPayload(handId, nickname, pocketKey,
                firstCard, secondCard));
    }

    public static boolean verifyShowdownReveal(byte[] publicKey, byte[] handId,
            String nickname, byte[] pocketKey, int firstCard, int secondCard,
            byte[] signature) {
        try {
            return verify(publicKey, SHOWDOWN, showdownPayload(handId, nickname,
                    pocketKey, firstCard, secondCard), signature);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public static byte[] signStraddleDecision(PrivateKey key, byte[] handId,
            String nickname, int decision) {
        return sign(key, STRADDLE, straddlePayload(handId, nickname, decision));
    }

    public static boolean verifyStraddleDecision(byte[] publicKey, byte[] handId,
            String nickname, int decision, byte[] signature) {
        try {
            return verify(publicKey, STRADDLE,
                    straddlePayload(handId, nickname, decision), signature);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public static byte[] signRabbitRequest(PrivateKey key, byte[] handId,
            String nickname, byte[] nonce) {
        return sign(key, RABBIT, rabbitPayload(handId, nickname, nonce));
    }

    public static boolean verifyRabbitRequest(byte[] publicKey, byte[] handId,
            String nickname, byte[] nonce, byte[] signature) {
        try {
            return verify(publicKey, RABBIT,
                    rabbitPayload(handId, nickname, nonce), signature);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public static byte[] signSeatCommit(PrivateKey key, byte[] nonce,
            String nickname, byte[] commitment) {
        return sign(key, SEAT_DRAW, seatCommitPayload(nonce, nickname, commitment));
    }

    public static boolean verifySeatCommit(byte[] publicKey, byte[] nonce,
            String nickname, byte[] commitment, byte[] signature) {
        try {
            return verify(publicKey, SEAT_DRAW,
                    seatCommitPayload(nonce, nickname, commitment), signature);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static byte[] receiptPayload(byte[] handId, byte[] finalHash, byte flags) {
        requireLength(handId, HAND_ID_BYTES, "handId");
        requireLength(finalHash, 32, "finalHash");
        byte[] payload = new byte[HAND_ID_BYTES + 32 + 1];
        System.arraycopy(handId, 0, payload, 0, HAND_ID_BYTES);
        System.arraycopy(finalHash, 0, payload, HAND_ID_BYTES, 32);
        payload[payload.length - 1] = flags;
        return payload;
    }

    private static byte[] showdownPayload(byte[] handId, String nickname,
            byte[] pocketKey, int firstCard, int secondCard) {
        requireLength(handId, HAND_ID_BYTES, "handId");
        requireNickname(nickname);
        requireLength(pocketKey, 32, "pocketKey");
        if (firstCard < 0 || firstCard > 51 || secondCard < 0 || secondCard > 51
                || firstCard == secondCard) {
            throw new IllegalArgumentException("showdown cards must be distinct indices");
        }
        byte[] nick = nickname.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[HAND_ID_BYTES + nick.length + 32 + 2];
        int offset = copy(handId, payload, 0);
        offset = copy(nick, payload, offset);
        offset = copy(pocketKey, payload, offset);
        payload[offset] = (byte) Math.min(firstCard, secondCard);
        payload[offset + 1] = (byte) Math.max(firstCard, secondCard);
        return payload;
    }

    private static byte[] straddlePayload(byte[] handId, String nickname, int decision) {
        requireLength(handId, HAND_ID_BYTES, "handId");
        requireNickname(nickname);
        byte[] nick = nickname.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[HAND_ID_BYTES + nick.length + 1];
        int offset = copy(handId, payload, 0);
        offset = copy(nick, payload, offset);
        payload[offset] = (byte) decision;
        return payload;
    }

    private static byte[] rabbitPayload(byte[] handId, String nickname, byte[] nonce) {
        requireLength(handId, HAND_ID_BYTES, "handId");
        requireNickname(nickname);
        requireLength(nonce, 16, "nonce");
        byte[] nick = nickname.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[HAND_ID_BYTES + nick.length + 16];
        int offset = copy(handId, payload, 0);
        offset = copy(nick, payload, offset);
        copy(nonce, payload, offset);
        return payload;
    }

    private static byte[] seatCommitPayload(byte[] nonce, String nickname,
            byte[] commitment) {
        requireLength(nonce, 32, "nonce");
        requireNickname(nickname);
        requireLength(commitment, 32, "commitment");
        byte[] nick = nickname.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[32 + nick.length + 32];
        int offset = copy(nonce, payload, 0);
        offset = copy(nick, payload, offset);
        copy(commitment, payload, offset);
        return payload;
    }

    private static byte[] sign(PrivateKey key, byte[] domain, byte[] payload) {
        if (key == null) throw new IllegalArgumentException("private key required");
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(key);
            signer.update(domain);
            signer.update(payload);
            return signer.sign();
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot sign game identity payload", failure);
        }
    }

    private static boolean verify(byte[] publicKey, byte[] domain, byte[] payload,
            byte[] signature) {
        if (publicKey == null || publicKey.length != RAW_PUBLIC_KEY_BYTES
                || signature == null || signature.length != SIGNATURE_BYTES) {
            return false;
        }
        try {
            byte[] encoded = new byte[X509_HEADER.length + publicKey.length];
            System.arraycopy(X509_HEADER, 0, encoded, 0, X509_HEADER.length);
            System.arraycopy(publicKey, 0, encoded, X509_HEADER.length, publicKey.length);
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(
                    new X509EncodedKeySpec(encoded)));
            verifier.update(domain);
            verifier.update(payload);
            return verifier.verify(signature);
        } catch (Exception invalid) {
            return false;
        }
    }

    private static byte[] domain(String name) {
        return (name + '\0').getBytes(StandardCharsets.UTF_8);
    }

    private static void requireLength(byte[] value, int length, String name) {
        if (value == null || value.length != length) {
            throw new IllegalArgumentException(name + " must be " + length + " bytes");
        }
    }

    private static void requireNickname(String nickname) {
        if (nickname == null || nickname.isEmpty()) {
            throw new IllegalArgumentException("nickname required");
        }
    }

    private static int copy(byte[] source, byte[] target, int offset) {
        System.arraycopy(source, 0, target, offset, source.length);
        return offset + source.length;
    }
}
