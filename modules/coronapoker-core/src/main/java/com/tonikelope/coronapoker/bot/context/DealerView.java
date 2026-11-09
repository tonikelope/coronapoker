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
package com.tonikelope.coronapoker.bot.context;

import com.tonikelope.coronapoker.core.game.GameOpponentStats;
import java.util.List;

/**
 * Read-only slice of the {@code Crupier}/{@code GameFrame} state required for a
 * bot to make decisions. Exposing only these methods lets the test harness
 * provide a lightweight fake dealer instead of bootstrapping the full game.
 *
 * Spanish method names match the existing fields in {@code Crupier} so that
 * Crupier can satisfy the contract by adding a single {@code implements} clause
 * and one delegation for the players-in-seating-order view.
 */
public interface DealerView {

    int getStreet();

    double getBote_total();

    /** Contribution that sets side-pot eligibility. Shared money stays in the pot total. */
    default double matchedContribution(BotPlayerView player) {
        return player.getBote();
    }

    double getApuesta_actual();

    double getUltimo_raise();

    double getCiega_grande();

    double getCiega_pequeña();

    /** Initial chip denomination used for legacy bet-size rounding. */
    default double getInitialSmallBlind() {
        return getCiega_pequeña();
    }

    int getConta_bet();

    int getLimpersCount();

    String getDealer_nick();

    String getSb_nick();

    String getBb_nick();

    String getUtg_nick();

    // With a straddle, the REAL under-the-gun (first to act) is the active player after the
    // straddler; null if there's no straddle. The straddler itself is getUtg_nick(), but it
    // acts LAST preflop (has the option), so bot position logic must distinguish the two.
    String getStraddleUtgNick();

    BotPlayerView getLast_aggressor();

    /** Last seat to increase the preflop wager in this hand. */
    default String getPreflopAggressorNick() {
        return null;
    }

    /** Observations owned by this table. */
    default GameOpponentStats getOpponentStats(String nickname) {
        return null;
    }

    /** Whether the seat still contests the pot, including all-in players. */
    default boolean isContender(BotPlayerView player) {
        return player.isActivo();
    }

    /** Whether the betting round has reopened for this seat. */
    default boolean canPlayerRaise(String nickname) {
        return true;
    }

    int getJugadoresActivos();

    /**
     * Players in seating order (active and inactive). The bot iterates this
     * list and filters by {@link BotPlayerView#isActivo()} when computing
     * positional information.
     */
    List<? extends BotPlayerView> getPlayersInSeatingOrder();

    /**
     * Community card index in the canonical Alberta encoding (rank + suit*13)
     * for position {@code i} in [0..boardSize-1], or -1 if the position is not
     * yet dealt.
     */
    int getBoardCardIndex(int i);

    /**
     * Number of community cards currently visible (0 preflop, 3 flop, 4 turn, 5
     * river).
     */
    int getBoardSize();
}
