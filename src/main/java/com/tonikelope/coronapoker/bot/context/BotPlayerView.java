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

/**
 * Read-only slice of a Player that the bot subsystem inspects to make its
 * decisions. Splitting it out of {@link com.tonikelope.coronapoker.Player} lets
 * test harnesses and replay tools fabricate participants without dragging in
 * Swing-bound concrete implementations.
 *
 * The full {@code Player} interface extends this contract, so any concrete
 * Player automatically satisfies it.
 */
public interface BotPlayerView {

    String getNickname();

    double getStack();

    double getBet();

    boolean isActivo();

    /**
     * First hole card as an Alberta-encoded index (rank + suit*13), or -1 when
     * the card is not available.
     */
    int getHoleCard1Index();

    /**
     * Second hole card as an Alberta-encoded index.
     */
    int getHoleCard2Index();
}
