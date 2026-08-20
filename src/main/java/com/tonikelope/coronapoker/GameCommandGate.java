/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/** Registry-first acknowledgement and replay gate for GAME commands. */
public final class GameCommandGate {

    private static final int DEFAULT_MAX_SEEN_COMMANDS = 65_536;

    public static final class Decision {
        private final boolean acknowledge;
        private final boolean enqueue;
        private final boolean closeConnection;

        private Decision(boolean acknowledge, boolean enqueue, boolean closeConnection) {
            this.acknowledge = acknowledge;
            this.enqueue = enqueue;
            this.closeConnection = closeConnection;
        }

        public boolean acknowledge() { return acknowledge; }
        public boolean enqueue() { return enqueue; }
        public boolean closeConnection() { return closeConnection; }
    }

    private final GameCommandType.Direction direction;
    private final int maxSeenCommands;
    private final Map<Long, byte[]> seenCommands = new HashMap<>();

    public GameCommandGate(GameCommandType.Direction direction) {
        this(direction, DEFAULT_MAX_SEEN_COMMANDS);
    }

    GameCommandGate(GameCommandType.Direction direction, int maxSeenCommands) {
        if (direction == null) throw new IllegalArgumentException("direction is required");
        if (maxSeenCommands <= 0) throw new IllegalArgumentException("maxSeenCommands must be positive");
        this.direction = direction;
        this.maxSeenCommands = maxSeenCommands;
    }

    public synchronized Decision accept(String wireName, int id, String authenticatedFrame) {
        GameCommandType type = GameCommandType.from(direction, wireName);
        if (type == null || authenticatedFrame == null) {
            return new Decision(false, false, true);
        }
        long replayKey = (((long) type.ordinal()) << 32) | (id & 0xffff_ffffL);
        byte[] frameFingerprint = fingerprint(authenticatedFrame);
        byte[] previous = seenCommands.get(replayKey);
        if (previous != null) {
            // Retransmission of the exact authenticated frame is idempotent. Reusing an
            // ID for different bytes is either a sender bug/collision or a replay attempt;
            // silently treating it as the first command would lose critical traffic.
            if (MessageDigest.isEqual(previous, frameFingerprint)) {
                return new Decision(true, false, false);
            }
            return new Decision(false, false, true);
        }
        if (seenCommands.size() >= maxSeenCommands) {
            return new Decision(false, false, true);
        }
        seenCommands.put(replayKey, frameFingerprint);
        return new Decision(true, true, false);
    }

    private static byte[] fingerprint(String authenticatedFrame) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(authenticatedFrame.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /** Any rate-limited GAME frame is critical: close, never silently drop. */
    public synchronized Decision rejectForRateLimit(String wireName) {
        return new Decision(false, false, true);
    }

    /** A known critical command with an invalid payload or sender is fatal. */
    public synchronized Decision rejectCriticalViolation() {
        return new Decision(false, false, true);
    }

    public synchronized int dedupSize() {
        return seenCommands.size();
    }
}
