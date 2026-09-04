/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.nio.file.Path;

/** Frontend-owned process environment used by the canonical game controller. */
public interface GameRuntimeEnvironment {

    Path dataDirectory();

    boolean developmentMode();

    boolean modActive();

    static GameRuntimeEnvironment defaults() {
        return DefaultEnvironment.INSTANCE;
    }

    final class DefaultEnvironment implements GameRuntimeEnvironment {
        private static final DefaultEnvironment INSTANCE = new DefaultEnvironment();

        private DefaultEnvironment() {
        }

        @Override
        public Path dataDirectory() {
            return Path.of(".");
        }

        @Override
        public boolean developmentMode() {
            return false;
        }

        @Override
        public boolean modActive() {
            return false;
        }
    }
}
