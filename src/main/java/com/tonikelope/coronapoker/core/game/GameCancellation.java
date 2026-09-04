/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Shared cooperative-cancellation diagnostics. */
public final class GameCancellation {

    private GameCancellation() {
    }

    public static void log(Logger logger, String operation, Throwable failure) {
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        logger.log(Level.INFO, "{0} cancelled — {1} (cooperative cancellation)",
                new Object[]{operation, failure.getClass().getSimpleName()});
    }
}
