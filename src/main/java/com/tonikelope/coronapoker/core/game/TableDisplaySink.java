/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.core.game;

/** Immediate, semantic table-display operations retained by the classic flow. */
public interface TableDisplaySink {

    void showPot(double amount, Double mainPotProfit);

    void showPotText(String text);

    void hideStreetBets();

    void showStreetBets(double amount);

    void showBlinds(double smallBlind, double bigBlind);

    void showHandNumber(int handNumber);

    void refresh();

    void setLightsSuppressed(boolean suppressed);

    void downgradeAndRefreshSeats();

    void showShuffleTurn(String nickname);

    void hideShuffleTurn();

    static TableDisplaySink noop() {
        return new TableDisplaySink() {
            @Override
            public void showPot(double amount, Double mainPotProfit) {
            }

            @Override
            public void showPotText(String text) {
            }

            @Override
            public void hideStreetBets() {
            }

            @Override
            public void showStreetBets(double amount) {
            }

            @Override
            public void showBlinds(double smallBlind, double bigBlind) {
            }

            @Override
            public void showHandNumber(int handNumber) {
            }

            @Override
            public void refresh() {
            }

            @Override
            public void setLightsSuppressed(boolean suppressed) {
            }

            @Override
            public void downgradeAndRefreshSeats() {
            }

            @Override
            public void showShuffleTurn(String nickname) {
            }

            @Override
            public void hideShuffleTurn() {
            }
        };
    }
}
