/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

/** Validated number of confirmed rebuys persisted with a hand balance. */
public final class BuyinCount {

    public static final int MAX_VALUE = 1_000_000;
    private final int value;

    private BuyinCount(int value) {
        this.value = value;
    }

    public static BuyinCount of(int value) {
        if (value < 0 || value > MAX_VALUE) {
            throw new IllegalArgumentException("rebuy count outside game domain: " + value);
        }
        return new BuyinCount(value);
    }

    public int value() {
        return value;
    }
}
