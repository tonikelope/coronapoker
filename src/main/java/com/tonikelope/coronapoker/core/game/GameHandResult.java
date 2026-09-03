/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.List;

/** Evaluated poker hand shared by the canonical controller and its adapters. */
public interface GameHandResult {

    double getFuerza();

    int getValue();

    String getName();

    List<? extends GameCardController> getWinners();

    List<? extends GameCardController> getMano();
}
