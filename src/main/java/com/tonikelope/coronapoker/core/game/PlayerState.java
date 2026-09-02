/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.List;
import java.util.Objects;

/** Authoritative player data shared by controllers and both frontends. */
public class PlayerState {

    public enum Decision { NONE, FOLD, CHECK, BET, ALL_IN }
    public enum Position { NONE, DEALER, SMALL_BLIND, BIG_BLIND, DEAD_DEALER,
        STRADDLE, DEALER_STRADDLE }

    public record Snapshot(String nickname, int buyIn, double stack, double bet,
            double potContribution, double pendingPayment, Decision decision,
            Position position, boolean active, boolean spectator, boolean exited,
            boolean timedOut, boolean winner, boolean showingCards,
            List<CardState.Snapshot> holeCards, String lastAction, String handName) { }

    private volatile String nickname;
    private volatile int buyIn;
    private volatile double stack;
    private volatile double bet;
    private volatile double potContribution;
    private volatile double pendingPayment;
    private volatile Decision decision = Decision.NONE;
    private volatile Position position = Position.NONE;
    private volatile boolean active;
    private volatile boolean spectator;
    private volatile boolean exited;
    private volatile boolean timedOut;
    private volatile boolean winner;
    private volatile boolean showingCards;
    private volatile String lastAction = "";
    private volatile String handName = "";
    private volatile CardState firstCard = new CardState();
    private volatile CardState secondCard = new CardState();

    public PlayerState() {
        nickname = "";
    }

    public PlayerState(String nickname) {
        setNickname(nickname);
    }

    public String nickname() { return nickname; }
    public int buyIn() { return buyIn; }
    public double stack() { return stack; }
    public double bet() { return bet; }
    public double potContribution() { return potContribution; }
    public double pendingPayment() { return pendingPayment; }
    public Decision decision() { return decision; }
    public Position position() { return position; }
    public boolean active() { return active; }
    public boolean spectator() { return spectator; }
    public boolean exited() { return exited; }
    public boolean timedOut() { return timedOut; }
    public boolean winner() { return winner; }
    public boolean showingCards() { return showingCards; }
    public String lastAction() { return lastAction; }
    public String handName() { return handName; }
    public CardState firstCard() { return firstCard; }
    public CardState secondCard() { return secondCard; }

    /**
     * Connects this neutral player model to the card models owned by an
     * existing frontend adapter. Both references are swapped atomically under
     * the same monitor used by {@link #snapshot()}.
     */
    public synchronized void bindHoleCards(CardState first, CardState second) {
        firstCard = Objects.requireNonNull(first, "firstCard");
        secondCard = Objects.requireNonNull(second, "secondCard");
    }

    public final void setNickname(String value) {
        String normalized = Objects.requireNonNull(value, "nickname").trim();
        nickname = normalized;
    }
    public void setBuyIn(int value) { buyIn = value; }
    public void setStack(double value) { stack = finite(value, "stack"); }
    public void setBet(double value) { bet = finite(value, "bet"); }
    public void setPotContribution(double value) { potContribution = finite(value, "potContribution"); }
    public void setPendingPayment(double value) { pendingPayment = finite(value, "pendingPayment"); }
    public void setDecision(Decision value) { decision = Objects.requireNonNull(value, "decision"); }
    public void setPosition(Position value) { position = Objects.requireNonNull(value, "position"); }
    public void setActive(boolean value) { active = value; }
    public void setSpectator(boolean value) { spectator = value; }
    public void setExited(boolean value) { exited = value; }
    public void setTimedOut(boolean value) { timedOut = value; }
    public void setWinner(boolean value) { winner = value; }
    public void setShowingCards(boolean value) { showingCards = value; }
    public void setLastAction(String value) { lastAction = Objects.requireNonNullElse(value, ""); }
    public void setHandName(String value) { handName = Objects.requireNonNullElse(value, ""); }

    public synchronized Snapshot snapshot() {
        return new Snapshot(nickname, buyIn, stack, bet, potContribution,
                pendingPayment, decision, position, active, spectator, exited,
                timedOut, winner, showingCards,
                List.of(firstCard.snapshot(), secondCard.snapshot()),
                lastAction, handName);
    }

    private static double finite(double value, String label) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(label + " must be finite");
        return value;
    }
}
