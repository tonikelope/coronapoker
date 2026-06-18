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

    // Minimum buy-in: minBB big blinds.
    public static int min(float big_blind, int minBB) {
        return (int) (big_blind * minBB);
    }

    // Maximum buy-in: maxBB big blinds.
    public static int max(float big_blind, int maxBB) {
        return (int) (big_blind * maxBB);
    }

    // Suggested buy-in: SUGGESTED_BB big blinds, clamped into [min,max] (so a deep
    // range such as 100-300 BB suggests its minimum rather than the bare 50 BB).
    public static int defaultBuyin(float big_blind, int minBB, int maxBB) {
        int suggested = (int) (big_blind * SUGGESTED_BB);
        return Math.max(min(big_blind, minBB), Math.min(suggested, max(big_blind, maxBB)));
    }

    // Per-table stack ceiling: the single fixed buy-in everyone shares, or maxBB
    // big blinds when each player chooses their own (the deepest anybody could have
    // bought in for). No player may ever hold more than this.
    public static int cap(boolean fixed, int buyin, float big_blind, int maxBB) {
        return fixed ? buyin : max(big_blind, maxBB);
    }

    // Maximum a player may ADD to their stack via a rebuy/top-up without exceeding
    // the ceiling; 0 if already at (or over) it. ceil() on the current stack is
    // conservative: fractional stacks from sub-1 blinds never let a whole-chip
    // rebuy push the total above the cap.
    public static int headroom(boolean fixed, int buyin, float big_blind, int maxBB, float current_stack) {
        return Math.max(0, cap(fixed, buyin, big_blind, maxBB) - (int) Math.ceil(current_stack));
    }
}
