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

/**
 * Mutable in-memory player used by the offline harness. Overrides the index
 * helpers so the harness never needs to construct Swing-bound corona Card
 * objects; getHoleCard1/getHoleCard2 throw to make accidental UI-path usage
 * loud during tests.
 */
public final class TestBotPlayer implements BotPlayerView {

    private final String nickname;
    private double stack;
    private double bet;
    private double committed;
    private boolean activo;
    private boolean folded;
    private int holeCard1Index = -1;
    private int holeCard2Index = -1;

    public TestBotPlayer(String nickname, double stack) {
        this.nickname = nickname;
        this.stack = stack;
        this.activo = true;
    }

    public void setHoleCards(int idx1, int idx2) {
        this.holeCard1Index = idx1;
        this.holeCard2Index = idx2;
    }

    public void setStack(double stack) {
        this.stack = stack;
    }

    public void setBet(double bet) {
        this.bet = bet;
    }

    void resetHandContribution() {
        folded = false;
        bet = 0;
        committed = 0;
    }

    public void commit(double amount) {
        long chips = SimulatedBettingRound.cents(amount);
        stack = SimulatedBettingRound.money(SimulatedBettingRound.cents(stack) - chips);
        bet = SimulatedBettingRound.money(SimulatedBettingRound.cents(bet) + chips);
        committed = SimulatedBettingRound.money(SimulatedBettingRound.cents(committed) + chips);
    }

    @Override
    public double getBote() {
        return committed;
    }

    /** Folded seats keep their table position until the next hand. */
    public void fold() {
        folded = true;
    }

    public boolean isContending() {
        return activo && !folded;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    @Override
    public String getNickname() {
        return nickname;
    }

    @Override
    public double getStack() {
        return stack;
    }

    @Override
    public double getBet() {
        return bet;
    }

    @Override
    public boolean isActivo() {
        return activo;
    }

    @Override
    public int getHoleCard1Index() {
        return holeCard1Index;
    }

    @Override
    public int getHoleCard2Index() {
        return holeCard2Index;
    }

    @Override
    public Object getHoleCard1() {
        throw new UnsupportedOperationException("Harness fakes expose hole cards via getHoleCard1Index()");
    }

    @Override
    public Object getHoleCard2() {
        throw new UnsupportedOperationException("Harness fakes expose hole cards via getHoleCard2Index()");
    }
}
