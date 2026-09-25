/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/** Supplies the validated initial table configuration owned by the host. */
@FunctionalInterface
public interface HostGameConfigurationSource {

    GameConfigCodecV1.Configuration create(String sessionId);

    static HostGameConfigurationSource unavailable() {
        return sessionId -> {
            throw new IllegalStateException("Host game configuration is not installed");
        };
    }
}
