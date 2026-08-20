/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.util.HashSet;
import java.util.Set;

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
    private final Set<Long> seenCommands = new HashSet<>();

    public GameCommandGate(GameCommandType.Direction direction) {
        this(direction, DEFAULT_MAX_SEEN_COMMANDS);
    }

    GameCommandGate(GameCommandType.Direction direction, int maxSeenCommands) {
        if (direction == null) throw new IllegalArgumentException("direction is required");
        if (maxSeenCommands <= 0) throw new IllegalArgumentException("maxSeenCommands must be positive");
        this.direction = direction;
        this.maxSeenCommands = maxSeenCommands;
    }

    public synchronized Decision accept(String wireName, int id) {
        GameCommandType type = GameCommandType.from(direction, wireName);
        if (type == null) return new Decision(false, false, true);
        long replayKey = (((long) type.ordinal()) << 32) | (id & 0xffff_ffffL);
        if (seenCommands.contains(replayKey)) {
            return new Decision(true, false, false);
        }
        if (seenCommands.size() >= maxSeenCommands) {
            return new Decision(false, false, true);
        }
        seenCommands.add(replayKey);
        return new Decision(true, true, false);
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
