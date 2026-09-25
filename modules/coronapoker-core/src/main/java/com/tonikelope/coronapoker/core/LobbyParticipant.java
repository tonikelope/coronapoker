package com.tonikelope.coronapoker.core;

import java.nio.file.Path;
import java.util.Objects;

/** Renderer-neutral row in the waiting-room participant list. */
public record LobbyParticipant(String nickname, Path avatar, boolean local,
        boolean host, boolean bot, boolean connected, boolean asyncWaiting,
        boolean secure, int latency, int previousLatency,
        byte[] identityPublicKey, byte[] sessionFingerprint) {

    public static final int NO_LATENCY = -2;

    public LobbyParticipant {
        nickname = Objects.requireNonNull(nickname, "nickname").trim();
        if (nickname.isEmpty()) {
            throw new IllegalArgumentException("Participant nickname is required");
        }
        avatar = avatar == null ? null : avatar.toAbsolutePath().normalize();
        if (bot && !nickname.contains("$")) {
            throw new IllegalArgumentException("Bot nicknames use the reserved dollar marker");
        }
        if (!bot && nickname.contains("$")) {
            throw new IllegalArgumentException("Human nicknames cannot use the bot marker");
        }
        if (latency < NO_LATENCY || previousLatency < NO_LATENCY) {
            throw new IllegalArgumentException("Invalid latency sentinel");
        }
        identityPublicKey = identityPublicKey == null
                ? null : identityPublicKey.clone();
        sessionFingerprint = sessionFingerprint == null
                ? null : sessionFingerprint.clone();
        if (sessionFingerprint != null && sessionFingerprint.length != 32) {
            throw new IllegalArgumentException(
                    "Session fingerprint must be a SHA-256 digest");
        }
    }

    /** Backwards-compatible constructor for non-network and test lobbies. */
    public LobbyParticipant(String nickname, Path avatar, boolean local,
            boolean host, boolean bot, boolean connected, boolean asyncWaiting,
            boolean secure, int latency, int previousLatency) {
        this(nickname, avatar, local, host, bot, connected, asyncWaiting,
                secure, latency, previousLatency, null, null);
    }

    /** Backwards-compatible constructor for identity-aware participants. */
    public LobbyParticipant(String nickname, Path avatar, boolean local,
            boolean host, boolean bot, boolean connected, boolean asyncWaiting,
            boolean secure, int latency, int previousLatency,
            byte[] identityPublicKey) {
        this(nickname, avatar, local, host, bot, connected, asyncWaiting,
                secure, latency, previousLatency, identityPublicKey, null);
    }

    @Override
    public byte[] identityPublicKey() {
        return identityPublicKey == null ? null : identityPublicKey.clone();
    }

    @Override
    public byte[] sessionFingerprint() {
        return sessionFingerprint == null ? null : sessionFingerprint.clone();
    }

    public boolean latencyAvailable() {
        return latency != NO_LATENCY;
    }
}
