/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.sql.Connection;
import java.sql.SQLException;

/** Shared SQLite access and lock used by the canonical game controller. */
public interface GameDatabase {

    Object lock();

    Connection connection() throws SQLException;

    static GameDatabase unavailable() {
        return Unavailable.INSTANCE;
    }

    final class Unavailable implements GameDatabase {
        private static final Unavailable INSTANCE = new Unavailable();
        private final Object lock = new Object();
        private Unavailable() { }
        @Override public Object lock() { return lock; }
        @Override public Connection connection() {
            throw new IllegalStateException("Game database is not installed");
        }
    }
}
