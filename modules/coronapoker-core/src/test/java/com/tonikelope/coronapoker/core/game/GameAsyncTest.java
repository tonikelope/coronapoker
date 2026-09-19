/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class GameAsyncTest {

    @Test
    void ownedSchedulerExecutesSubmittedWork() throws Exception {
        try (GameAsync.OwnedAsync async = GameAsync.owned("test-game-task")) {
            assertEquals(42, async.submit(() -> 42).get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void closingOwnedSchedulerInterruptsWorkAndRejectsLaterTasks()
            throws Exception {
        GameAsync.OwnedAsync async = GameAsync.owned("test-game-task");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        Future<?> running = async.execute(() -> {
            started.countDown();
            try {
                Thread.sleep(Duration.ofMinutes(1).toMillis());
            } catch (InterruptedException expected) {
                Thread.currentThread().interrupt();
                interrupted.countDown();
            }
        });

        assertTrue(started.await(2, TimeUnit.SECONDS));
        async.close();

        assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        running.get(2, TimeUnit.SECONDS);
        assertTrue(running.isDone());
        assertTrue(async.execute(() -> {
            throw new AssertionError("work submitted after close must not run");
        }).isCancelled());
    }

    @Test
    void closingOwnedSchedulerWaitsForInterruptedTaskCleanup() throws Exception {
        GameAsync.OwnedAsync async = GameAsync.owned("test-game-task");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch cleaning = new CountDownLatch(1);
        CountDownLatch releaseCleanup = new CountDownLatch(1);
        AtomicBoolean cleanupFinished = new AtomicBoolean();
        async.execute(() -> {
            started.countDown();
            try {
                Thread.sleep(Duration.ofMinutes(1).toMillis());
            } catch (InterruptedException expected) {
                cleaning.countDown();
                try {
                    releaseCleanup.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException secondCancellation) {
                    Thread.currentThread().interrupt();
                }
                cleanupFinished.set(true);
            }
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));

        Thread closer = new Thread(async::close, "game-async-test-closer");
        closer.start();
        assertTrue(cleaning.await(2, TimeUnit.SECONDS));
        assertTrue(closer.isAlive(),
                "close must not return while owned cleanup is still running");
        releaseCleanup.countDown();
        closer.join(2_000L);

        assertTrue(cleanupFinished.get());
        assertFalse(closer.isAlive());
    }

    @Test
    void closingOwnedSchedulerFromItsWorkerDoesNotWaitForItself()
            throws Exception {
        GameAsync.OwnedAsync async = GameAsync.owned("test-game-task");
        Future<?> close = async.execute(async::close);

        close.get(2, TimeUnit.SECONDS);
        assertTrue(close.isDone());
    }
}
