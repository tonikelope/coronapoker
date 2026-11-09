/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/** Mutable observations used by automated players across hands. */
public interface GameOpponentStats {

    void recordHandPlayed();

    void recordVPIP(int handId);

    void recordPFR(int handId);

    void recordPostFlopBetOrRaise();

    void recordPostFlopCall();

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
