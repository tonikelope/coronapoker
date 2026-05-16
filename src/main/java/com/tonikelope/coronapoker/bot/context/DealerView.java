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

import com.tonikelope.coronapoker.Card;
import java.util.List;

/**
 * Read-only slice of the {@code Crupier}/{@code GameFrame} state required for a
 * bot to make decisions. Exposing only these methods lets the test harness
 * provide a lightweight fake dealer instead of bootstrapping the full game.
 *
 * Spanish method names match the existing fields in {@code Crupier} so that
 * Crupier can satisfy the contract by adding a single {@code implements}
 * clause and one delegation for the players-in-seating-order view.
 */
public interface DealerView {

    int getStreet();

    float getBote_total();

    float getApuesta_actual();

    float getUltimo_raise();

    float getCiega_grande();

    float getCiega_pequeña();

    int getConta_bet();

    int getLimpersCount();

    String getDealer_nick();

    String getSb_nick();

    String getBb_nick();

    String getUtg_nick();

    BotPlayerView getLast_aggressor();

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
     * Number of community cards currently visible (0 preflop, 3 flop, 4 turn,
     * 5 river).
     */
    int getBoardSize();
}
