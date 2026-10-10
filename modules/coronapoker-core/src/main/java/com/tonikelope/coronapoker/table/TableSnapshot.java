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
        List<CardSnapshot> communityCards,
        TablePresentation presentation) {

    public TableSnapshot(long revision, String localNickname, Street street,
            double pot, String currentTurnNickname, boolean paused,
            List<PlayerSnapshot> players,
            List<CardSnapshot> communityCards) {
        this(revision, localNickname, street, pot, currentTurnNickname,
                paused, players, communityCards, TablePresentation.EMPTY);
    }

    public TableSnapshot {
        Objects.requireNonNull(localNickname, "localNickname");
        Objects.requireNonNull(street, "street");
        players = List.copyOf(players);
        communityCards = List.copyOf(communityCards);
        Objects.requireNonNull(presentation, "presentation");
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

    /**
     * Canonical hand decision that survives a mid-hand table bootstrap.
     *
     * <p>{@code active} describes whether a seat belongs to the running game;
     * it deliberately remains {@code true} after a fold.  Renderers therefore
     * need this separate semantic state to reconstruct the same folded/all-in
     * presentation that an already connected client obtained from ordered
     * visual events.</p>
     */
    public enum Decision {
        NONE,
        FOLD,
        CHECK,
        BET,
        ALL_IN
    }

    /** Exact renderer-neutral meaning of the current street action. */
    public enum ActionKind {
        NONE,
        FOLD,
        CHECK,
        CALL,
        BET,
        RAISE,
        RERAISE,
        ALL_IN
    }

    /** Durable public rebuy presentation for one seat. */
    public enum RebuyPhase {
        NONE,
        WAITING,
        REBOUGHT
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

    /**
     * Public table presentation that cannot be reconstructed from cards and
     * balances alone when a renderer attaches in the middle of a hand.
     */
    public record TablePresentation(String potPrefix,
            int sharedProgressRemainingSeconds,
            List<Integer> rabbitCardSlots) {

        public static final TablePresentation EMPTY
                = new TablePresentation("", 0, List.of());

        public TablePresentation(String potPrefix,
                int sharedProgressRemainingSeconds) {
            this(potPrefix, sharedProgressRemainingSeconds, List.of());
        }

        public TablePresentation {
            potPrefix = Objects.requireNonNullElse(potPrefix, "");
            if (sharedProgressRemainingSeconds < 0) {
                throw new IllegalArgumentException(
                        "Shared progress cannot be negative");
            }
            rabbitCardSlots = PlayerPresentation.checkedSlots(
                    rabbitCardSlots, 5, "rabbit card slots");
        }
    }

    /**
     * Hand-scoped public presentation for one seat.  These facts are kept in
     * the neutral snapshot so a late renderer does not have to guess them from
     * translated labels or from whether the seat is still active.
     */
    public record PlayerPresentation(
            boolean showingCards,
            boolean partialHand,
            float partialWinPercentage,
            boolean resultResolved,
            String publicHandName,
            List<Integer> wonPotIndexes,
            boolean returnedSidePot,
            boolean showdownHighlightEnabled,
            List<Integer> winningHoleCardSlots,
            List<Integer> winningCommunityCardSlots,
            RebuyPhase rebuyPhase,
            int immediateRebuyAmount,
            String publicActionLabel) {

        public static final PlayerPresentation EMPTY = new PlayerPresentation(
                false, false, -1f, false, "", List.of(), false, false,
                List.of(), List.of(), RebuyPhase.NONE, 0, "");

        public PlayerPresentation(boolean showingCards, boolean partialHand,
                float partialWinPercentage, boolean resultResolved,
                String publicHandName, List<Integer> wonPotIndexes,
                boolean returnedSidePot, boolean showdownHighlightEnabled,
                List<Integer> winningHoleCardSlots,
                List<Integer> winningCommunityCardSlots,
                RebuyPhase rebuyPhase, int immediateRebuyAmount) {
            this(showingCards, partialHand, partialWinPercentage,
                    resultResolved, publicHandName, wonPotIndexes,
                    returnedSidePot, showdownHighlightEnabled,
                    winningHoleCardSlots, winningCommunityCardSlots,
                    rebuyPhase, immediateRebuyAmount, "");
        }

        public PlayerPresentation(boolean showingCards, boolean partialHand,
                float partialWinPercentage, boolean resultResolved,
                String publicHandName, List<Integer> wonPotIndexes,
                boolean returnedSidePot, boolean showdownHighlightEnabled,
                List<Integer> winningHoleCardSlots,
                List<Integer> winningCommunityCardSlots) {
            this(showingCards, partialHand, partialWinPercentage,
                    resultResolved, publicHandName, wonPotIndexes,
                    returnedSidePot, showdownHighlightEnabled,
                    winningHoleCardSlots, winningCommunityCardSlots,
                    RebuyPhase.NONE, 0, "");
        }

        public PlayerPresentation {
            publicHandName = Objects.requireNonNullElse(publicHandName, "");
            publicActionLabel = Objects.requireNonNullElse(
                    publicActionLabel, "");
            Objects.requireNonNull(rebuyPhase, "rebuyPhase");
            if (immediateRebuyAmount < 0) {
                throw new IllegalArgumentException(
                        "Immediate rebuy amount cannot be negative");
            }
            if (!Float.isFinite(partialWinPercentage)
                    || partialWinPercentage < -1f
                    || partialWinPercentage > 100f) {
                throw new IllegalArgumentException(
                        "Invalid partial win percentage");
            }
            if (!partialHand && Float.compare(partialWinPercentage, -1f) != 0) {
                throw new IllegalArgumentException(
                        "A non-partial hand cannot carry a percentage");
            }
            wonPotIndexes = checkedOrderedPositive(wonPotIndexes,
                    "won pot indexes");
            winningHoleCardSlots = checkedSlots(winningHoleCardSlots, 2,
                    "winning hole-card slots");
            winningCommunityCardSlots = checkedSlots(
                    winningCommunityCardSlots, 5,
                    "winning community-card slots");
            if (!showdownHighlightEnabled
                    && (!winningHoleCardSlots.isEmpty()
                    || !winningCommunityCardSlots.isEmpty())) {
                throw new IllegalArgumentException(
                        "Disabled showdown highlight cannot carry slots");
            }
        }

        private static List<Integer> checkedOrderedPositive(
                List<Integer> values, String label) {
            List<Integer> copy = List.copyOf(Objects.requireNonNull(values,
                    label));
            int previous = 0;
            for (Integer value : copy) {
                if (value == null || value <= previous) {
                    throw new IllegalArgumentException(label
                            + " must be positive, unique and ordered");
                }
                previous = value;
            }
            return copy;
        }

        private static List<Integer> checkedSlots(List<Integer> values,
                int size, String label) {
            List<Integer> copy = List.copyOf(Objects.requireNonNull(values,
                    label));
            int previous = -1;
            for (Integer value : copy) {
                if (value == null || value <= previous || value >= size) {
                    throw new IllegalArgumentException(label
                            + " must be unique ordered slots");
                }
                previous = value;
            }
            return copy;
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
            boolean underTheGun,
            Position position,
            Decision decision,
            ActionKind actionKind,
            String lastAction,
            String handName,
            List<CardSnapshot> holeCards,
            int buyIn,
            int rebuyCount,
            boolean warming,
            PlayerPresentation presentation) {

        public PlayerSnapshot(String nickname, double stack,
                double streetBet, double potContribution, boolean active,
                boolean spectator, boolean exited, boolean timedOut,
                int latency, int previousLatency, int reconnectionCount,
                long telemetryAt, boolean winner, boolean underTheGun,
                Position position, Decision decision, ActionKind actionKind,
                String lastAction, String handName,
                List<CardSnapshot> holeCards, int buyIn, int rebuyCount,
                boolean warming) {
            this(nickname, stack, streetBet, potContribution, active,
                    spectator, exited, timedOut, latency, previousLatency,
                    reconnectionCount, telemetryAt, winner, underTheGun,
                    position, decision, actionKind, lastAction, handName,
                    holeCards, buyIn, rebuyCount, warming,
                    PlayerPresentation.EMPTY);
        }

        public PlayerSnapshot(String nickname, double stack,
                double streetBet, double potContribution, boolean active,
                boolean spectator, boolean exited, boolean timedOut,
                int latency, int previousLatency, int reconnectionCount,
                long telemetryAt, boolean winner, boolean underTheGun,
                Position position, String lastAction, String handName,
                List<CardSnapshot> holeCards, int buyIn, int rebuyCount,
                boolean warming) {
            this(nickname, stack, streetBet, potContribution, active,
                    spectator, exited, timedOut, latency, previousLatency,
                    reconnectionCount, telemetryAt, winner, underTheGun,
                    position, Decision.NONE, ActionKind.NONE, lastAction,
                    handName, holeCards,
                    buyIn, rebuyCount, warming);
        }

        public PlayerSnapshot(String nickname, double stack,
                double streetBet, double potContribution, boolean active,
                boolean spectator, boolean exited, boolean timedOut,
                int latency, int previousLatency, int reconnectionCount,
                long telemetryAt, boolean winner, boolean underTheGun,
                Position position, String lastAction, String handName,
                List<CardSnapshot> holeCards, int buyIn, int rebuyCount) {
            this(nickname, stack, streetBet, potContribution, active,
                    spectator, exited, timedOut, latency, previousLatency,
                    reconnectionCount, telemetryAt, winner, underTheGun,
                    position, Decision.NONE, ActionKind.NONE, lastAction,
                    handName, holeCards, buyIn,
                    rebuyCount, false);
        }

        public PlayerSnapshot(String nickname, double stack,
                double streetBet, double potContribution, boolean active,
                boolean spectator, boolean exited, boolean timedOut,
                int latency, int previousLatency, int reconnectionCount,
                long telemetryAt, boolean winner, boolean underTheGun,
                Position position, String lastAction, String handName,
                List<CardSnapshot> holeCards) {
            this(nickname, stack, streetBet, potContribution, active,
                    spectator, exited, timedOut, latency, previousLatency,
                    reconnectionCount, telemetryAt, winner, underTheGun,
                    position, Decision.NONE, ActionKind.NONE, lastAction,
                    handName, holeCards, 0, 0, false);
        }

        public PlayerSnapshot(String nickname, double stack,
                double streetBet, double potContribution, boolean active,
                boolean spectator, boolean exited, boolean timedOut,
                int latency, int previousLatency, int reconnectionCount,
                long telemetryAt, boolean winner, Position position,
                String lastAction, String handName,
                List<CardSnapshot> holeCards) {
            this(nickname, stack, streetBet, potContribution, active,
                    spectator, exited, timedOut, latency, previousLatency,
                    reconnectionCount, telemetryAt, winner, false, position,
                    Decision.NONE, ActionKind.NONE, lastAction, handName,
                    holeCards, 0, 0, false);
        }

        public PlayerSnapshot {
            Objects.requireNonNull(nickname, "nickname");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(actionKind, "actionKind");
            lastAction = lastAction == null ? "" : lastAction;
            handName = handName == null ? "" : handName;
            holeCards = List.copyOf(holeCards);
            buyIn = Math.max(0, buyIn);
            rebuyCount = Math.max(0, rebuyCount);
            warming = warming && spectator && !exited;
            Objects.requireNonNull(presentation, "presentation");
        }
    }
}
