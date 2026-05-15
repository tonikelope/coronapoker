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

import org.alberta.poker.ai.HandPotential;
import org.alberta.poker.ai.model.WeightTable;

/**
 * Adapter that exposes the legacy University of Alberta Poker Research Group
 * library (org.alberta.poker.*) behind {@link BotEvaluator}.
 *
 * Card indices accepted by this adapter must use the canonical encoding
 * shared with the Alberta library (rank + suit * 13).
 *
 * This implementation is not thread-safe by itself: the underlying
 * {@link HandPotential} caches data between calls. A single bot decision is
 * sequential, so the singleton in {@code Bot} is safe under current usage. If
 * multiple bots must evaluate concurrently, instantiate one adapter per bot.
 */
public final class AlbertaEvaluatorAdapter implements BotEvaluator {

    private final org.alberta.poker.HandEvaluator he;
    private final HandPotential hp;

    public AlbertaEvaluatorAdapter() {
        this(new org.alberta.poker.HandEvaluator(), new HandPotential());
    }

    public AlbertaEvaluatorAdapter(org.alberta.poker.HandEvaluator handEvaluator, HandPotential handPotential) {
        this.he = handEvaluator;
        this.hp = handPotential;
    }

    @Override
    public double handStrength(int hole1, int hole2, int[] board) {
        org.alberta.poker.Card c1 = new org.alberta.poker.Card(hole1);
        org.alberta.poker.Card c2 = new org.alberta.poker.Card(hole2);
        return he.handRank(c1, c2, toHand(board));
    }

    @Override
    public double handStrengthVsN(int hole1, int hole2, int[] board, int opponents) {
        org.alberta.poker.Card c1 = new org.alberta.poker.Card(hole1);
        org.alberta.poker.Card c2 = new org.alberta.poker.Card(hole2);
        return he.handRank(c1, c2, toHand(board), opponents);
    }

    @Override
    public double handStrengthVsRange(int hole1, int hole2, int[] board, double[][] rangeWeights) {
        if (rangeWeights == null) {
            return handStrength(hole1, hole2, board);
        }
        org.alberta.poker.Hand h = toHand(board);
        org.alberta.poker.Hand myHand = new org.alberta.poker.Hand(h);
        myHand.addCard(hole1);
        myHand.addCard(hole2);
        int myRank = he.rankHand(myHand);

        org.alberta.poker.Deck d = new org.alberta.poker.Deck();
        d.reset();
        d.extractCard(new org.alberta.poker.Card(hole1));
        d.extractCard(new org.alberta.poker.Card(hole2));
        d.extractHand(h);

        org.alberta.poker.Hand opHand = new org.alberta.poker.Hand(h);
        double goodW = 0.0;
        double tiedW = 0.0;
        double totalW = 0.0;
        for (int i = d.getTopCardIndex(); i < 52; i++) {
            org.alberta.poker.Card o1 = d.getCard(i);
            int o1i = o1.getIndex();
            opHand.addCard(o1);
            for (int j = i + 1; j < 52; j++) {
                org.alberta.poker.Card o2 = d.getCard(j);
                int o2i = o2.getIndex();
                double w = rangeWeights[o1i][o2i];
                if (w <= 0.0) {
                    continue;
                }
                opHand.addCard(o2);
                int oRank = he.rankHand(opHand);
                if (myRank > oRank) {
                    goodW += w;
                } else if (myRank == oRank) {
                    tiedW += w;
                }
                totalW += w;
                opHand.removeCard();
            }
            opHand.removeCard();
        }
        if (totalW <= 0.0) {
            return 0.0;
        }
        return (goodW + tiedW / 2.0) / totalW;
    }

    @Override
    public Potential potential(int hole1, int hole2, int[] board, boolean fullLookahead) {
        if (board == null || board.length >= 5) {
            return Potential.ZERO;
        }
        org.alberta.poker.Card c1 = new org.alberta.poker.Card(hole1);
        org.alberta.poker.Card c2 = new org.alberta.poker.Card(hole2);
        double ppot = hp.ppot_raw(c1, c2, toHand(board), fullLookahead && board.length == 3);
        double npot = hp.getLastNPot();
        return new Potential(ppot, npot);
    }

    @Override
    public Potential potentialVsRange(int hole1, int hole2, int[] board, double[][] rangeWeights, boolean fullLookahead) {
        if (board == null || board.length >= 5) {
            return Potential.ZERO;
        }
        if (rangeWeights == null) {
            return potential(hole1, hole2, board, fullLookahead);
        }
        org.alberta.poker.Card c1 = new org.alberta.poker.Card(hole1);
        org.alberta.poker.Card c2 = new org.alberta.poker.Card(hole2);
        WeightTable w = new WeightTable();
        for (int i = 0; i < 52; i++) {
            for (int j = i + 1; j < 52; j++) {
                w.setCell(i, j, rangeWeights[i][j]);
            }
        }
        double ppot = hp.ppot(c1, c2, toHand(board), w, fullLookahead && board.length == 3);
        double npot = hp.getLastNPot();
        return new Potential(ppot, npot);
    }

    @Override
    public int rankHand(int[] cards) {
        return he.rankHand(toHand(cards));
    }

    @Override
    public int compareHands(int[] cards1, int[] cards2) {
        return he.compareHands(toHand(cards1), toHand(cards2));
    }

    private static org.alberta.poker.Hand toHand(int[] indices) {
        org.alberta.poker.Hand h = new org.alberta.poker.Hand();
        if (indices == null) {
            return h;
        }
        for (int idx : indices) {
            h.addCard(idx);
        }
        return h;
    }
}
