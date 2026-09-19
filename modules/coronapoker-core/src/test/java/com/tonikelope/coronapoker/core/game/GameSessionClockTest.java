package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class GameSessionClockTest {

    @Test
    void advancesOnlyWhileTheCanonicalSessionIsRunningAndUnpaused() {
        GameSession session = new GameSession("server", true);

        GameSessionClock.tick(session, true);
        assertEquals(0L, session.playTimeSeconds());

        session.start();
        GameSessionClock.tick(session, true);
        assertEquals(1L, session.playTimeSeconds());

        session.setPaused(true);
        GameSessionClock.tick(session, true);
        assertEquals(1L, session.playTimeSeconds());

        session.setPaused(false);
        GameSessionClock.tick(session, false);
        assertEquals(1L, session.playTimeSeconds());

        GameSessionClock.tick(session, true);
        assertEquals(2L, session.playTimeSeconds());

        session.finish();
        GameSessionClock.tick(session, true);
        assertEquals(2L, session.playTimeSeconds());
    }

    @Test
    void startedClockUsesFrontendSchedulerAndStopsWithTheTable() {
        GameSession session = new GameSession("server", true);
        session.start();
        AtomicBoolean tableOpen = new AtomicBoolean(true);
        AtomicInteger pauses = new AtomicInteger();
        AtomicInteger publishedSeconds = new AtomicInteger();
        GameAsync deterministicAsync = new GameAsync() {
            @Override
            public Future<?> execute(Runnable task) {
                task.run();
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public <T> Future<T> submit(Callable<T> task) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void pause(long millis) {
                assertEquals(GameSessionClock.TICK_MILLIS, millis);
                if (pauses.incrementAndGet() == 2) tableOpen.set(false);
            }

            @Override
            public void park(long millis) {
                throw new UnsupportedOperationException();
            }
        };

        GameSessionClock.start(session, deterministicAsync, tableOpen::get,
                seconds -> publishedSeconds.set(Math.toIntExact(seconds)));

        assertEquals(1L, session.playTimeSeconds());
        assertEquals(2, pauses.get());
        assertEquals(1, publishedSeconds.get());
    }
}
