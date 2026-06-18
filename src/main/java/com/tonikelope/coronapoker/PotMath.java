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
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker;

/**
 * Pure pot-division arithmetic, free of any game/Swing state so the money
 * conservation can be unit-tested in isolation (same spirit as BuyinRules).
 *
 * The table's smallest chip is one cent ({@code 0.01}, the engine's money
 * resolution, see {@code Helpers.floatClean}). Pots are split in WHOLE cents:
 * every winner gets the floored share and the indivisible remainder is returned
 * to be carried forward (the dealer's {@code bote_sobrante}), so no money is
 * ever created or destroyed. All arithmetic is done in integer cents, which is
 * exact (no binary-float drift) and matches the cents domain of the
 * canonical/consensus layer ({@code CanonicalActionRecord.amountToCents}).
 *
 * @author tonikelope
 */
public final class PotMath {

    private PotMath() {
    }

    // Cents in one money unit. The chip is one cent (0.01).
    public static final int CENTS_PER_UNIT = 100;

    static long toCents(float amount) {
        return Math.round((double) amount * CENTS_PER_UNIT);
    }

    static long toCents(double amount) {
        return Math.round(amount * CENTS_PER_UNIT);
    }

    static float fromCents(long cents) {
        return (float) (cents / (double) CENTS_PER_UNIT);
    }

    static double fromCentsDouble(long cents) {
        return cents / (double) CENTS_PER_UNIT;
    }

    /**
     * Splits {@code amount} among {@code winners} equal winners.
     *
     * @return {@code [perWinner, remainder]} where {@code perWinner * winners +
     * remainder == amount} (at cent resolution). The remainder is the
     * indivisible odd cents that cannot be split evenly; the single-winner case
     * returns the whole amount with zero remainder.
     */
    public static float[] splitAmongWinners(float amount, int winners) {
        if (winners <= 1) {
            return new float[]{amount, 0f};
        }
        long total = toCents(amount);
        long per = total / winners;          // integer floor
        long remainder = total - per * winners;
        return new float[]{fromCents(per), fromCents(remainder)};
    }

    /**
     * {@code double} money overload of {@link #splitAmongWinners(float, int)}. The
     * split is still computed in integer cents (exact, no binary-float drift), so
     * below the float exactness ceiling it returns cent-for-cent the same shares as
     * the float overload; above it the double inputs/outputs no longer lose cents.
     */
    public static double[] splitAmongWinners(double amount, int winners) {
        if (winners <= 1) {
            return new double[]{amount, 0d};
        }
        long total = toCents(amount);
        long per = total / winners;          // integer floor
        long remainder = total - per * winners;
        return new double[]{fromCentsDouble(per), fromCentsDouble(remainder)};
    }

    /**
     * Splits a pot into the two equal halves of a run-it-twice board (SIDE-A and
     * SIDE-B). Each board gets {@code floor(pot/2)} at cent resolution; if the
     * pot is an odd number of cents the leftover cent is NOT dealt (the caller
     * recomputes and carries it in {@code bote_sobrante}). Invariant:
     * {@code sideA + sideB + oddCent == pot}.
     *
     * @return {@code [sideA, sideB]} (both equal)
     */
    public static float[] splitForRunItTwice(float pot) {
        long half = toCents(pot) / 2;        // floor; odd cent carried by the caller
        float each = fromCents(half);
        return new float[]{each, each};
    }

    /**
     * {@code double} money overload of {@link #splitForRunItTwice(float)}. Halves
     * are taken in integer cents (the odd cent, if any, is not dealt and the caller
     * carries it), so below the float ceiling it returns the same halves as the
     * float overload and above it the double type keeps every cent.
     */
    public static double[] splitForRunItTwice(double pot) {
        long half = toCents(pot) / 2;        // floor; odd cent carried by the caller
        double each = fromCentsDouble(half);
        return new double[]{each, each};
    }
}
