/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.nio.file.Path;
import java.util.Objects;

/** Frontend-owned process environment used by the canonical game controller. */
public interface GameRuntimeEnvironment {

    Path dataDirectory();

    boolean developmentMode();

    boolean modActive();

    static GameRuntimeEnvironment at(Path dataDirectory) {
        return at(dataDirectory, false);
    }

    static GameRuntimeEnvironment at(Path dataDirectory, boolean modActive) {
        return new ConfiguredEnvironment(Objects.requireNonNull(dataDirectory,
                "dataDirectory").toAbsolutePath().normalize(), false,
                modActive);
    }

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

    record ConfiguredEnvironment(Path dataDirectory, boolean developmentMode,
            boolean modActive) implements GameRuntimeEnvironment {
    }
}
