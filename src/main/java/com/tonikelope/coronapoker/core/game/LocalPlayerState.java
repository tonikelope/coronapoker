/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/** Local-only decision state; the poker rules remain in the canonical engine. */
public final class LocalPlayerState extends PlayerState {
    private volatile boolean turn;
    private volatile double callRequired;
    private volatile double minimumRaise;
    private volatile Decision preselectedDecision = Decision.NONE;
    private volatile int responseTime;

    public LocalPlayerState(String nickname) { super(nickname); }
    public boolean turn() { return turn; }
    public double callRequired() { return callRequired; }
    public double minimumRaise() { return minimumRaise; }
    public Decision preselectedDecision() { return preselectedDecision; }
    public int responseTime() { return responseTime; }
    public void setTurn(boolean value) { turn = value; }
    public void setCallRequired(double value) { callRequired = finite(value); }
    public void setMinimumRaise(double value) { minimumRaise = finite(value); }
    public void setPreselectedDecision(Decision value) {
        preselectedDecision = java.util.Objects.requireNonNull(value, "decision");
    }
    public void setResponseTime(int value) { responseTime = value; }
    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("amount must be finite");
        return value;
    }
}
