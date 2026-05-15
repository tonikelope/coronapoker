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
 * Estimates how often a hole-card pair currently wins against opponent ranges.
 *
 * Card indices follow the canonical encoding used by the bot subsystem
 * (rank in [0..12], suit in [0..3], index = rank + suit * 13, range [0..51]).
 */
public interface HandStrengthEvaluator {

    /**
     * Probability that the given two-card hand beats a single random opponent
     * hand on the current board.
     *
     * @param hole1 first hole card index (0..51)
     * @param hole2 second hole card index (0..51)
     * @param board board card indices (3..5 entries; may be empty for preflop)
     * @return value in [0.0, 1.0]
     */
    double handStrength(int hole1, int hole2, int[] board);

    /**
     * Approximation of beating {@code opponents} independent random hands.
     * Equivalent to {@code handStrength^opponents} when opponents act
     * independently; concrete implementations are free to use a tighter
     * estimator.
     */
    double handStrengthVsN(int hole1, int hole2, int[] board, int opponents);

    /**
     * Probability that the given two-card hand beats an opponent whose range is
     * described by a weighted 52x52 matrix. Weights outside the diagonal must
     * be symmetric (weights[i][j] == weights[j][i]); the diagonal is ignored
     * (a player cannot hold two copies of the same card).
     */
    double handStrengthVsRange(int hole1, int hole2, int[] board, double[][] rangeWeights);
}
