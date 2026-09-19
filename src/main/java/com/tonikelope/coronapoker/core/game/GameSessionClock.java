/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.Objects;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/**
 * Renderer-neutral play-time clock. Swing used to own the only one-second
 * increment, which left a native GDX host unable to trigger minute-based blind
 * increases. This clock belongs to the session and observes its canonical
 * pause and lifecycle state.
 */
public final class GameSessionClock {

    static final long TICK_MILLIS = 1_000L;

    private GameSessionClock() {
    }

    public static Future<?> start(GameSession session, GameAsync async,
            BooleanSupplier tableOpen) {
        return start(session, async, tableOpen, ignored -> { });
    }

    public static Future<?> start(GameSession session, GameAsync async,
            BooleanSupplier tableOpen, LongConsumer afterTick) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(async, "async");
        Objects.requireNonNull(tableOpen, "tableOpen");
        Objects.requireNonNull(afterTick, "afterTick");
        return async.execute(() -> {
            while (tableOpen.getAsBoolean()
                    && session.phase() == GameSession.Phase.RUNNING) {
                try {
                    async.pause(TICK_MILLIS);
                } catch (GameCancellationException cancelled) {
                    return;
                }
                if (tick(session, tableOpen.getAsBoolean())) {
                    afterTick.accept(session.playTimeSeconds());
                }
            }
        });
    }

    static boolean tick(GameSession session, boolean tableOpen) {
        if (tableOpen && session.phase() == GameSession.Phase.RUNNING
                && !session.isPaused()) {
            session.incrementPlayTimeSecond();
            return true;
        }
        return false;
    }
}
