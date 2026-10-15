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
 * The engine stores money in cents, but a table may use a larger physical chip
 * denomination. The denomination-aware overloads therefore split in complete
 * table chips and return the indivisible remainder to the caller (the dealer's
 * {@code bote_sobrante}). All arithmetic still uses integer cents, so no money
 * is ever created or destroyed and the canonical/consensus layer remains exact.
 *
 * @author tonikelope
 */
public final class PotMath {

    private PotMath() {
    }

    // Cents in one money unit. This is the storage resolution; the physical
    // table-chip denomination may be larger and is handled by the overloads.
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

    private static long denominationCents(double denomination) {
        if (!Double.isFinite(denomination) || denomination <= 0d) {
            return 1L;
        }
        return Math.max(1L, toCents(denomination));
    }

    private static long gcd(long left, long right) {
        long a = Math.abs(left);
        long b = Math.abs(right);
        while (b != 0L) {
            long remainder = a % b;
            a = b;
            b = remainder;
        }
        return a;
    }

    public static double smallestChipDenomination(double... configuredAmounts) {
        long denomination = 0L;
        if (configuredAmounts != null) {
            for (double amount : configuredAmounts) {
                if (!Double.isFinite(amount) || amount <= 0d) continue;
                denomination = gcd(denomination, toCents(amount));
            }
        }
        return fromCentsDouble(Math.max(1L, denomination));
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
     * {@code double} money overload of {@link #splitAmongWinners(float, int)}.
     * The split is still computed in integer cents (exact, no binary-float
     * drift), so below the float exactness ceiling it returns cent-for-cent the
     * same shares as the float overload; above it the double inputs/outputs no
     * longer lose cents.
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
     * Splits a pot without manufacturing fractions of the table's smallest
     * physical chip. Even a single winner receives only complete chips; any
     * sub-chip residue is carried into the next pot by the caller.
     */
    public static double[] splitAmongWinners(double amount, int winners,
            double chipDenomination) {
        int recipientCount = Math.max(1, winners);
        long total = Math.max(0L, toCents(amount));
        long chip = denominationCents(chipDenomination);
        long wholeChips = total / chip;
        long chipsPerWinner = wholeChips / recipientCount;
        long perWinner = chipsPerWinner * chip;
        long remainder = total - perWinner * recipientCount;
        return new double[]{fromCentsDouble(perWinner),
            fromCentsDouble(remainder)};
    }

    /**
     * Splits a pot into the two equal halves of a run-it-twice board (SIDE-A
     * and SIDE-B). Each board gets {@code floor(pot/2)} at cent resolution; if
     * the pot is an odd number of cents the leftover cent is NOT dealt (the
     * caller recomputes and carries it in {@code bote_sobrante}). Invariant:
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
     * {@code double} money overload of {@link #splitForRunItTwice(float)}.
     * Halves are taken in integer cents (the odd cent, if any, is not dealt and
     * the caller carries it), so below the float ceiling it returns the same
     * halves as the float overload and above it the double type keeps every
     * cent.
     */
    public static double[] splitForRunItTwice(double pot) {
        long half = toCents(pot) / 2;        // floor; odd cent carried by the caller
        double each = fromCentsDouble(half);
        return new double[]{each, each};
    }

    /** Splits both boards in complete table chips; the caller carries the rest. */
    public static double[] splitForRunItTwice(double pot,
            double chipDenomination) {
        long total = Math.max(0L, toCents(pot));
        long chip = denominationCents(chipDenomination);
        long chipsPerBoard = total / chip / 2L;
        double each = fromCentsDouble(chipsPerBoard * chip);
        return new double[]{each, each};
    }
}
