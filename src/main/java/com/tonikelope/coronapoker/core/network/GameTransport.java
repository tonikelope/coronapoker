/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.core.network;

/** Transport boundary used by a running game, independent of its frontend. */
public interface GameTransport {

    String hostNickname();

    String tablePassword();

    boolean gameStarted();

    ConfirmationTracker confirmations();

    void sendCommandToHost(String clearText);

    void closeHostConnection();

    static GameTransport unavailable() {
        ConfirmationTracker confirmations = new ConfirmationTracker();
        return new GameTransport() {
            @Override
            public String hostNickname() {
                return "";
            }

            @Override
            public String tablePassword() {
                return null;
            }

            @Override
            public boolean gameStarted() {
                return false;
            }

            @Override
            public ConfirmationTracker confirmations() {
                return confirmations;
            }

            @Override
            public void sendCommandToHost(String clearText) {
                throw new IllegalStateException("Game transport is unavailable");
            }

            @Override
            public void closeHostConnection() {
            }
        };
    }
}
