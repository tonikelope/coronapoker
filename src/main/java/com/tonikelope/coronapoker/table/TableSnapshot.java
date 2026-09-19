/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.table;

import java.util.List;
import java.util.Objects;

/**
 * Immutable, renderer-neutral state of a CoronaPoker table.
 *
 * It contains display state only. It is not authoritative game state and a
 * renderer must never feed mutations back into the dealer through it.
 */
public record TableSnapshot(
        long revision,
        String localNickname,
        Street street,
        double pot,
        String currentTurnNickname,
        boolean paused,
        List<PlayerSnapshot> players,
        List<CardSnapshot> communityCards) {

    public TableSnapshot {
        Objects.requireNonNull(localNickname, "localNickname");
        Objects.requireNonNull(street, "street");
        players = List.copyOf(players);
        communityCards = List.copyOf(communityCards);
    }

    public enum Street {
        WAITING,
        PREFLOP,
        FLOP,
        TURN,
        RIVER,
        SHOWDOWN,
        FINISHED
    }

    public enum Position {
        NONE,
        DEALER,
        DEAD_DEALER,
        SMALL_BLIND,
        BIG_BLIND,
        STRADDLE,
        DEALER_STRADDLE
    }

    public record CardSnapshot(String code, boolean faceUp, boolean disabled,
            boolean visible) {

        public CardSnapshot(String code, boolean faceUp, boolean disabled) {
            this(code, faceUp, disabled, true);
        }

        public CardSnapshot {
            code = code == null ? "" : code;
        }
    }

    public record PlayerSnapshot(
            String nickname,
            double stack,
            double streetBet,
            double potContribution,
            boolean active,
            boolean spectator,
            boolean exited,
            boolean timedOut,
            int latency,
            int previousLatency,
            int reconnectionCount,
            long telemetryAt,
            boolean winner,
            Position position,
            String lastAction,
            String handName,
            List<CardSnapshot> holeCards) {

        public PlayerSnapshot {
            Objects.requireNonNull(nickname, "nickname");
            Objects.requireNonNull(position, "position");
            lastAction = lastAction == null ? "" : lastAction;
            handName = handName == null ? "" : handName;
            holeCards = List.copyOf(holeCards);
        }
    }
}
