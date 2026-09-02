/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.core.game;

/** Semantic updates sent back to the lobby while a game takes ownership. */
public interface LobbyTransitionSink {

    void removeParticipant(String nickname);

    void seatingPlayers();

    void hideLobby();

    void gameStarted();

    static LobbyTransitionSink noop() {
        return new LobbyTransitionSink() {
            @Override
            public void removeParticipant(String nickname) {
            }

            @Override
            public void seatingPlayers() {
            }

            @Override
            public void hideLobby() {
            }

            @Override
            public void gameStarted() {
            }
        };
    }
}
