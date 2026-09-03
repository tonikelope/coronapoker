/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.List;

/** Canonical player behavior with no Swing, AWT or libGDX types. */
public interface GamePlayerController
        extends com.tonikelope.coronapoker.bot.context.BotPlayerView {

    int NODEC = -1;
    int FOLD = 1;
    int CHECK = 2;
    int BET = 3;
    int ALLIN = 4;

    int DEALER = 11;
    int SMALL_BLIND = 12;
    int BIG_BLIND = 13;
    int DEAD_DEALER = 14;
    int STRADDLE = 15;
    int DEALER_STRADDLE = 16;

    void setContaWin(int count);

    int getContaWin();

    void ordenarCartas();

    int getResponseTime();

    boolean isCalentando();

    boolean isActivo();

    void stopActionTimer();

    boolean isTurno();

    void resetBote();

    void pagar(double amount, Integer sidePot);

    void marcarBotePot(int sidePot);

    void repaintLastAction();

    void checkGameOver();

    int getBuyin();

    double getBote();

    void setTimeout(boolean value);

    String getNickname();

    PlayerState getState();

    void setNickname(String name);

    GameCardController getHoleCard1();

    GameCardController getHoleCard2();

    List<? extends GameCardController> getHoleCards();

    void nuevaMano();

    void esTuTurno();

    int getDecision();

    void markFoldedOnRecover();

    void setStack(double stack);

    void setCounterRollDeferred(boolean deferred);

    void rollCountersToModel();

    double getStack();

    void setBet(double bet);

    double postAnte(double ante);

    double postStraddle(double amount);

    void resetBetDecision();

    boolean isSpectator();

    boolean isExit();

    void setExit();

    String getLastActionString();

    void setBuyin(int buyin);

    double getPagar();

    void setPagar(double payment);

    void setSpectator(String message);

    void unsetSpectator();

    void setSpectatorBB(boolean bigBlind);

    boolean isTimeout();

    void setJugadaParcial(GameHandResult hand, boolean winner,
            float winPercentage);

    boolean isWinner();

    boolean isMuestra();

    void disableUTG();

    void setUTG();
}
