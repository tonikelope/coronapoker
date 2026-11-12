/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.bot.eval;

import org.alberta.poker.HandEvaluator;
import org.alberta.poker.ai.HandPotential;

/**
 * Production evaluator. It computes draw potential and terminal pot share
 * from one enumeration, reusing the terminal ranks across card assignments.
 * Other operations use the Alberta adapter.
 */
public final class MemoizedAlbertaEvaluator implements BotEvaluator {

    private final AlbertaEvaluatorAdapter delegate;
    private final MemoizedHandPotential fastPotential;

    public MemoizedAlbertaEvaluator() {
        HandEvaluator he = new HandEvaluator();
        this.delegate = new AlbertaEvaluatorAdapter(he, new HandPotential());
        this.fastPotential = new MemoizedHandPotential(he);
    }

    @Override
    public double handStrength(int hole1, int hole2, int[] board) {
        return delegate.handStrength(hole1, hole2, board);
    }

    @Override
    public ShowdownOdds showdownOdds(int hole1, int hole2, int[] board) {
        return delegate.showdownOdds(hole1, hole2, board);
    }

    @Override
    public double handStrengthVsRange(int hole1, int hole2, int[] board, double[][] rangeWeights) {
        return delegate.handStrengthVsRange(hole1, hole2, board, rangeWeights);
    }

    @Override
    public ShowdownOdds showdownOddsVsRange(int hole1, int hole2, int[] board, double[][] rangeWeights) {
        return delegate.showdownOddsVsRange(hole1, hole2, board, rangeWeights);
    }

    @Override
    public Potential potential(int hole1, int hole2, int[] board, boolean fullLookahead) {
        if (board == null || board.length >= 5 || board.length < 3) {
            return Potential.ZERO;
        }
        return fastPotential.evaluate(hole1, hole2, board, board.length == 3 && fullLookahead, 0).potential();
    }

    @Override
    public PostflopEvaluation evaluatePostflop(int hole1, int hole2, int[] board, int maxOpponents) {
        if (board.length == 3 || board.length == 4) {
            return fastPotential.evaluate(hole1, hole2, board, board.length == 3, maxOpponents);
        }
        if (board.length == 5 && maxOpponents > 1) {
            return fastPotential.evaluateRiver(hole1, hole2, board, maxOpponents);
        }
        return BotEvaluator.super.evaluatePostflop(hole1, hole2, board, maxOpponents);
    }

    @Override
    public Potential potentialVsRange(int hole1, int hole2, int[] board, double[][] rangeWeights, boolean fullLookahead) {
        return delegate.potentialVsRange(hole1, hole2, board, rangeWeights, fullLookahead);
    }

    @Override
    public boolean isOnePair(int[] cards) {
        return delegate.isOnePair(cards);
    }

    @Override
    public int rankHand(int[] cards) {
        return delegate.rankHand(cards);
    }

    @Override
    public int[] opponentRanks(int[] board) {
        return delegate.opponentRanks(board);
    }

    @Override
    public double preflopAllInEquity(int hole1, int hole2, double[][] weights) {
        return delegate.preflopAllInEquity(hole1, hole2, weights);
    }

    @Override
    public int compareHands(int[] cards1, int[] cards2) {
        return delegate.compareHands(cards1, cards2);
    }
}
