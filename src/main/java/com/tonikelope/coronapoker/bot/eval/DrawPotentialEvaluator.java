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
 * Estimates the positive (PPot) and negative (NPot) hand potential, i.e. the
 * probability that a hand currently behind/ahead will swap state when remaining
 * board cards are dealt. See Papp 1998, section 5.3.
 *
 * Card indices follow the canonical encoding (rank + suit * 13, range 0..51).
 */
public interface DrawPotentialEvaluator {

    /**
     * Compute PPot/NPot against a uniformly random opponent hand.
     *
     * @param hole1 first hole card index
     * @param hole2 second hole card index
     * @param board current board card indices (3 or 4 entries; river returns zero potential)
     * @param fullLookahead if true, perform the slow two-card lookahead (only meaningful when board has 3 cards)
     */
    Potential potential(int hole1, int hole2, int[] board, boolean fullLookahead);

    /**
     * Compute PPot/NPot weighting opponent holdings by the given 52x52 range
     * matrix. Implementations may treat a null or empty matrix as the uniform
     * range.
     */
    Potential potentialVsRange(int hole1, int hole2, int[] board, double[][] rangeWeights, boolean fullLookahead);
}
