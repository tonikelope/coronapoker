/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.List;

/** Canonical player behavior with no Swing, AWT or libGDX types. */
public interface GamePlayerController
        extends com.tonikelope.coronapoker.bot.context.BotPlayerView {

    /** Binds an automated seat to the canonical dealer after table assembly. */
    default void bindDealer(com.tonikelope.coronapoker.bot.context.DealerView dealer) {
    }

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

    /**
     * Applies the already resolved dealer/blind position for the hand and posts
     * its forced blind. Recovery resolves positions after the ordinary hand
     * reset, so it must be possible to repeat only this narrow step without
     * consuming a rebuy or pending winnings twice.
     */
    default void applyCurrentHandPosition() {
        // Lightweight/testing controllers that do not model forced-position
        // presentation have nothing to apply. Real Swing and core controllers
        // override this hook with the canonical blind/dealer update.
    }

    void esTuTurno();

    /**
     * Releases a displayed local turn without manufacturing a poker action.
     * This is used when every opponent has already left the hand while the
     * local player was deciding, so the pot can be awarded immediately.
     */
    default void cancelTurnWithoutDecision() {
        stopActionTimer();
    }

    /**
     * Whether the canonical dealer must enforce this local controller's turn
     * deadline. Swing owns its countdown in {@code LocalPlayer}; native/core
     * controllers deliberately do not have a widget timer and opt in here.
     */
    default boolean requiresDealerManagedTurnTimeout() {
        return false;
    }

    /**
     * Atomically applies the canonical timeout action for the current turn.
     * Implementations must reject stale calls after a manual decision or turn
     * change. The default keeps legacy/Swing controllers completely untouched.
     */
    default boolean submitTurnTimeoutDecision() {
        return false;
    }

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

    /** Clears per-hand state owned by an automated decision provider, if any. */
    default void resetAutomatedDecisionState() {
    }

    /** Whether this seat has an in-process automated decision provider. */
    default boolean hasAutomatedDecisionProvider() {
        return false;
    }

    /** Requests one decision from the seat's automated provider. */
    default int calculateAutomatedDecision(int opponentCount) {
        throw new IllegalStateException("Player has no automated decision provider");
    }

    /** Bet size selected by the last automated decision. */
    default double automatedBetSize() {
        throw new IllegalStateException("Player has no automated decision provider");
    }

    /** Feeds the settled hand result back to an automated provider, if present. */
    default void recordAutomatedHandResult(boolean winner) {
    }

    /** Applies the host-authoritative decision for a non-local seat. */
    default void applyRemoteDecision(int decision, double bet) {
        throw new IllegalStateException("Player cannot apply a remote decision");
    }

    /**
     * Neutral players have no widget-owned remote all-in hook, so the dealer
     * must start their cinematic. Swing RemotePlayer already owns that hook.
     */
    default boolean requiresDealerRemoteAllInCinematic() {
        return false;
    }

    /** Per-seat monitor that serializes late card reveals. */
    default Object revealLock() {
        return this;
    }

    /** Updates optional connection telemetry without coupling the engine to a widget. */
    default void applyTelemetry(int latency1, int latency2, int reconnectionCount) {
    }

    /** Whether this seat may be force-revealed by the IWTSTH rule. */
    default boolean isIwtsthCandidate() {
        return false;
    }

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

    boolean isLoser();

    boolean isMuestra();

    void setMuestra(boolean showing);

    void setConta_rabbit(int count);

    void setRabbitJugada(String handName,
            List<? extends GameCardController> rabbitHandCards);

    void setChipForcedHidden(boolean hidden);

    int getParguela_counter();

    void disableUTG();

    void setUTG();
}
