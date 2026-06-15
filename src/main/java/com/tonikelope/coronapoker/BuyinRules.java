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

    // Minimum buy-in: 10 big blinds.
    public static int min(float big_blind) {
        return (int) (big_blind * 10f);
    }

    // Default suggested buy-in: 50 big blinds.
    public static int defaultBuyin(float big_blind) {
        return (int) (big_blind * 50f);
    }

    // Maximum buy-in: 100 big blinds.
    public static int max(float big_blind) {
        return (int) (big_blind * 100f);
    }

    // Per-table stack ceiling: the single fixed buy-in everyone shares, or 100BB
    // when each player chooses their own (the deepest anybody could have bought
    // in for). No player may ever hold more than this.
    public static int cap(boolean fixed, int buyin, float big_blind) {
        return fixed ? buyin : max(big_blind);
    }

    // Maximum a player may ADD to their stack via a rebuy/top-up without exceeding
    // the ceiling; 0 if already at (or over) it. ceil() on the current stack is
    // conservative: fractional stacks from sub-1 blinds never let a whole-chip
    // rebuy push the total above the cap.
    public static int headroom(boolean fixed, int buyin, float big_blind, float current_stack) {
        return Math.max(0, cap(fixed, buyin, big_blind) - (int) Math.ceil(current_stack));
    }
}
