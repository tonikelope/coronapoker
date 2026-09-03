/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.game.GameDatabase;
import java.sql.Connection;

/** Classic adapter preserving the established SQLite lock and connection. */
final class SwingGameDatabase implements GameDatabase {
    @Override public Object lock() { return GameFrame.SQL_LOCK; }
    @Override public Connection connection() throws java.sql.SQLException {
        return Helpers.getSQLITE();
    }
}
