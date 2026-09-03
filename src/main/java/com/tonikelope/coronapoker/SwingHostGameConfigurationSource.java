/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.game.GameConfigCodecV1;
import com.tonikelope.coronapoker.core.game.HostGameConfigurationSource;

/** Reads the classic host settings once and returns their strict neutral form. */
final class SwingHostGameConfigurationSource implements HostGameConfigurationSource {
    @Override
    public GameConfigCodecV1.Configuration create(String sessionId) {
        GameFrame.UGI = sessionId;
        GameConfigWireV1.Result result = GameConfigWireV1.fromGlobals();
        if (!result.isOk()) {
            throw new IllegalArgumentException(result.error());
        }
        return result.value().toCoreConfiguration();
    }
}
