/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.bot.harness;

import com.tonikelope.coronapoker.bot.context.BotPlayerView;
import com.tonikelope.coronapoker.bot.context.DealerView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Mutable DealerView used by the offline harness. Everything is a plain field
 * the simulator writes to as the hand progresses. There is no thread-safety
 * because the harness drives the entire hand serially.
 */
public final class TestDealer implements DealerView {

    private int street;
    private float pot;
    private float currentBet;
    private float lastRaise;
    private float bigBlind;
    private float smallBlind;
    private int betCount;
    private int limpersCount;
    private String dealerNick;
    private String sbNick;
    private String bbNick;
    private String utgNick;
    private BotPlayerView lastAggressor;
    private final List<BotPlayerView> seating = new ArrayList<>();
    private final int[] boardCards = new int[]{-1, -1, -1, -1, -1};
    private int boardSize;

    public void setBlinds(float small, float big) {
        this.smallBlind = small;
        this.bigBlind = big;
    }

    public void setStreet(int street) {
        this.street = street;
    }

    public void setPot(float pot) {
        this.pot = pot;
    }

    public void setCurrentBet(float currentBet) {
        this.currentBet = currentBet;
    }

    public void setLastRaise(float lastRaise) {
        this.lastRaise = lastRaise;
    }

    public void setBetCount(int betCount) {
        this.betCount = betCount;
    }

    public void setLimpersCount(int limpersCount) {
        this.limpersCount = limpersCount;
    }

    public void setLastAggressor(BotPlayerView lastAggressor) {
        this.lastAggressor = lastAggressor;
    }

    public void setRoles(String dealer, String sb, String bb, String utg) {
        this.dealerNick = dealer;
        this.sbNick = sb;
        this.bbNick = bb;
        this.utgNick = utg;
    }

    public void setSeating(List<? extends BotPlayerView> players) {
        seating.clear();
        seating.addAll(players);
    }

    public void resetBoard() {
        Arrays.fill(boardCards, -1);
        boardSize = 0;
    }

    public void appendBoardCard(int cardIndex) {
        if (boardSize >= boardCards.length) {
            throw new IllegalStateException("Board already full");
        }
        boardCards[boardSize++] = cardIndex;
    }

    // --- DealerView contract ------------------------------------------------

    @Override
    public int getStreet() {
        return street;
    }

    @Override
    public float getBote_total() {
        return pot;
    }

    @Override
    public float getApuesta_actual() {
        return currentBet;
    }

    @Override
    public float getUltimo_raise() {
        return lastRaise;
    }

    @Override
    public float getCiega_grande() {
        return bigBlind;
    }

    @Override
    public float getCiega_pequeña() {
        return smallBlind;
    }

    @Override
    public int getConta_bet() {
        return betCount;
    }

    @Override
    public int getLimpersCount() {
        return limpersCount;
    }

    @Override
    public String getDealer_nick() {
        return dealerNick;
    }

    @Override
    public String getSb_nick() {
        return sbNick;
    }

    @Override
    public String getBb_nick() {
        return bbNick;
    }

    @Override
    public String getUtg_nick() {
        return utgNick;
    }

    @Override
    public BotPlayerView getLast_aggressor() {
        return lastAggressor;
    }

    @Override
    public int getJugadoresActivos() {
        int n = 0;
        for (BotPlayerView p : seating) {
            if (p.isActivo()) {
                n++;
            }
        }
        return n;
    }

    @Override
    public List<? extends BotPlayerView> getPlayersInSeatingOrder() {
        return seating;
    }

    @Override
    public int getBoardSize() {
        return boardSize;
    }

    @Override
    public int getBoardCardIndex(int i) {
        if (i < 0 || i >= boardSize) {
            return -1;
        }
        return boardCards[i];
    }
}
