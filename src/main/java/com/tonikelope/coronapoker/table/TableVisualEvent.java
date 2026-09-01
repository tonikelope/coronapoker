/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.table;

import java.util.Objects;

/** Semantic visual events emitted by the unchanged game flow. */
public sealed interface TableVisualEvent permits TableVisualEvent.Synchronize,
        TableVisualEvent.Shuffle, TableVisualEvent.MovePosition,
        TableVisualEvent.PostChips, TableVisualEvent.DealHoleCard,
        TableVisualEvent.RevealCommunityCard, TableVisualEvent.PlayerAction,
        TableVisualEvent.RevealHoleCards, TableVisualEvent.HandResult,
        TableVisualEvent.SeatRoster, TableVisualEvent.CloseTable {

    long sequence();

    record Synchronize(long sequence, TableSnapshot snapshot) implements TableVisualEvent {

        public Synchronize {
            Objects.requireNonNull(snapshot, "snapshot");
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

    record PostChips(long sequence, String nickname, double amount,
            Destination destination) implements TableVisualEvent {

        public PostChips {
            Objects.requireNonNull(nickname, "nickname");
            Objects.requireNonNull(destination, "destination");
        }

        public enum Destination {
            STREET_BET,
            POT
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

    record RevealCommunityCard(long sequence, int slot,
            TableSnapshot.CardSnapshot card) implements TableVisualEvent {

        public RevealCommunityCard {
            Objects.requireNonNull(card, "card");
            if (slot < 0 || slot > 4) {
                throw new IllegalArgumentException("Community-card slot must be 0..4");
            }
        }
    }

    record PlayerAction(long sequence, String nickname, String action,
            double amount) implements TableVisualEvent {

        public PlayerAction {
            Objects.requireNonNull(nickname, "nickname");
            Objects.requireNonNull(action, "action");
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

    record SeatRoster(long sequence, TableSnapshot snapshot) implements TableVisualEvent {

        public SeatRoster {
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    record CloseTable(long sequence) implements TableVisualEvent {
    }
}
