/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Exact, bounded chip amount expressed in cents. */
public final class MoneyCents {

    /** Ten seats, each with the largest stack accepted by existing config. */
    public static final long MAX_CENTS = 10L * Integer.MAX_VALUE * 100L;

    private final long cents;

    private MoneyCents(long cents) {
        this.cents = cents;
    }

    public static MoneyCents ofCents(long cents) {
        if (cents < 0L || cents > MAX_CENTS) {
            throw new IllegalArgumentException("money cents outside table domain: " + cents);
        }
        return new MoneyCents(cents);
    }

    public static MoneyCents parse(String amount) {
        Objects.requireNonNull(amount, "amount");
        final BigDecimal decimal;
        try {
            decimal = new BigDecimal(amount);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid money amount", ex);
        }
        if (decimal.scale() > 2) {
            throw new IllegalArgumentException("money amount has more than two decimals: " + amount);
        }
        try {
            return ofCents(decimal.movePointRight(2).longValueExact());
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("money amount outside table domain: " + amount, ex);
        }
    }

    public static MoneyCents fromFloat(float amount) {
        if (!Float.isFinite(amount)) {
            throw new IllegalArgumentException("money amount must be finite: " + amount);
        }
        return parse(Float.toString(amount));
    }

    public static MoneyCents fromDouble(double amount) {
        if (!Double.isFinite(amount) || amount < 0d) {
            throw new IllegalArgumentException("money amount must be finite and non-negative: " + amount);
        }
        if (amount > ((double) MAX_CENTS) / 100d) {
            throw new IllegalArgumentException("money amount outside table domain: " + amount);
        }
        BigDecimal raw = BigDecimal.valueOf(amount);
        try {
            return ofCents(raw.movePointRight(2).longValueExact());
        } catch (ArithmeticException ex) {
            BigDecimal nearestCent = raw.setScale(2, RoundingMode.HALF_UP);
            BigDecimal tolerance = BigDecimal.valueOf(Math.max(Math.ulp(amount) * 4d, 1e-12d));
            if (raw.subtract(nearestCent).abs().compareTo(tolerance) > 0) {
                throw new IllegalArgumentException("money amount has more than two decimals: " + amount, ex);
            }
            try {
                return ofCents(nearestCent.movePointRight(2).longValueExact());
            } catch (ArithmeticException rangeError) {
                throw new IllegalArgumentException("money amount outside table domain: " + amount, rangeError);
            }
        }
    }

    public long cents() {
        return cents;
    }

    public BigDecimal toDecimal() {
        return BigDecimal.valueOf(cents, 2);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MoneyCents && cents == ((MoneyCents) other).cents;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(cents);
    }

    @Override
    public String toString() {
        return toDecimal().toPlainString();
    }
}
