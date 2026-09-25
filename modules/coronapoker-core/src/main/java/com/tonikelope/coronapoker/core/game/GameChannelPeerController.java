/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import com.tonikelope.coronapoker.core.network.ConfirmationTracker;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.spec.SecretKeySpec;

/** Remote human carried by the authenticated renderer-neutral game channel. */
public final class GameChannelPeerController implements GamePeerController {

    private final String nickname;
    private final GameChannel channel;
    private final ConfirmationTracker confirmations;
    private final byte[] identityPublicKey;
    private final int latency;
    private final int previousLatency;
    private final AtomicBoolean exit = new AtomicBoolean();
    private final AtomicInteger newHandReady = new AtomicInteger();
    private volatile byte[] sraUnlock;
    private volatile byte[] communityUnlock;
    private volatile byte[] receivedToken;

    public GameChannelPeerController(String nickname, GameChannel channel,
            ConfirmationTracker confirmations, byte[] identityPublicKey,
            int latency, int previousLatency) {
        this.nickname = requireNickname(nickname);
        this.channel = Objects.requireNonNull(channel, "channel");
        this.confirmations = Objects.requireNonNull(confirmations,
                "confirmations");
        if (identityPublicKey == null || identityPublicKey.length == 0) {
            throw new IllegalArgumentException(
                    "Remote human identity public key is required");
        }
        this.identityPublicKey = identityPublicKey.clone();
        this.latency = latency;
        this.previousLatency = previousLatency;
    }

    private static String requireNickname(String value) {
        String checked = Objects.requireNonNull(value, "nickname").trim();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException("nickname is required");
        }
        return checked;
    }

    @Override public String getNick() { return nickname; }
    @Override public boolean isCpu() { return false; }
    @Override public boolean isExit() { return exit.get(); }
    @Override public void setExit(boolean value) { exit.set(value); }
    @Override public boolean isForce_reset_socket() { return false; }
    @Override public boolean isSocketDownOrReconnecting() {
        return !exit.get() && channel.isPeerReconnecting(nickname);
    }
    @Override public int getNew_hand_ready() { return newHandReady.get(); }
    @Override public void setNew_hand_ready(int value) { newHandReady.set(value); }
    @Override public int getLatency() {
        int current = channel.peerLatency(nickname);
        return current == Integer.MIN_VALUE ? latency : current;
    }
    @Override public int getLatency2() {
        int current = channel.peerSecondaryLatency(nickname);
        return current == Integer.MIN_VALUE ? previousLatency : current;
    }
    @Override public int getReconnectionCount() {
        return channel.peerReconnectionCount(nickname);
    }
    @Override public byte[] getSra_unlock() { return cloneBytes(sraUnlock); }
    @Override public void setSra_unlock(byte[] value) { sraUnlock = cloneBytes(value); }
    @Override public byte[] getSra_unlock_community() { return cloneBytes(communityUnlock); }
    @Override public void setSra_unlock_community(byte[] value) {
        communityUnlock = cloneBytes(value);
    }
    @Override public byte[] getReceived_token() { return cloneBytes(receivedToken); }
    @Override public void setReceived_token(byte[] value) {
        receivedToken = cloneBytes(value);
    }
    @Override public byte[] getIdentity_pubkey() {
        return identityPublicKey.clone();
    }

    /* Native GameChannel owns its session keys and framing. */
    @Override public SecretKeySpec getAes_key() { return null; }
    @Override public SecretKeySpec getHmac_key() { return null; }

    /** Encrypted legacy frames must never be fed into the native channel. */
    @Override public boolean writeCommandFromServer(String encryptedCommand) {
        return true;
    }

    @Override
    public boolean writeGameCommandFromServer(String clearCommand, byte[] iv) {
        ParsedEnvelope envelope;
        try {
            envelope = ParsedEnvelope.parse(clearCommand);
            channel.sendFromHost(nickname, envelope.command())
                    .whenComplete((ignored, failure) -> {
                        if (failure == null) {
                            confirmations.confirm(nickname,
                                    envelope.commandId() + 1);
                        } else {
                            markExitAndNotify("native game-channel write failed");
                        }
                    });
            return false;
        } catch (IOException | RuntimeException failure) {
            markExitAndNotify("native game-channel write failed");
            return true;
        }
    }

    @Override
    public void markExitAndNotify(String reason) {
        exit.set(true);
        confirmations.wakeAll();
    }

    @Override public void exitAndCloseSocket() { markExitAndNotify("peer exit"); }
    @Override public void socketClose() { markExitAndNotify("peer socket closed"); }

    private static byte[] cloneBytes(byte[] value) {
        return value == null ? null : value.clone();
    }

    private record ParsedEnvelope(int commandId, String command) {
        static ParsedEnvelope parse(String clearCommand) {
            String[] parts = Objects.requireNonNull(clearCommand,
                    "clearCommand").split("#", 3);
            if (parts.length != 3 || !"GAME".equals(parts[0])
                    || parts[2].isBlank()) {
                throw new IllegalArgumentException(
                        "Invalid canonical GAME envelope");
            }
            int id = Integer.parseInt(parts[1]);
            if (id < 0) {
                throw new IllegalArgumentException("Invalid GAME command id");
            }
            return new ParsedEnvelope(id, parts[2]);
        }
    }
}
