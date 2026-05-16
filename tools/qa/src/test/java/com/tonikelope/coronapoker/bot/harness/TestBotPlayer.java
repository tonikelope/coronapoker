/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.bot.harness;

import com.tonikelope.coronapoker.Card;
import com.tonikelope.coronapoker.bot.context.BotPlayerView;

/**
 * Mutable in-memory player used by the offline harness. Overrides the index
 * helpers so the harness never needs to construct Swing-bound corona Card
 * objects; getHoleCard1/getHoleCard2 throw to make accidental UI-path usage
 * loud during tests.
 */
public final class TestBotPlayer implements BotPlayerView {

    private final String nickname;
    private float stack;
    private float bet;
    private boolean activo;
    private int holeCard1Index = -1;
    private int holeCard2Index = -1;

    public TestBotPlayer(String nickname, float stack) {
        this.nickname = nickname;
        this.stack = stack;
        this.activo = true;
    }

    public void setHoleCards(int idx1, int idx2) {
        this.holeCard1Index = idx1;
        this.holeCard2Index = idx2;
    }

    public void setStack(float stack) {
        this.stack = stack;
    }

    public void setBet(float bet) {
        this.bet = bet;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    @Override
    public String getNickname() {
        return nickname;
    }

    @Override
    public float getStack() {
        return stack;
    }

    @Override
    public float getBet() {
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
    public Card getHoleCard1() {
        throw new UnsupportedOperationException("Harness fakes expose hole cards via getHoleCard1Index()");
    }

    @Override
    public Card getHoleCard2() {
        throw new UnsupportedOperationException("Harness fakes expose hole cards via getHoleCard2Index()");
    }
}
