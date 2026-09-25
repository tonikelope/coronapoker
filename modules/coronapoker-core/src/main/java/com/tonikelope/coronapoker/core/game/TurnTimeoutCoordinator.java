/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/** Pause-aware, generation-safe countdown for native local-player turns. */
public final class TurnTimeoutCoordinator {

    private static final long MAX_POLL_MILLIS = 50L;

    private TurnTimeoutCoordinator() {
    }

    public static void run(long timeoutNanos, long hurryNanos,
            BooleanSupplier activeTurn, BooleanSupplier paused,
            Runnable awaitResume, LongSupplier nanoTime,
            LongConsumer pauseMillis, Runnable hurryAction,
            Runnable timeoutAction) {
        run(timeoutNanos, hurryNanos, activeTurn, paused, awaitResume,
                nanoTime, pauseMillis, hurryAction, timeoutAction, () -> { });
    }

    public static void run(long timeoutNanos, long hurryNanos,
            BooleanSupplier activeTurn, BooleanSupplier paused,
            Runnable awaitResume, LongSupplier nanoTime,
            LongConsumer pauseMillis, Runnable hurryAction,
            Runnable timeoutAction, Runnable finishAction) {
        Objects.requireNonNull(activeTurn, "activeTurn");
        Objects.requireNonNull(paused, "paused");
        Objects.requireNonNull(awaitResume, "awaitResume");
        Objects.requireNonNull(nanoTime, "nanoTime");
        Objects.requireNonNull(pauseMillis, "pauseMillis");
        Objects.requireNonNull(hurryAction, "hurryAction");
        Objects.requireNonNull(timeoutAction, "timeoutAction");
        Objects.requireNonNull(finishAction, "finishAction");
        try {
            if (timeoutNanos <= 0L) {
                if (activeTurn.getAsBoolean()) timeoutAction.run();
                return;
            }

            long remaining = timeoutNanos;
            long previous = nanoTime.getAsLong();
            boolean hurryTriggered = false;
            while (remaining > 0L && activeTurn.getAsBoolean()) {
                if (paused.getAsBoolean()) {
                    awaitResume.run();
                    previous = nanoTime.getAsLong();
                    continue;
                }
                long now = nanoTime.getAsLong();
                remaining = Math.max(0L, remaining
                        - Math.max(0L, now - previous));
                previous = now;
                if (!hurryTriggered && remaining <= hurryNanos) {
                    hurryTriggered = true;
                    hurryAction.run();
                }
                if (remaining > 0L) {
                    pauseMillis.accept(Math.min(MAX_POLL_MILLIS,
                            Math.max(1L, remaining / 1_000_000L)));
                }
            }
            if (activeTurn.getAsBoolean()) timeoutAction.run();
        } finally {
            finishAction.run();
        }
    }
}
