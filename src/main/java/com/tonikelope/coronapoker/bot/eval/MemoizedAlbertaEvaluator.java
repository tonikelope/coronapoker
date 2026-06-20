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
 * Drop-in {@link BotEvaluator} that produces numbers identical to
 * {@link AlbertaEvaluatorAdapter} but computes {@link #potential} with
 * {@link MemoizedHandPotential}, cutting the flop two-card look-ahead from
 * ~2.7M hand evaluations to the few hundred thousand distinct ones.
 *
 * <p>Hand strength, rank and compare are not the bottleneck and are delegated to
 * a plain {@link AlbertaEvaluatorAdapter} sharing the same {@link HandEvaluator}.
 * Range-weighted potential is rarely used and also delegated unchanged.</p>
 *
 * <p>This is the evaluator wired into {@code Bot.EVALUATOR} in production. Its
 * PPot/NPot are numerically identical to the Alberta adapter — the equivalence is
 * gated by {@code MemoizedHandPotentialTest} — so it changes bot speed, never bot
 * decisions. Not thread-safe: a single shared instance is correct only because bot
 * decisions are evaluated sequentially, the same contract as the
 * {@link AlbertaEvaluatorAdapter} it replaced.</p>
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
    public double handStrengthVsN(int hole1, int hole2, int[] board, int opponents) {
        return delegate.handStrengthVsN(hole1, hole2, board, opponents);
    }

    @Override
    public double handStrengthVsRange(int hole1, int hole2, int[] board, double[][] rangeWeights) {
        return delegate.handStrengthVsRange(hole1, hole2, board, rangeWeights);
    }

    @Override
    public Potential potential(int hole1, int hole2, int[] board, boolean fullLookahead) {
        if (board == null || board.length >= 5 || board.length < 3) {
            return Potential.ZERO;
        }
        double[] pn = fastPotential.ppotNpot(hole1, hole2, board, fullLookahead);
        return new Potential(pn[0], pn[1]);
    }

    @Override
    public Potential potentialVsRange(int hole1, int hole2, int[] board, double[][] rangeWeights, boolean fullLookahead) {
        return delegate.potentialVsRange(hole1, hole2, board, rangeWeights, fullLookahead);
    }

    @Override
    public int rankHand(int[] cards) {
        return delegate.rankHand(cards);
    }

    @Override
    public int compareHands(int[] cards1, int[] cards2) {
        return delegate.compareHands(cards1, cards2);
    }
}
