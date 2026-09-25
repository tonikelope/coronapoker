/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import com.tonikelope.coronapoker.crypto.AuthenticatedCommandCodec;
import javax.crypto.spec.SecretKeySpec;

/** Authenticated game peer behavior consumed by the canonical dealer. */
public interface GamePeerController {

    String getNick();

    boolean isCpu();

    boolean isExit();

    void setExit(boolean exit);

    boolean isForce_reset_socket();

    boolean isSocketDownOrReconnecting();

    int getNew_hand_ready();

    void setNew_hand_ready(int value);

    int getLatency();

    int getLatency2();

    int getReconnectionCount();

    byte[] getSra_unlock();

    void setSra_unlock(byte[] value);

    byte[] getSra_unlock_community();

    void setSra_unlock_community(byte[] value);

    byte[] getReceived_token();

    void setReceived_token(byte[] value);

    byte[] getIdentity_pubkey();

    SecretKeySpec getAes_key();

    SecretKeySpec getHmac_key();

    /** Legacy encrypted-frame write; true means the write failed. */
    boolean writeCommandFromServer(String encryptedCommand);

    /**
     * Sends one canonical GAME envelope. Classic Swing peers retain their
     * per-socket AES/HMAC frame; a renderer-neutral channel peer can override
     * this seam because its channel is already authenticated and encrypted.
     */
    default boolean writeGameCommandFromServer(String clearCommand, byte[] iv) {
        return writeCommandFromServer(AuthenticatedCommandCodec.encrypt(
                clearCommand, getAes_key(), iv, getHmac_key()));
    }

    void markExitAndNotify(String reason);

    void exitAndCloseSocket();

    void socketClose();

    /** Transport-less CPU placeholder used while replaying recovery data. */
    static GamePeerController recoveryBot(String nickname) {
        return new RecoveryBotPeer(nickname);
    }

    final class RecoveryBotPeer implements GamePeerController {
        private final String nickname;
        private volatile boolean exit;
        private volatile byte[] sraUnlock;
        private volatile byte[] communityUnlock;
        private volatile byte[] receivedToken;

        private RecoveryBotPeer(String nickname) {
            this.nickname = java.util.Objects.requireNonNull(nickname, "nickname");
        }

        @Override public String getNick() { return nickname; }
        @Override public boolean isCpu() { return true; }
        @Override public boolean isExit() { return exit; }
        @Override public void setExit(boolean value) { exit = value; }
        @Override public boolean isForce_reset_socket() { return false; }
        @Override public boolean isSocketDownOrReconnecting() { return false; }
        @Override public int getNew_hand_ready() { return 0; }
        @Override public void setNew_hand_ready(int value) { }
        @Override public int getLatency() { return 0; }
        @Override public int getLatency2() { return 0; }
        @Override public int getReconnectionCount() { return 0; }
        @Override public byte[] getSra_unlock() { return sraUnlock; }
        @Override public void setSra_unlock(byte[] value) { sraUnlock = value; }
        @Override public byte[] getSra_unlock_community() { return communityUnlock; }
        @Override public void setSra_unlock_community(byte[] value) { communityUnlock = value; }
        @Override public byte[] getReceived_token() { return receivedToken; }
        @Override public void setReceived_token(byte[] value) { receivedToken = value; }
        @Override public byte[] getIdentity_pubkey() { return null; }
        @Override public SecretKeySpec getAes_key() { return null; }
        @Override public SecretKeySpec getHmac_key() { return null; }
        @Override public boolean writeCommandFromServer(String command) { return true; }
        @Override public void markExitAndNotify(String reason) { exit = true; }
        @Override public void exitAndCloseSocket() { exit = true; }
        @Override public void socketClose() { }
    }
}
