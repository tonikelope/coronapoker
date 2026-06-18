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
 * Pure No-Limit Texas Hold'em betting-minimum rules, free of any game/Swing
 * state so they can be unit-tested in isolation (same spirit as BuyinRules /
 * PotMath). These are the authoritative minimums the engine enforces.
 *
 * The rules (independent of the money chip and of the spinner's step size):
 * <ul>
 * <li>Minimum bet to OPEN a street (nobody has bet yet) = the big blind.</li>
 * <li>Minimum RAISE increment = the size of the previous raise this street, or
 * the big blind if there has been no raise yet (preflop the posted big blind
 * counts as the opening bet, so the first raise is to at least 2x the big
 * blind).</li>
 * <li>A player may go ALL-IN for less than the minimum raise; such a partial
 * raise does NOT reopen the betting for players who have already acted.</li>
 * </ul>
 *
 * The small blind is a forced half-bet, never a betting minimum.
 *
 * @author tonikelope
 */
public final class BetRules {

    private BetRules() {
    }

    /**
     * Minimum amount to open the betting on a street: the big blind.
     */
    public static float minOpen(float bigBlind) {
        return bigBlind;
    }

    /**
     * Minimum raise INCREMENT (how much the bet must go up by): the previous
     * raise increment this street, or the big blind if there has been none yet.
     *
     * @param lastRaiseIncrement the size of the last raise this street, 0 if none
     */
    public static float minRaiseIncrement(float lastRaiseIncrement, float bigBlind) {
        return lastRaiseIncrement > 0f ? lastRaiseIncrement : bigBlind;
    }

    /**
     * Minimum total a player must raise the current bet TO to make a legal
     * (full) raise: the current bet plus {@link #minRaiseIncrement}.
     */
    public static float minRaiseTo(float currentBet, float lastRaiseIncrement, float bigBlind) {
        return currentBet + minRaiseIncrement(lastRaiseIncrement, bigBlind);
    }

    /**
     * Whether a committed raise increment is a FULL raise (reopens the betting)
     * rather than an all-in for less than the minimum (a partial raise that does
     * not reopen). Compared at the engine's cent resolution.
     *
     * @param raiseIncrement how much the player actually raised the bet by
     * @param minRaiseIncrement the minimum legal increment ({@link #minRaiseIncrement})
     */
    public static boolean isFullRaise(float raiseIncrement, float minRaiseIncrement) {
        return Math.round((double) raiseIncrement * 100.0) >= Math.round((double) minRaiseIncrement * 100.0);
    }
}
