/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import com.tonikelope.coronapoker.core.LobbySnapshot;
import java.nio.file.Path;
import java.util.Objects;

/** Immutable ownership handoff from the authenticated lobby to the game. */
public record GameLaunchContext(LobbySnapshot lobby, GameChannel channel,
        GameIdentity identity, String sessionId, Path dataDirectory,
        String tablePassword, GameConfigCodecV1.Configuration initialConfiguration,
        int recoveryGameId) {

    public GameLaunchContext(LobbySnapshot lobby, GameChannel channel,
            GameIdentity identity) {
        this(lobby, channel, identity, GameSessionIds.random(), Path.of("."),
                null, null, -1);
    }

    public GameLaunchContext(LobbySnapshot lobby, GameChannel channel,
            GameIdentity identity, String sessionId) {
        this(lobby, channel, identity, sessionId, Path.of("."), null, null, -1);
    }

    public GameLaunchContext(LobbySnapshot lobby, GameChannel channel,
            GameIdentity identity, String sessionId, Path dataDirectory) {
        this(lobby, channel, identity, sessionId, dataDirectory, null, null, -1);
    }

    public GameLaunchContext(LobbySnapshot lobby, GameChannel channel,
            GameIdentity identity, String sessionId, Path dataDirectory,
            String tablePassword) {
        this(lobby, channel, identity, sessionId, dataDirectory,
                tablePassword, null, -1);
    }

    public GameLaunchContext {
        Objects.requireNonNull(lobby, "lobby");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(identity, "identity");
        sessionId = Objects.requireNonNull(sessionId, "sessionId").trim();
        if (sessionId.isEmpty()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory")
                .toAbsolutePath().normalize();
        tablePassword = tablePassword == null || tablePassword.isBlank()
                ? null : tablePassword;
        if (recoveryGameId == 0 || recoveryGameId < -1) {
            throw new IllegalArgumentException("recoveryGameId must be positive or -1");
        }
        if (initialConfiguration != null) {
            GameConfigCodecV1.requireValid(initialConfiguration);
        }
        if (!lobby.startingOrStarted()) {
            throw new IllegalArgumentException("Game handoff requires a starting lobby");
        }
    }
}
