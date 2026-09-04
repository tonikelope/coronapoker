/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.List;

/** Pot/side-pot chain contract consumed by the UI-neutral dealer. */
public interface GamePot {

    double getBet();

    double getTotal();

    int getSide_pot_count();

    GamePot getSidePot();

    List<? extends GamePlayerController> getPlayerControllers();

    void addPlayerController(GamePlayerController player);

    void genSidePots();
}
