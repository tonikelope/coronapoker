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
 * Deterministic ranking of 5-7 card poker hands for showdown evaluation. Higher
 * rank == better hand. Ties produce equal rank values.
 *
 * This contract is intentionally narrow: callers needing equity should use
 * {@link HandStrengthEvaluator} instead.
 */
public interface HandRankResolver {

    /**
     * Numerical rank of the best 5-card hand contained in the given cards.
     *
     * @param cards 5 to 7 card indices (0..51)
     */
    int rankHand(int[] cards);

    /**
     * Compare two 5-7 card hands.
     *
     * @return 1 if {@code cards1} wins, 2 if {@code cards2} wins, 0 for a tie
     */
    int compareHands(int[] cards1, int[] cards2);
}
