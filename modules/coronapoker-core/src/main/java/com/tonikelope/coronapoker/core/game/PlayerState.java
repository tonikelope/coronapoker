/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.List;
import java.util.Objects;

/** Authoritative player data shared by controllers and both frontends. */
public class PlayerState {

    public enum Decision { NONE, FOLD, CHECK, BET, ALL_IN }
    public enum ActionKind { NONE, FOLD, CHECK, CALL, BET, RAISE, RERAISE,
        ALL_IN }
    public enum RebuyPhase { NONE, WAITING, REBOUGHT }
    public enum Position { NONE, DEALER, SMALL_BLIND, BIG_BLIND, DEAD_DEALER,
        STRADDLE, DEALER_STRADDLE }

    public record Snapshot(String nickname, int buyIn, double stack, double bet,
            double potContribution, double pendingPayment, Decision decision,
            ActionKind actionKind, Position position, boolean active,
            boolean spectator, boolean warming, boolean exited,
            boolean timedOut, boolean winner, boolean showingCards,
            boolean underTheGun,
            int latency, int previousLatency, int reconnectionCount,
            long telemetryAt, List<CardState.Snapshot> holeCards,
            String lastAction, String handName, boolean partialHand,
            float partialWinPercentage, boolean resultResolved,
            String publicHandName, List<Integer> wonPotIndexes,
            boolean returnedSidePot,
            boolean showdownHighlightEnabled,
            List<Integer> winningHoleCardSlots,
            List<Integer> winningCommunityCardSlots,
            RebuyPhase rebuyPhase, int immediateRebuyAmount,
            String publicActionLabel) { }

    private volatile String nickname;
    private volatile int buyIn;
    private volatile double stack;
    private volatile double bet;
    private volatile double potContribution;
    private volatile double pendingPayment;
    private volatile Decision decision = Decision.NONE;
    private volatile ActionKind actionKind = ActionKind.NONE;
    private volatile Position position = Position.NONE;
    private volatile boolean active;
    private volatile boolean spectator;
    private volatile boolean warming;
    private volatile boolean exited;
    private volatile boolean timedOut;
    private volatile boolean winner;
    private volatile boolean showingCards;
    private volatile boolean underTheGun;
    private volatile int latency = -2;
    private volatile int previousLatency = -2;
    private volatile int reconnectionCount;
    private volatile long telemetryAt;
    private volatile String lastAction = "";
    private volatile String handName = "";
    private volatile boolean partialHand;
    private volatile float partialWinPercentage = -1f;
    private volatile boolean resultResolved;
    private volatile String publicHandName = "";
    private volatile List<Integer> wonPotIndexes = List.of();
    private volatile boolean returnedSidePot;
    private volatile boolean showdownHighlightEnabled;
    private volatile List<Integer> winningHoleCardSlots = List.of();
    private volatile List<Integer> winningCommunityCardSlots = List.of();
    private volatile RebuyPhase rebuyPhase = RebuyPhase.NONE;
    private volatile int immediateRebuyAmount;
    private volatile String publicActionLabel = "";
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
    public ActionKind actionKind() { return actionKind; }
    public Position position() { return position; }
    public boolean active() { return active; }
    public boolean spectator() { return spectator; }
    public boolean warming() { return warming; }
    public boolean exited() { return exited; }
    public boolean timedOut() { return timedOut; }
    public boolean winner() { return winner; }
    public boolean showingCards() { return showingCards; }
    public boolean underTheGun() { return underTheGun; }
    public String lastAction() { return lastAction; }
    public String handName() { return handName; }
    public int latency() { return latency; }
    public int previousLatency() { return previousLatency; }
    public int reconnectionCount() { return reconnectionCount; }
    public long telemetryAt() { return telemetryAt; }
    public boolean partialHand() { return partialHand; }
    public float partialWinPercentage() { return partialWinPercentage; }
    public boolean resultResolved() { return resultResolved; }
    public String publicHandName() { return publicHandName; }
    public List<Integer> wonPotIndexes() { return wonPotIndexes; }
    public boolean returnedSidePot() { return returnedSidePot; }
    public boolean showdownHighlightEnabled() {
        return showdownHighlightEnabled;
    }
    public List<Integer> winningHoleCardSlots() {
        return winningHoleCardSlots;
    }
    public List<Integer> winningCommunityCardSlots() {
        return winningCommunityCardSlots;
    }
    public RebuyPhase rebuyPhase() { return rebuyPhase; }
    public int immediateRebuyAmount() { return immediateRebuyAmount; }
    public String publicActionLabel() { return publicActionLabel; }
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
    public void setActionKind(ActionKind value) {
        actionKind = Objects.requireNonNull(value, "actionKind");
    }
    public void setPosition(Position value) { position = Objects.requireNonNull(value, "position"); }
    public void setActive(boolean value) { active = value; }
    public void setSpectator(boolean value) { spectator = value; }
    public void setWarming(boolean value) { warming = value; }
    public void setExited(boolean value) { exited = value; }
    public void setTimedOut(boolean value) { timedOut = value; }
    public void setWinner(boolean value) { winner = value; }
    public void setShowingCards(boolean value) { showingCards = value; }
    public void setUnderTheGun(boolean value) { underTheGun = value; }
    public void setLastAction(String value) { lastAction = Objects.requireNonNullElse(value, ""); }
    public void setHandName(String value) { handName = Objects.requireNonNullElse(value, ""); }
    public void setPartialHand(float percentage) {
        if (!Float.isFinite(percentage) || percentage < -1f
                || percentage > 100f) {
            throw new IllegalArgumentException("Invalid partial percentage");
        }
        partialHand = true;
        partialWinPercentage = percentage;
        resultResolved = false;
        publicHandName = handName;
        wonPotIndexes = List.of();
        returnedSidePot = false;
    }
    public void setResolvedHandResult(boolean resolved) {
        resultResolved = resolved;
        if (resolved) {
            partialHand = false;
            partialWinPercentage = -1f;
        }
    }
    public void setPublicHandName(String value) {
        publicHandName = Objects.requireNonNullElse(value, "");
    }
    public void setWonPotIndexes(List<Integer> indexes) {
        wonPotIndexes = List.copyOf(Objects.requireNonNull(indexes,
                "wonPotIndexes"));
    }
    public void setReturnedSidePot(boolean returned) {
        returnedSidePot = returned;
    }
    public void setShowdownHighlight(boolean enabled,
            List<Integer> holeSlots, List<Integer> communitySlots) {
        showdownHighlightEnabled = enabled;
        winningHoleCardSlots = enabled
                ? List.copyOf(Objects.requireNonNull(holeSlots, "holeSlots"))
                : List.of();
        winningCommunityCardSlots = enabled
                ? List.copyOf(Objects.requireNonNull(communitySlots,
                        "communitySlots")) : List.of();
    }
    public void setRebuyPhase(RebuyPhase value) {
        rebuyPhase = Objects.requireNonNull(value, "rebuyPhase");
    }
    public void setImmediateRebuyAmount(int value) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    "Immediate rebuy amount cannot be negative");
        }
        immediateRebuyAmount = value;
    }
    public void setPublicActionLabel(String value) {
        publicActionLabel = Objects.requireNonNullElse(value, "");
    }
    public void resetHandPresentation() {
        partialHand = false;
        partialWinPercentage = -1f;
        resultResolved = false;
        publicHandName = "";
        wonPotIndexes = List.of();
        returnedSidePot = false;
        showdownHighlightEnabled = false;
        winningHoleCardSlots = List.of();
        winningCommunityCardSlots = List.of();
        publicActionLabel = "";
    }
    public void setTelemetry(int current, int previous, int reconnections) {
        if (current < -1 || previous < -1 || reconnections < 0) {
            throw new IllegalArgumentException("Invalid player telemetry");
        }
        latency = current;
        previousLatency = previous;
        reconnectionCount = reconnections;
        telemetryAt = System.currentTimeMillis();
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(nickname, buyIn, stack, bet, potContribution,
                pendingPayment, decision, actionKind, position, active,
                spectator, warming, exited,
                timedOut, winner, showingCards, underTheGun,
                latency, previousLatency, reconnectionCount, telemetryAt,
                List.of(firstCard.snapshot(), secondCard.snapshot()),
                lastAction, handName, partialHand, partialWinPercentage,
                resultResolved, publicHandName, wonPotIndexes, returnedSidePot,
                showdownHighlightEnabled, winningHoleCardSlots,
                winningCommunityCardSlots, rebuyPhase,
                immediateRebuyAmount, publicActionLabel);
    }

    private static double finite(double value, String label) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(label + " must be finite");
        return value;
    }
}
