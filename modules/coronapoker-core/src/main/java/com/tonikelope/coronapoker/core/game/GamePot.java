/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.List;

/** Pot/side-pot chain contract consumed by the UI-neutral dealer. */
public interface GamePot {

    double getBet();

    double getTotal();

    /** Amount contributed by the sole eligible player that nobody matched. */
    double getUncalledBet();

    int getSide_pot_count();

    GamePot getSidePot();

    List<? extends GamePlayerController> getPlayerControllers();

    void addPlayerController(GamePlayerController player);

    /** The portion of a player's contribution used to set side-pot limits. */
    double matchedContribution(GamePlayerController player);

    /** Marks a single player's posted ante as shared money in the main pot. */
    void recordTableAnte(GamePlayerController payer, double amount);

    void genSidePots();
}
