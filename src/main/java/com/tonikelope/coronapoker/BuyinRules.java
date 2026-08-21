/*
 * Copyright (C) 2020 tonikelope
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
 * Pure buy-in arithmetic shared by the new-game spinner, the table-entry buy-in
 * dialog and the rebuy dialogs. Deliberately free of any GameFrame/Swing state
 * so the money-critical ceiling/clamp logic can be unit-tested in isolation.
 *
 * @author tonikelope
 */
public final class BuyinRules {

    private BuyinRules() {
    }

    // Configurable buy-in range, in big blinds. The host can widen it for
    // deep-stack tables; the defaults preserve the historical 10-100 BB range.
    public static final int DEFAULT_MIN_BB = 10;   // lower limit, default value
    public static final int DEFAULT_MAX_BB = 100;  // upper limit, default value
    public static final int FLOOR_MIN_BB = 10;     // the lower-limit spinner cannot go below this
    public static final int CEIL_MAX_BB = 500;     // the upper-limit spinner cannot go above this
    public static final int SUGGESTED_BB = 50;     // suggested buy-in within the range

    // The buy-in (chip count) is int across the whole model (Player, wire protocol,
    // SQLite). No clamp is needed here because BlindStructure.MAX_BLIND caps any
    // big blind at 4,000,000, so big_blind * maxBB is at most 500 * 4,000,000 =
    // 2,000,000,000, which fits in an int. The blind ladder and the buy-in ceiling
    // are kept consistent at the source (the blind limit), not patched here.
    /**
     * Minimum buy-in, in chips: {@code minBB} big blinds. ({@code big_blind} is
     * double-typed money; the returned chip count stays an int.)
     *
     * @param big_blind current big blind size
     * @param minBB minimum buy-in, in big blinds
     * @return minimum buy-in, in chips
     */
    public static int min(double big_blind, int minBB) {
        return (int) (big_blind * minBB);
    }

    /**
     * Maximum buy-in, in chips: {@code maxBB} big blinds.
     *
     * @param big_blind current big blind size
     * @param maxBB maximum buy-in, in big blinds
     * @return maximum buy-in, in chips
     */
    public static int max(double big_blind, int maxBB) {
        return (int) (big_blind * maxBB);
    }

    /**
     * Suggested buy-in, in chips: {@link #SUGGESTED_BB} big blinds, clamped
     * into {@code [min,max]} (so a deep range such as 100-300 BB suggests its
     * minimum rather than the bare 50 BB).
     *
     * @param big_blind current big blind size
     * @param minBB minimum buy-in, in big blinds
     * @param maxBB maximum buy-in, in big blinds
     * @return suggested buy-in, in chips
     */
    public static int defaultBuyin(double big_blind, int minBB, int maxBB) {
        int suggested = (int) (big_blind * SUGGESTED_BB);
        return Math.max(min(big_blind, minBB), Math.min(suggested, max(big_blind, maxBB)));
    }

    /**
     * Per-table stack ceiling: the single fixed buy-in everyone shares, or
     * {@code maxBB} big blinds when each player chooses their own (the deepest
     * anybody could have bought in for). No player may ever hold more than
     * this.
     *
     * @param fixed whether the table uses one shared buy-in for every player
     * @param buyin the shared buy-in, in chips (used only when {@code fixed} is
     * true)
     * @param big_blind current big blind size
     * @param maxBB maximum buy-in, in big blinds (used only when {@code fixed}
     * is false)
     * @return stack ceiling, in chips
     */
    public static int cap(boolean fixed, int buyin, double big_blind, int maxBB) {
        return fixed ? buyin : max(big_blind, maxBB);
    }

    /**
     * Maximum a player may ADD to their stack via a rebuy/top-up without
     * exceeding the ceiling; 0 if already at (or over) it.
     * {@code current_stack} is rounded up before subtracting, so a fractional
     * stack (from sub-1 blinds) never lets a whole-chip rebuy push the total
     * above the cap.
     *
     * @param fixed whether the table uses one shared buy-in for every player
     * @param buyin the shared buy-in, in chips (used only when {@code fixed} is
     * true)
     * @param big_blind current big blind size
     * @param maxBB maximum buy-in, in big blinds (used only when {@code fixed}
     * is false)
     * @param current_stack player's current stack
     * @return maximum rebuy amount, in chips; never negative
     */
    public static int headroom(boolean fixed, int buyin, double big_blind, int maxBB, double current_stack) {
        return Math.max(0, cap(fixed, buyin, big_blind, maxBB) - (int) Math.ceil(current_stack));
    }
}
