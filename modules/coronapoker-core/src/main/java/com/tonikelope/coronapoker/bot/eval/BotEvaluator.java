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
 * Umbrella aggregate of the evaluation contracts required by the bot subsystem.
 * Provided as a convenience so that wiring code can inject a single dependency
 * rather than three.
 */
public interface BotEvaluator extends HandStrengthEvaluator, DrawPotentialEvaluator, HandRankResolver {

    /** Evaluates one public board for every pot size from zero to maxOpponents. */
    default PostflopEvaluation evaluatePostflop(int hole1, int hole2, int[] board, int maxOpponents) {
        ShowdownOdds odds = showdownOdds(hole1, hole2, board);
        Potential draw = board.length == 5 ? Potential.ZERO : potential(hole1, hole2, board, board.length == 3);
        double[] equity = new double[maxOpponents + 1];
        equity[0] = 1;
        double headsUp = odds.equity(1);
        double projected = headsUp + (1 - headsUp) * draw.ppot() - headsUp * draw.npot();
        for (int opponents = 1; opponents <= maxOpponents; opponents++) {
            equity[opponents] = board.length == 5 ? odds.equity(opponents) : Math.pow(projected, opponents);
        }
        return new PostflopEvaluation(odds, draw, equity);
    }
}
