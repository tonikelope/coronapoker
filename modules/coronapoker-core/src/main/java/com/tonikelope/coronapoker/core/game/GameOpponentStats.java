/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/** Mutable observations used by automated players across hands. */
public interface GameOpponentStats {

    void recordHandPlayed();

    void recordVPIP(int handId);

    void recordPFR(int handId);

    void recordPostFlopBetOrRaise();

    void recordPostFlopCall();

    default void recordBlindDefense(boolean folded) {
    }

    default double foldToBlindStealRate() {
        return 0.5;
    }

    /** Observe a completed postflop action and the wager the player faced. */
    default void recordPostflopDecision(int street, boolean facingBet, boolean invested, boolean raised, boolean folded) {
    }

    /** Smooth the observed fold frequency with a prior for this decision. */
    default double foldToBetRate(int street, boolean invested, double prior) {
        return prior;
    }

    /** Smooth the frequency of betting when no wager is outstanding. */
    default double openingBetRate(int street, double prior) {
        return prior;
    }

    default boolean playedVoluntarilyPreflop() {
        return false;
    }

    default double voluntaryPreflopRate() {
        return 0.40;
    }

    default boolean hasEnoughData() {
        return false;
    }

    default boolean isStation() {
        return false;
    }

    default boolean looksPassiveStation() {
        return false;
    }

    default boolean isNit() {
        return false;
    }

    default boolean isManiac() {
        return false;
    }
}
