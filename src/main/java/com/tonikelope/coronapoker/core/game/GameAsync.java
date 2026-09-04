/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.LockSupport;

/** Frontend-owned scheduling and timing used by the canonical controller. */
public interface GameAsync {

    Future<?> execute(Runnable task);

    <T> Future<T> submit(Callable<T> task);

    void pause(long millis);

    void park(long millis);

    static GameAsync standalone() {
        return StandaloneAsync.INSTANCE;
    }

    final class StandaloneAsync implements GameAsync {
        private static final StandaloneAsync INSTANCE = new StandaloneAsync();

        private StandaloneAsync() {
        }

        @Override
        public Future<?> execute(Runnable task) {
            FutureTask<Void> future = new FutureTask<>(task, null);
            start(future);
            return future;
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            FutureTask<T> future = new FutureTask<>(task);
            start(future);
            return future;
        }

        private static void start(FutureTask<?> task) {
            Thread thread = new Thread(task, "CoronaPoker-game-task");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void pause(long millis) {
            if (Thread.currentThread().isInterrupted()) {
                throw new GameCancellationException();
            }
            try {
                Thread.sleep(Math.max(0L, millis));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new GameCancellationException(interrupted);
            }
        }

        @Override
        public void park(long millis) {
            long nanos = millis * 1_000_000L;
            if (nanos > 0L) {
                long end = System.nanoTime() + nanos;
                while (System.nanoTime() < end && !Thread.currentThread().isInterrupted()) {
                    LockSupport.parkNanos(end - System.nanoTime());
                }
            }
        }
    }
}
