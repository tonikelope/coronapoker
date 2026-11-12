/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.bot.eval;

import com.tonikelope.coronapoker.bot.eval.HandStrengthEvaluator.ShowdownOdds;

/** Current heads-up odds, draw potential and estimated pot share at showdown. */
public final class PostflopEvaluation {

    private final ShowdownOdds currentOdds;
    private final Potential potential;
    private final double[] equity;

    PostflopEvaluation(ShowdownOdds currentOdds, Potential potential, double[] equity) {
        this.currentOdds = currentOdds;
        this.potential = potential;
        this.equity = equity;
    }

    public ShowdownOdds currentOdds() {
        return currentOdds;
    }

    public Potential potential() {
        return potential;
    }

    public double equity(int opponents) {
        return equity[opponents];
    }
}
