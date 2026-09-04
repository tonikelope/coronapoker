/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.game.GameAsync;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/** Exact adapter for the classic per-table executor and timing helpers. */
final class SwingGameAsync implements GameAsync {

    @Override
    public Future<?> execute(Runnable task) {
        return Helpers.threadRun(task);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return Helpers.futureRun(task);
    }

    @Override
    public void pause(long millis) {
        Helpers.pausar(millis);
    }

    @Override
    public void park(long millis) {
        Helpers.parkThreadMillis(millis);
    }
}
