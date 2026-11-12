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
 * Card indices accepted by this adapter must use the canonical encoding shared
 * with the Alberta library (rank + suit * 13).
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
        return showdownOdds(hole1, hole2, board).equity(1);
    }

    @Override
    public synchronized ShowdownOdds showdownOdds(int hole1, int hole2, int[] board) {
        requireCardIndex(hole1, "hole1");
        requireCardIndex(hole2, "hole2");
        org.alberta.poker.Card c1 = new org.alberta.poker.Card(hole1);
        org.alberta.poker.Card c2 = new org.alberta.poker.Card(hole2);
        he.handRank(c1, c2, toHand(board));
        int won = he.getNumWorse();
        int tied = he.getNumTied();
        double total = won + tied + he.getNumBetter();
        return new ShowdownOdds(won / total, tied / total);
    }

    @Override
    public double handStrengthVsRange(int hole1, int hole2, int[] board, double[][] rangeWeights) {
        return showdownOddsVsRange(hole1, hole2, board, rangeWeights).equity(1);
    }

    @Override
    public ShowdownOdds showdownOddsVsRange(int hole1, int hole2, int[] board, double[][] rangeWeights) {
        if (rangeWeights == null) {
            return showdownOdds(hole1, hole2, board);
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
            return new ShowdownOdds(0, 0);
        }
        return new ShowdownOdds(goodW / totalW, tiedW / totalW);
    }

    @Override
    public Potential potential(int hole1, int hole2, int[] board, boolean fullLookahead) {
        if (board == null || board.length < 3 || board.length >= 5) {
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
        if (board == null || board.length < 3 || board.length >= 5) {
            return Potential.ZERO;
        }
        if (rangeWeights == null || rangeWeights.length == 0) {
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
    public boolean isOnePair(int[] cards) {
        // Alberta encodes the category above five base-13 rank digits.
        int categorySize = 13 * 13 * 13 * 13 * 13;
        return rankHand(cards) / categorySize == 1;
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
        for (int i = 0; i < indices.length; i++) {
            int idx = indices[i];
            requireCardIndex(idx, "board[" + i + "]");
            h.addCard(idx);
        }
        return h;
    }

    private static void requireCardIndex(int index, String position) {
        if (index < 0 || index >= org.alberta.poker.Card.NUM_CARDS) {
            throw new IllegalArgumentException(
                    "Invalid Alberta card index for " + position + ": " + index);
        }
    }
}
