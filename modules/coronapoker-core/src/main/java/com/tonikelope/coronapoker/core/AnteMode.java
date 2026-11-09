/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core;

import com.tonikelope.coronapoker.MoneyCents;

/** Who posts the ante for a hand. */
public enum AnteMode {
    TRADITIONAL("traditional", "(A)"),
    BIG_BLIND("big_blind", "(BBA)"),
    BUTTON("button", "(BA)"),
    SMALL_BLIND("small_blind", "(SBA)");

    private final String labelKey;
    private final String marker;

    AnteMode(String key, String marker) {
        this.labelKey = "game.ante_mode." + key;
        this.marker = marker;
    }

    public String labelKey() {
        return labelKey;
    }

    public String marker() {
        return marker;
    }

    public double amount(double bigBlind) {
        if (this != TRADITIONAL) return bigBlind;
        long blindCents = MoneyCents.fromDouble(bigBlind).cents();
        return (blindCents + 5L) / 10L / 100d;
    }
}
