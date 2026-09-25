/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

/** Distinct validated type for a hand's persisted pot. */
public final class HandPotCents {

    private final MoneyCents amount;

    private HandPotCents(MoneyCents amount) {
        this.amount = amount;
    }

    public static HandPotCents of(MoneyCents amount) {
        if (amount == null) {
            throw new IllegalArgumentException("hand pot is required");
        }
        return new HandPotCents(amount);
    }

    public static HandPotCents fromDouble(double amount) {
        return of(MoneyCents.fromDouble(amount));
    }

    public long cents() {
        return amount.cents();
    }

    public double toDouble() {
        return amount.toDecimal().doubleValue();
    }
}
