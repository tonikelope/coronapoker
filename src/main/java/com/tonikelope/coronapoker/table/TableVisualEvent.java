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

/** Semantic visual events emitted by the unchanged game flow. */
public sealed interface TableVisualEvent permits TableVisualEvent.Synchronize,
        TableVisualEvent.HandBoundary, TableVisualEvent.Shuffle,
        TableVisualEvent.MovePosition, TableVisualEvent.PositionRotation,
        TableVisualEvent.PostChips,
        TableVisualEvent.CollectBets, TableVisualEvent.DealHoleCard,
        TableVisualEvent.DealCommunityCard,
        TableVisualEvent.SwapHoleCards, TableVisualEvent.FoldHoleCards,
        TableVisualEvent.RevealCommunityCards, TableVisualEvent.TurnTimer,
        TableVisualEvent.PlayerAction, TableVisualEvent.Cinematic,
        TableVisualEvent.RevealHoleCards, TableVisualEvent.HandResult,
        TableVisualEvent.ShowdownHighlight, TableVisualEvent.Payout,
        TableVisualEvent.DeckChanged, TableVisualEvent.SeatRoster,
        TableVisualEvent.CloseTable {

    long sequence();

    record Synchronize(long sequence, TableSnapshot snapshot) implements TableVisualEvent {

        public Synchronize {
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    record HandBoundary(long sequence, long handId, Phase phase) implements TableVisualEvent {

        public HandBoundary {
            Objects.requireNonNull(phase, "phase");
        }

        public enum Phase {
            PREPARE,
            START,
            END
        }
    }

    record Shuffle(long sequence, String deck, Phase phase) implements TableVisualEvent {

        public Shuffle {
            Objects.requireNonNull(deck, "deck");
            Objects.requireNonNull(phase, "phase");
        }

        public enum Phase {
            START,
            FINISH
        }
    }

    record MovePosition(long sequence, String nickname,
            TableSnapshot.Position position) implements TableVisualEvent {

        public MovePosition {
            Objects.requireNonNull(nickname, "nickname");
            Objects.requireNonNull(position, "position");
        }
    }

    /** Dealer/SB/BB move in one parallel batch before forced bets are posted. */
    record PositionRotation(long sequence, List<PositionTransfer> transfers,
            long durationMillis) implements TableVisualEvent {

        public PositionRotation {
            transfers = List.copyOf(transfers);
            if (transfers.isEmpty()) {
                throw new IllegalArgumentException("Position rotation needs at least one transfer");
            }
            if (durationMillis <= 0L) {
                throw new IllegalArgumentException("Position rotation duration must be positive");
            }
        }
    }

    record PositionTransfer(String fromNickname, String toNickname,
            TableSnapshot.Position position, boolean fromCenter) {

        public PositionTransfer {
            fromNickname = fromNickname == null ? "" : fromNickname;
            Objects.requireNonNull(toNickname, "toNickname");
            if (toNickname.isBlank()) {
                throw new IllegalArgumentException("Position destination player is required");
            }
            Objects.requireNonNull(position, "position");
        }
    }

    record PostChips(long sequence, String nickname, double amount,
            Destination destination) implements TableVisualEvent {

        public PostChips {
            Objects.requireNonNull(nickname, "nickname");
            Objects.requireNonNull(destination, "destination");
            requireMoney(amount, "Chip amount");
        }

        public enum Destination {
            STREET_BET,
            POT
        }
    }

    record CollectBets(long sequence, List<ChipTransfer> transfers,
            double potBefore, double potAfterLanding) implements TableVisualEvent {

        public CollectBets {
            transfers = List.copyOf(transfers);
            if (transfers.isEmpty()) {
                throw new IllegalArgumentException("Collect-bets event needs at least one transfer");
            }
            requireMoney(potBefore, "Pot before collection");
            requireMoney(potAfterLanding, "Pot after landing");
            if (potAfterLanding < potBefore) {
                throw new IllegalArgumentException("Pot cannot decrease while collecting bets");
            }
        }
    }

    record ChipTransfer(String nickname, double amount) {

        public ChipTransfer {
            Objects.requireNonNull(nickname, "nickname");
            requireMoney(amount, "Chip transfer amount");
        }
    }

    record DealHoleCard(long sequence, String nickname, int slot,
            TableSnapshot.CardSnapshot card) implements TableVisualEvent {

        public DealHoleCard {
            Objects.requireNonNull(nickname, "nickname");
            Objects.requireNonNull(card, "card");
            if (slot < 0 || slot > 1) {
                throw new IllegalArgumentException("Hole-card slot must be 0 or 1");
            }
        }
    }

    /** Places one community card face down during the initial deal. */
    record DealCommunityCard(long sequence, int slot) implements TableVisualEvent {

        public DealCommunityCard {
            if (slot < 0 || slot > 4) {
                throw new IllegalArgumentException("Community-card slot must be 0..4");
            }
        }
    }

    record SwapHoleCards(long sequence, String nickname) implements TableVisualEvent {

        public SwapHoleCards {
            Objects.requireNonNull(nickname, "nickname");
        }
    }

    record FoldHoleCards(long sequence, String nickname) implements TableVisualEvent {

        public FoldHoleCards {
            Objects.requireNonNull(nickname, "nickname");
        }
    }

    /** One semantic reveal. The flop is emitted as one event containing three cards. */
    record RevealCommunityCards(long sequence, int firstSlot,
            List<TableSnapshot.CardSnapshot> cards) implements TableVisualEvent {

        public RevealCommunityCards {
            cards = List.copyOf(cards);
            if (firstSlot < 0 || firstSlot > 4 || cards.isEmpty()
                    || firstSlot + cards.size() > 5) {
                throw new IllegalArgumentException("Community-card reveal must fit slots 0..4");
            }
            if (cards.size() != 1 && !(firstSlot == 0 && cards.size() == 3)) {
                throw new IllegalArgumentException("Only a grouped flop or one turn/river card is valid");
            }
        }
    }

    record TurnTimer(long sequence, String nickname, long totalMillis,
            long remainingMillis, Phase phase) implements TableVisualEvent {

        public TurnTimer {
            nickname = nickname == null ? "" : nickname;
            Objects.requireNonNull(phase, "phase");
            if (totalMillis < 0 || remainingMillis < 0 || remainingMillis > totalMillis) {
                throw new IllegalArgumentException("Invalid turn timer duration");
            }
            if (phase == Phase.START && nickname.isBlank()) {
                throw new IllegalArgumentException("A started turn needs a player");
            }
        }

        public enum Phase {
            START,
            UPDATE,
            STOP
        }
    }

    record PlayerAction(long sequence, String nickname, ActionKind kind,
            String label, double amount, double potContribution) implements TableVisualEvent {

        public PlayerAction {
            Objects.requireNonNull(nickname, "nickname");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(label, "label");
            requireMoney(amount, "Action amount");
            requireMoney(potContribution, "Pot contribution");
        }

        public enum ActionKind {
            WAITING,
            FOLD,
            CHECK,
            CALL,
            BET,
            RAISE,
            ALL_IN,
            SMALL_BLIND,
            BIG_BLIND,
            STRADDLE
        }
    }

    record Cinematic(long sequence, Type type, Phase phase) implements TableVisualEvent {

        public Cinematic {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(phase, "phase");
        }

        public enum Type {
            ALL_IN
        }

        public enum Phase {
            START,
            FINISH
        }
    }

    record RevealHoleCards(long sequence, String nickname,
            TableSnapshot.CardSnapshot left,
            TableSnapshot.CardSnapshot right) implements TableVisualEvent {

        public RevealHoleCards {
            Objects.requireNonNull(nickname, "nickname");
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
        }
    }

    record HandResult(long sequence, String nickname, String handName,
            boolean winner) implements TableVisualEvent {

        public HandResult {
            Objects.requireNonNull(nickname, "nickname");
            Objects.requireNonNull(handName, "handName");
        }
    }

    record ShowdownHighlight(long sequence, String nickname, boolean enabled,
            List<Integer> holeCardSlots, List<Integer> communityCardSlots)
            implements TableVisualEvent {

        public ShowdownHighlight {
            Objects.requireNonNull(nickname, "nickname");
            holeCardSlots = checkedSlots(holeCardSlots, 2, "hole-card");
            communityCardSlots = checkedSlots(communityCardSlots, 5, "community-card");
        }
    }

    record Payout(long sequence, String nickname, double amount,
            int potIndex) implements TableVisualEvent {

        public Payout {
            Objects.requireNonNull(nickname, "nickname");
            requireMoney(amount, "Payout amount");
            if (potIndex < 0) {
                throw new IllegalArgumentException("Pot index cannot be negative");
            }
        }
    }

    record DeckChanged(long sequence, String deck) implements TableVisualEvent {

        public DeckChanged {
            Objects.requireNonNull(deck, "deck");
        }
    }

    record SeatRoster(long sequence, TableSnapshot snapshot) implements TableVisualEvent {

        public SeatRoster {
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    record CloseTable(long sequence) implements TableVisualEvent {
    }

    private static void requireMoney(double value, String name) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static List<Integer> checkedSlots(List<Integer> slots, int limit, String name) {
        List<Integer> copy = List.copyOf(slots);
        for (Integer slot : copy) {
            if (slot == null || slot < 0 || slot >= limit) {
                throw new IllegalArgumentException("Invalid " + name + " slot");
            }
        }
        return copy;
    }
}
