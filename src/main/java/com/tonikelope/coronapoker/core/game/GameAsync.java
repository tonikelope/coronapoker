/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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

    /**
     * Creates a scheduler whose tasks belong to one table session. Closing the
     * scheduler interrupts every outstanding task and prevents work from a
     * finished table leaking into a later one.
     */
    static OwnedAsync owned(String threadPrefix) {
        return new OwnedAsync(threadPrefix);
    }

    private static void cancellablePause(long millis) {
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

    private static void cancellablePark(long millis) {
        long nanos = millis * 1_000_000L;
        if (nanos > 0L) {
            long end = System.nanoTime() + nanos;
            while (System.nanoTime() < end
                    && !Thread.currentThread().isInterrupted()) {
                LockSupport.parkNanos(end - System.nanoTime());
            }
        }
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
            cancellablePause(millis);
        }

        @Override
        public void park(long millis) {
            cancellablePark(millis);
        }
    }

    final class OwnedAsync implements GameAsync, AutoCloseable {
        private static final long CLOSE_AWAIT_SECONDS = 5L;
        private static final AtomicLong OWNER_SEQUENCE = new AtomicLong();

        private final AtomicBoolean closed = new AtomicBoolean();
        private final ExecutorService executor;
        private final java.util.Set<Thread> workerThreads
                = java.util.concurrent.ConcurrentHashMap.newKeySet();

        private OwnedAsync(String threadPrefix) {
            String prefix = java.util.Objects.requireNonNull(
                    threadPrefix, "threadPrefix").strip();
            if (prefix.isEmpty()) {
                throw new IllegalArgumentException("threadPrefix is blank");
            }
            long owner = OWNER_SEQUENCE.incrementAndGet();
            AtomicLong taskSequence = new AtomicLong();
            executor = Executors.newCachedThreadPool(task -> {
                Thread thread = new Thread(task, prefix + "-" + owner + "-"
                        + taskSequence.incrementAndGet());
                thread.setDaemon(true);
                workerThreads.add(thread);
                return thread;
            });
        }

        @Override
        public Future<?> execute(Runnable task) {
            java.util.Objects.requireNonNull(task, "task");
            return submit(java.util.concurrent.Executors.callable(task, null));
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            java.util.Objects.requireNonNull(task, "task");
            if (closed.get()) {
                return cancelledFuture();
            }
            try {
                return executor.submit(task);
            } catch (RejectedExecutionException closedDuringSubmit) {
                return cancelledFuture();
            }
        }

        private static <T> Future<T> cancelledFuture() {
            FutureTask<T> cancelled = new FutureTask<>(() -> null);
            cancelled.cancel(false);
            return cancelled;
        }

        @Override
        public void pause(long millis) {
            cancellablePause(millis);
        }

        @Override
        public void park(long millis) {
            cancellablePark(millis);
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                executor.shutdownNow();
                // TableSession.close() is the ownership boundary before its
                // DatabaseService can be closed.  Merely interrupting here let
                // an already-running crypto verification finish afterwards and
                // attempt to persist into the closed database.  Wait for owned
                // work when close comes from outside this executor.  A worker is
                // never allowed to wait for its own termination.
                if (!workerThreads.contains(Thread.currentThread())) {
                    boolean interrupted = false;
                    try {
                        executor.awaitTermination(CLOSE_AWAIT_SECONDS,
                                TimeUnit.SECONDS);
                    } catch (InterruptedException cancellation) {
                        interrupted = true;
                    } finally {
                        if (interrupted) Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }
}
