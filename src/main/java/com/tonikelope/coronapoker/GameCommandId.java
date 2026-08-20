/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.util.concurrent.atomic.AtomicInteger;

/** Process-wide monotonic IDs for the current GAME protocol. */
public final class GameCommandId {

    private static final AtomicInteger NEXT = new AtomicInteger((int) System.nanoTime());

    private GameCommandId() {
    }

    public static int next() {
        return NEXT.getAndIncrement();
    }
}
