/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.List;

/** Canonical per-hand state container, independent of any renderer. */
public final class HandState {
    public enum Street { WAITING, PREFLOP, FLOP, TURN, RIVER, SHOWDOWN, FINISHED }
    public record Snapshot(int number, String handId, Street street,
            List<CardState.Snapshot> communityCards, PotState.Snapshot pot,
            TurnState.Snapshot turn, boolean runItTwice, boolean cancelled) { }
    private volatile int number;
    private volatile String handId = "";
    private volatile Street street = Street.WAITING;
    private volatile List<CardState> communityCards = List.of();
    private final PotState pot = new PotState();
    private final TurnState turn = new TurnState();
    private volatile boolean runItTwice;
    private volatile boolean cancelled;
    public int number() { return number; }
    public String handId() { return handId; }
    public Street street() { return street; }
    public List<CardState> communityCards() { return communityCards; }
    public PotState pot() { return pot; }
    public TurnState turn() { return turn; }
    public boolean runItTwice() { return runItTwice; }
    public boolean cancelled() { return cancelled; }
    public void begin(int nextNumber, String nextHandId) {
        number = nextNumber;
        handId = java.util.Objects.requireNonNullElse(nextHandId, "");
        street = Street.PREFLOP;
        communityCards = List.of();
        runItTwice = false;
        cancelled = false;
    }
    public void setStreet(Street value) { street = java.util.Objects.requireNonNull(value, "street"); }
    public void setCommunityCards(List<CardState> value) { communityCards = List.copyOf(value); }
    public void setRunItTwice(boolean value) { runItTwice = value; }
    public void setCancelled(boolean value) { cancelled = value; }
    public Snapshot snapshot() {
        return new Snapshot(number, handId, street,
                communityCards.stream().map(CardState::snapshot).toList(),
                pot.snapshot(), turn.snapshot(), runItTwice, cancelled);
    }
}
