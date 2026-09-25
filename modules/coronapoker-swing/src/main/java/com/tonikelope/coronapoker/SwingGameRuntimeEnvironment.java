/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.game.GameRuntimeEnvironment;
import java.nio.file.Path;

/** Live adapter for the classic process environment. */
final class SwingGameRuntimeEnvironment implements GameRuntimeEnvironment {

    @Override
    public Path dataDirectory() {
        return Path.of(Init.CORONA_DIR);
    }

    @Override
    public boolean developmentMode() {
        return Init.DEV_MODE;
    }

    @Override
    public boolean modActive() {
        return Init.MOD != null;
    }
}
