/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package com.tonikelope.coronapoker.bot.eval;

/**
 * Immutable pair of positive and negative hand potential, both in [0.0, 1.0].
 */
public final class Potential {

    public static final Potential ZERO = new Potential(0.0, 0.0);

    private final double ppot;
    private final double npot;

    public Potential(double ppot, double npot) {
        this.ppot = ppot;
        this.npot = npot;
    }

    public double ppot() {
        return ppot;
    }

    public double npot() {
        return npot;
    }

    @Override
    public String toString() {
        return "Potential[ppot=" + ppot + ", npot=" + npot + "]";
    }
}
