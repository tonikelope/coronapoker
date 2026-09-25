/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.List;

/** Neutral monetary projection of the canonical main and side pots. */
public final class PotState {
    public record SidePot(int sequence, double amount, List<String> eligiblePlayers) {
        public SidePot { eligiblePlayers = List.copyOf(eligiblePlayers); }
    }
    public record Snapshot(double total, double streetBets, double remainder,
            List<SidePot> sidePots) { }

    private volatile double total;
    private volatile double streetBets;
    private volatile double remainder;
    private volatile List<SidePot> sidePots = List.of();

    public double total() { return total; }
    public double streetBets() { return streetBets; }
    public double remainder() { return remainder; }
    public List<SidePot> sidePots() { return sidePots; }
    public void setTotal(double value) { total = finite(value); }
    public void setStreetBets(double value) { streetBets = finite(value); }
    public void setRemainder(double value) { remainder = finite(value); }
    public void setSidePots(List<SidePot> value) { sidePots = List.copyOf(value); }
    public Snapshot snapshot() { return new Snapshot(total, streetBets, remainder, sidePots); }
    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("amount must be finite");
        return value;
    }
}
