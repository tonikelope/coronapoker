/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Consumer;

/** Atomic current-protocol conversion of ordered SQLite action rows. */
final class RecoveredActionSqlReader {

    private RecoveredActionSqlReader() {
    }

    static String readAll(ResultSet rows) throws SQLException {
        return readAll(rows, ignored -> {
        });
    }

    static String readAll(ResultSet rows,
            Consumer<RuntimeException> corruptRowObserver) throws SQLException {
        if (rows == null) {
            throw new IllegalArgumentException("recovery action rows are required");
        }
        if (corruptRowObserver == null) {
            throw new IllegalArgumentException("corrupt-row observer is required");
        }
        StringBuilder actions = new StringBuilder();
        while (rows.next()) {
            try {
                actions.append(RecoveredActionCodec.encodeV1(
                        rows.getString("player"), rows.getInt("action"),
                        rows.getDouble("bet"), rows.getString("record_b64"),
                        rows.getString("sig_b64"))).append('@');
            } catch (RuntimeException corruptRow) {
                // Keep the row's exact position. The receiving side decodes the
                // complete batch before publishing any replay action, so this
                // marker rejects the batch atomically instead of dropping a
                // state-changing row and continuing on a divergent chain.
                corruptRowObserver.accept(corruptRow);
                actions.append("INVALID_RECOVERY_ACTION@");
            }
        }
        return actions.toString();
    }
}
