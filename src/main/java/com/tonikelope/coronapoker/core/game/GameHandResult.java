/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.List;

/** Evaluated poker hand shared by the canonical controller and its adapters. */
public interface GameHandResult {

    int CARTA_ALTA = 1;
    int PAREJA = 2;
    int DOBLE_PAREJA = 3;
    int TRIO = 4;
    int ESCALERA = 5;
    int COLOR = 6;
    int FULL = 7;
    int POKER = 8;
    int ESCALERA_COLOR = 9;
    int ESCALERA_COLOR_REAL = 10;

    double getFuerza();

    void setFuerza(double strength);

    int getValue();

    String getName();

    List<? extends GameCardController> getWinners();

    List<? extends GameCardController> getMano();
}
