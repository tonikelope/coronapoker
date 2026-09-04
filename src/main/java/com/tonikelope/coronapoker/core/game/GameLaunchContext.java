/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import com.tonikelope.coronapoker.core.LobbySnapshot;
import java.util.Objects;

/** Immutable ownership handoff from the authenticated lobby to the game. */
public record GameLaunchContext(LobbySnapshot lobby, GameChannel channel,
        GameIdentity identity) {

    public GameLaunchContext {
        Objects.requireNonNull(lobby, "lobby");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(identity, "identity");
        if (!lobby.startingOrStarted()) {
            throw new IllegalArgumentException("Game handoff requires a starting lobby");
        }
    }
}
