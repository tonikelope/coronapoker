/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.awt.geom.Point2D;
import java.net.URL;
import java.util.ArrayList;
import javax.swing.JLabel;

/**
 * Minimal {@link Player} test double scoped to HandPot side-pot
 * characterization. Only {@code getBote()}, {@code getDecision()},
 * {@code isActivo()} and {@code getNickname()} carry meaning — those are all
 * {@code HandPot.genSidePots()/getTotal()/addPlayer()} ever read. Every other
 * Player method throws, the same "loud on accidental use" convention
 * {@code TestBotPlayer} follows for its hole cards.
 */
public final class FakePotPlayer implements Player {

    private final String nickname;
    private final double bote;
    private final int decision;
    private final boolean activo;

    public FakePotPlayer(String nickname, double bote, int decision, boolean activo) {
        this.nickname = nickname;
        this.bote = bote;
        this.decision = decision;
        this.activo = activo;
    }

    // ---- the only state HandPot reads -------------------------------------
    @Override
    public String getNickname() {
        return nickname;
    }

    @Override
    public double getBote() {
        return bote;
    }

    @Override
    public int getDecision() {
        return decision;
    }

    @Override
    public boolean isActivo() {
        return activo;
    }

    // ---- everything else is irrelevant to pot math: fail loudly -----------
    private static UnsupportedOperationException nope() {
        return new UnsupportedOperationException("FakePotPlayer is only for HandPot characterization");
    }

    @Override
    public void setContaWin(int conta) {
        throw nope();
    }

    @Override
    public int getContaWin() {
        throw nope();
    }

    @Override
    public void resetGUI() {
        throw nope();
    }

    @Override
    public void ordenarCartas() {
        throw nope();
    }

    @Override
    public void destaparCartas(boolean sound) {
        throw nope();
    }

    @Override
    public int getResponseTime() {
        throw nope();
    }

    @Override
    public boolean isCalentando() {
        throw nope();
    }

    @Override
    public void stopActionTimer() {
        throw nope();
    }

    @Override
    public boolean isTurno() {
        throw nope();
    }

    @Override
    public void resetBote() {
        throw nope();
    }

    @Override
    public void checkGameOver() {
        throw nope();
    }

    @Override
    public void showCards(String jugada) {
        throw nope();
    }

    @Override
    public int getBuyin() {
        throw nope();
    }

    @Override
    public void setTimeout(boolean val) {
        throw nope();
    }

    @Override
    public void setNickname(String name) {
        throw nope();
    }

    @Override
    public Card getHoleCard1() {
        throw nope();
    }

    @Override
    public Card getHoleCard2() {
        throw nope();
    }

    @Override
    public ArrayList<Card> getHoleCards() {
        throw nope();
    }

    @Override
    public void setWinner(String msg) {
        throw nope();
    }

    @Override
    public void setLoser(String msg) {
        throw nope();
    }

    @Override
    public void repaintLastAction() {
        throw nope();
    }

    @Override
    public void pagar(double pasta, Integer sec_pot) {
        throw nope();
    }

    @Override
    public void marcarBotePot(int sec_pot) {
        throw nope();
    }

    @Override
    public double getBet() {
        throw nope();
    }

    @Override
    public double postAnte(double ante) {
        throw nope();
    }

    @Override
    public double postStraddle(double amount) {
        throw nope();
    }

    @Override
    public void disableUTG() {
        throw nope();
    }

    @Override
    public void setUTG() {
        throw nope();
    }

    @Override
    public void refreshPos() {
        throw nope();
    }

    @Override
    public void refreshPositionChipIcons() {
        throw nope();
    }

    @Override
    public JLabel getChip_label() {
        throw nope();
    }

    @Override
    public Point2D getPositionChipScreenCenter(int chip_w, int chip_h) {
        throw nope();
    }

    @Override
    public void nuevaMano() {
        throw nope();
    }

    @Override
    public void esTuTurno() {
        throw nope();
    }

    @Override
    public void setStack(double stack) {
        throw nope();
    }

    @Override
    public double getStack() {
        throw nope();
    }

    @Override
    public void setBet(double bet) {
        throw nope();
    }

    @Override
    public void resetBetDecision() {
        throw nope();
    }

    @Override
    public boolean isSpectator() {
        throw nope();
    }

    @Override
    public boolean isExit() {
        throw nope();
    }

    @Override
    public void setExit() {
        throw nope();
    }

    @Override
    public String getLastActionString() {
        throw nope();
    }

    @Override
    public void setBuyin(int buyin) {
        throw nope();
    }

    @Override
    public double getPagar() {
        throw nope();
    }

    @Override
    public void setPagar(double pagar) {
        throw nope();
    }

    @Override
    public void setSpectator(String msg) {
        throw nope();
    }

    @Override
    public void unsetSpectator() {
        throw nope();
    }

    @Override
    public void setAvatar() {
        throw nope();
    }

    @Override
    public void setSpectatorBB(boolean bb) {
        throw nope();
    }

    @Override
    public boolean isTimeout() {
        throw nope();
    }

    @Override
    public void setPlayerActionIcon(String icon) {
        throw nope();
    }

    @Override
    public void hidePlayerActionIcon() {
        throw nope();
    }

    @Override
    public void setNotifyImageChatLabel(URL u) {
        throw nope();
    }

    @Override
    public void setNotifyTTSChatLabel() {
        throw nope();
    }

    @Override
    public JLabel getChat_notify_label() {
        throw nope();
    }

    @Override
    public void setJugadaParcial(Hand jugada, boolean ganador, float win_per) {
        throw nope();
    }

    @Override
    public boolean isWinner() {
        throw nope();
    }

    @Override
    public boolean isMuestra() {
        throw nope();
    }

    @Override
    public int getHoleCard1Index() {
        throw nope();
    }

    @Override
    public int getHoleCard2Index() {
        throw nope();
    }
}
