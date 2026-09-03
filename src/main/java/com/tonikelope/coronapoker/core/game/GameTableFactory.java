/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import com.tonikelope.coronapoker.table.TableSession;

/** Creates the single canonical controller/table session for either frontend. */
@FunctionalInterface
public interface GameTableFactory {

    TableSession create(GameLaunchContext context) throws Exception;

    static GameTableFactory unavailable() {
        return context -> {
            throw new IllegalStateException("Canonical game controller is not installed");
        };
    }
}
