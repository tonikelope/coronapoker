package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class BettingTurnCancellationTest {

    @Test
    public void everyTableWaitObservesAllThreeCancellationSignals() {
        assertFalse(Crupier.shouldCancelTableWait(false, false, false));
        assertTrue(Crupier.shouldCancelTableWait(true, false, false));
        assertTrue(Crupier.shouldCancelTableWait(false, true, false));
        assertTrue(Crupier.shouldCancelTableWait(false, false, true));
    }

    @Test
    public void forceRecoverStopsAPlayerTurnWait() throws Exception {
        Object turnLock = new Object();
        AtomicBoolean hasTurn = new AtomicBoolean(true);
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicBoolean terminationPending = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(1);

        FutureTask<Boolean> wait = new FutureTask<>(() -> {
            started.countDown();
            return Crupier.awaitPlayerTurnCompletion(turnLock, hasTurn::get,
                    finished::get, terminationPending::get, 10);
        });
        Thread dealer = new Thread(wait, "force-recover-betting-wait-test");
        dealer.start();
        assertTrue(started.await(1, TimeUnit.SECONDS));

        terminationPending.set(true);
        synchronized (turnLock) {
            turnLock.notifyAll();
        }

        assertFalse(wait.get(1, TimeUnit.SECONDS),
                "force_recover must cancel the wait even while the player still owns the turn");
    }

    @Test
    public void executorInterruptStopsAPlayerTurnWait() throws Exception {
        Object turnLock = new Object();
        AtomicBoolean hasTurn = new AtomicBoolean(true);
        CountDownLatch started = new CountDownLatch(1);

        FutureTask<Boolean> wait = new FutureTask<>(() -> {
            started.countDown();
            return Crupier.awaitPlayerTurnCompletion(turnLock, hasTurn::get,
                    () -> false, () -> false, 10_000);
        });
        Thread dealer = new Thread(wait, "shutdown-betting-wait-test");
        dealer.start();
        assertTrue(started.await(1, TimeUnit.SECONDS));

        dealer.interrupt();

        assertFalse(wait.get(1, TimeUnit.SECONDS),
                "shutdownNow must not leave the dealer parked on a player turn");
        assertTrue(dealer.isInterrupted(), "the cancellation signal must be preserved");
    }
}
