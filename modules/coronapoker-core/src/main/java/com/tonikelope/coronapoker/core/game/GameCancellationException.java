/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/** Control-flow signal used when table work is cooperatively cancelled. */
public class GameCancellationException extends Error {

    private static final long serialVersionUID = 1L;

    public GameCancellationException() {
        super("cooperative cancellation", null, false, false);
    }

    public GameCancellationException(InterruptedException cause) {
        super("cooperative cancellation", cause, false, false);
    }
}
