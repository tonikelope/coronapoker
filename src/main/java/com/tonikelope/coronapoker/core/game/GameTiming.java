/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

/** Protocol and engine timing constants shared by both frontends. */
public final class GameTiming {

    public static final int QUEUE_POLL_MILLIS = 250;
    public static final int CLIENT_RECEPTION_TIMEOUT_MILLIS = 10_000;
    public static final int CONFIRMATION_TIMEOUT_MILLIS = 10_000;
    public static final int REBUY_TIMEOUT_MILLIS = 25_000;

    private GameTiming() {
    }
}
