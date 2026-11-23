/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

/** Denomination rules for new chips entering the table through a rebuy. */
public final class RebuyAmountRules {

    private RebuyAmountRules() {
    }

    public static boolean isPlayable(double amount, long chipCents) {
        try {
            long cents = MoneyCents.fromDouble(amount).cents();
            return cents > 0 && chipCents > 0 && cents % chipCents == 0;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public static double floorToChip(double amount, long chipCents) {
        long cents = MoneyCents.fromDouble(Math.max(0d, amount)).cents();
        return (cents / chipCents * chipCents) / 100d;
    }

    public static double minimum(double lowerBound, long chipCents) {
        long cents = MoneyCents.fromDouble(lowerBound).cents();
        return Math.max(1, (cents + chipCents - 1) / chipCents) * chipCents / 100d;
    }
}
