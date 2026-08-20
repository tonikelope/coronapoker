package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class TableTeardownSessionHandoffTest {

    @Test
    public void menuAndRecoverStopsReleaseGuardBeforeNextSession() throws Exception {
        verifyHandoff(false);
        verifyHandoff(true);
    }

    private static void verifyHandoff(boolean recover) throws Exception {
        SessionGuard guard = new SessionGuard();
        SessionGuard.Generation oldSession = guard.beginSession();
        ThreadPoolExecutor workers = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        CountDownLatch bettingRound = new CountDownLatch(1);
        Object turnLock = new Object();
        AtomicBoolean playerStillHasTurn = new AtomicBoolean(true);
        AtomicBoolean transmissionFinished = new AtomicBoolean();
        AtomicBoolean terminationPending = new AtomicBoolean();
        AtomicBoolean advancedAfterStop = new AtomicBoolean();

        workers.submit(() -> guard.runIfCurrent(oldSession, () -> {
            bettingRound.countDown();
            Crupier.awaitPlayerTurnCompletion(turnLock, playerStillHasTurn::get,
                    transmissionFinished::get, terminationPending::get, 10_000);
            advancedAfterStop.set(Crupier.shouldAdvanceBettingStreet(
                    transmissionFinished.get(), terminationPending.get(),
                    3, Crupier.PREFLOP, 3));
        }));

        assertTrue(bettingRound.await(1, TimeUnit.SECONDS));
        terminationPending.set(true);
        workers.shutdownNow();
        assertTrue(workers.awaitTermination(1, TimeUnit.SECONDS),
                "Old dealer must release the session guard for recover=" + recover);
        assertFalse(advancedAfterStop.get(),
                "Old dealer advanced state after stop for recover=" + recover);

        guard.invalidate(oldSession);
        SessionGuard.Generation newSession = guard.beginSession();
        AtomicInteger newWrites = new AtomicInteger();
        assertTrue(guard.runIfCurrent(newSession, newWrites::incrementAndGet));
        assertEquals(1, newWrites.get());
    }
}
