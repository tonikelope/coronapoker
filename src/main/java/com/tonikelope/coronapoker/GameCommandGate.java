/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.util.EnumMap;
import java.util.Map;

/** Registry-first acknowledgement and replay gate for GAME commands. */
public final class GameCommandGate {

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
    private final Map<GameCommandType, Integer> lastIds = new EnumMap<>(GameCommandType.class);

    public GameCommandGate(GameCommandType.Direction direction) {
        if (direction == null) throw new IllegalArgumentException("direction is required");
        this.direction = direction;
    }

    public synchronized Decision accept(String wireName, int id) {
        GameCommandType type = GameCommandType.from(direction, wireName);
        if (type == null) return new Decision(false, false, true);
        Integer previous = lastIds.put(type, id);
        return new Decision(true, previous == null || previous.intValue() != id, false);
    }

    /** Any rate-limited GAME frame is critical: close, never silently drop. */
    public synchronized Decision rejectForRateLimit(String wireName) {
        return new Decision(false, false, true);
    }

    public synchronized int dedupSize() {
        return lastIds.size();
    }
}
