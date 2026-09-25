/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/** Player identity operations required by the canonical game controller. */
public interface GameIdentity {

    boolean isReady();

    String getLoadError();

    byte[] getPublicKey();

    byte[] signAction(byte[] record);

    byte[] signReceipt(byte[] handId, byte[] finalHash, byte flags);

    byte[] signShowdownReveal(byte[] handId, String nickname, byte[] pocketKey,
            int firstCard, int secondCard);

    byte[] signStraddleDecision(byte[] handId, String nickname, int decision);

    byte[] signRabbitRequest(byte[] handId, String nickname, byte[] nonce);

    byte[] signSeatCommit(byte[] nonce, String nickname, byte[] commitment);

    boolean verifyActionSignature(byte[] publicKey, byte[] record, byte[] signature);

    boolean verifyReceiptSignature(byte[] publicKey, byte[] handId, byte[] finalHash,
            byte flags, byte[] signature);

    boolean verifyShowdownRevealSignature(byte[] publicKey, byte[] handId,
            String nickname, byte[] pocketKey, int firstCard, int secondCard,
            byte[] signature);

    boolean verifyStraddleDecisionSignature(byte[] publicKey, byte[] handId,
            String nickname, int decision, byte[] signature);

    boolean verifyRabbitRequestSignature(byte[] publicKey, byte[] handId,
            String nickname, byte[] nonce, byte[] signature);

    boolean verifySeatCommitSignature(byte[] publicKey, byte[] nonce,
            String nickname, byte[] commitment, byte[] signature);

    static GameIdentity unavailable() {
        return UnavailableGameIdentity.INSTANCE;
    }

    final class UnavailableGameIdentity implements GameIdentity {
        private static final UnavailableGameIdentity INSTANCE =
                new UnavailableGameIdentity();

        private UnavailableGameIdentity() { }

        @Override public boolean isReady() { return false; }
        @Override public String getLoadError() { return "Game identity is unavailable"; }
        @Override public byte[] getPublicKey() { return null; }
        @Override public byte[] signAction(byte[] record) { return null; }
        @Override public byte[] signReceipt(byte[] handId, byte[] finalHash, byte flags) { return null; }
        @Override public byte[] signShowdownReveal(byte[] handId, String nickname,
                byte[] pocketKey, int firstCard, int secondCard) { return null; }
        @Override public byte[] signStraddleDecision(byte[] handId, String nickname,
                int decision) { return null; }
        @Override public byte[] signRabbitRequest(byte[] handId, String nickname,
                byte[] nonce) { return null; }
        @Override public byte[] signSeatCommit(byte[] nonce, String nickname,
                byte[] commitment) { return null; }
        @Override public boolean verifyActionSignature(byte[] publicKey, byte[] record,
                byte[] signature) { return false; }
        @Override public boolean verifyReceiptSignature(byte[] publicKey, byte[] handId,
                byte[] finalHash, byte flags, byte[] signature) { return false; }
        @Override public boolean verifyShowdownRevealSignature(byte[] publicKey,
                byte[] handId, String nickname, byte[] pocketKey, int firstCard,
                int secondCard, byte[] signature) { return false; }
        @Override public boolean verifyStraddleDecisionSignature(byte[] publicKey,
                byte[] handId, String nickname, int decision, byte[] signature) { return false; }
        @Override public boolean verifyRabbitRequestSignature(byte[] publicKey,
                byte[] handId, String nickname, byte[] nonce, byte[] signature) { return false; }
        @Override public boolean verifySeatCommitSignature(byte[] publicKey, byte[] nonce,
                String nickname, byte[] commitment, byte[] signature) { return false; }
    }
}
