/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import java.util.ArrayList;
import java.util.List;

/**
 * Canonical physical chip denominations shared by flights, pots and racks.
 * Values are fixed at x1, x2, x5 and x10 the initial small blind and retain
 * one decorated colour per value for the whole game.
 */
final class GdxChipDenominations {

    static final int COLUMN_COUNT = 4;

    /** Casino progression shared by player racks, flights and the table pot. */
    private static final int[] COLORS = {1, 3, 0, 2};
    private static final int[] UNITS = {1, 2, 5, 10};

    private GdxChipDenominations() {
    }

    static int color(int column) {
        return COLORS[column];
    }

    static int columnForColor(int color) {
        for (int column = 0; column < COLORS.length; column++) {
            if (COLORS[column] == color) return column;
        }
        return -1;
    }

    static int units(int column) {
        return UNITS[column];
    }

    static int totalUnits() {
        int total = 0;
        for (int unit : UNITS) total += unit;
        return total;
    }

    static long baseMinor(double smallBlind) {
        return Double.isFinite(smallBlind) && smallBlind > 0d
                ? Math.max(1L, Math.round(smallBlind * 100d)) : 1L;
    }

    /**
     * Pays an amount with the smallest exact number of available chips.
     * A cent remainder below x1 is folded into the smallest selected chip so
     * split-pot rounding remains exact without inventing another denomination.
     */
    static List<Chip> minimum(double exactAmount, double smallBlind) {
        if (!Double.isFinite(exactAmount) || exactAmount <= 0d) {
            return List.of();
        }
        long totalMinor = Math.max(1L, Math.round(exactAmount * 100d));
        long baseMinor = baseMinor(smallBlind);
        long remaining = totalMinor;
        ArrayList<Chip> result = new ArrayList<>();
        for (int column = COLUMN_COUNT - 1; column >= 0; column--) {
            long denomination = safeMultiply(baseMinor, units(column));
            long count = remaining / denomination;
            for (long chip = 0L; chip < count; chip++) {
                result.add(new Chip(denomination, color(column)));
            }
            remaining -= count * denomination;
        }
        if (remaining > 0L) {
            if (result.isEmpty()) {
                result.add(new Chip(remaining, color(0)));
            } else {
                int smallest = 0;
                for (int index = 1; index < result.size(); index++) {
                    if (result.get(index).amountMinor()
                            < result.get(smallest).amountMinor()) {
                        smallest = index;
                    }
                }
                Chip chip = result.get(smallest);
                result.set(smallest, new Chip(
                        chip.amountMinor() + remaining, chip.color()));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Changes one canonical chip into the nearest exact lower-denomination
     * combination. Only one denomination step is performed here; callers may
     * change another resulting chip in a later pass if an exact payment still
     * cannot be formed. The x1 chip is deliberately never broken.
     */
    static List<Chip> breakDown(long amountMinor, double smallBlind) {
        long baseMinor = baseMinor(smallBlind);
        int sourceColumn = -1;
        for (int column = 1; column < COLUMN_COUNT; column++) {
            if (safeMultiply(baseMinor, units(column)) == amountMinor) {
                sourceColumn = column;
                break;
            }
        }
        if (sourceColumn < 1) return List.of();

        long remaining = amountMinor;
        ArrayList<Chip> result = new ArrayList<>();
        for (int column = sourceColumn - 1; column >= 0; column--) {
            long denomination = safeMultiply(baseMinor, units(column));
            long count = remaining / denomination;
            for (long chip = 0L; chip < count; chip++) {
                result.add(new Chip(denomination, color(column)));
            }
            remaining -= count * denomination;
        }
        return remaining == 0L && result.size() > 1
                ? List.copyOf(result) : List.of();
    }

    private static long safeMultiply(long value, int multiplier) {
        return value > Long.MAX_VALUE / multiplier
                ? Long.MAX_VALUE : value * multiplier;
    }

    record Chip(long amountMinor, int color) {
    }
}
