package com.tonikelope.coronapoker;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UnlockHandBoundaryGateTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        Crupier.SECURITY_LOCKDOWN = false;
    }

    @Test
    void nextHandUnlockWaitsForTheLocalHandTransition() throws Exception {
        Crupier crupier = new Crupier();
        setHand(crupier, 41);

        Future<Crupier.UnlockWaitResult> result = executor.submit(() ->
                crupier.awaitStreetForUnlockPhase(
                        Crupier.UNLOCK_PHASE_POCKET, 42, TimeUnit.SECONDS.toMillis(2)));

        assertThrows(TimeoutException.class,
                () -> result.get(150, TimeUnit.MILLISECONDS),
                "an honest host may start hand N+1 before the client finishes hand N");

        setHand(crupier, 42);
        assertEquals(Crupier.UnlockWaitResult.READY,
                result.get(1, TimeUnit.SECONDS));
    }

    @Test
    void alreadyClosedHandIsRejectedWithoutWaiting() throws Exception {
        Crupier crupier = new Crupier();
        setHand(crupier, 42);

        assertEquals(Crupier.UnlockWaitResult.STALE_HAND,
                crupier.awaitStreetForUnlockPhase(
                        Crupier.UNLOCK_PHASE_POCKET, 41, TimeUnit.SECONDS.toMillis(2)));
    }

    @Test
    void hostCannotJumpMoreThanOneHandAhead() throws Exception {
        Crupier crupier = new Crupier();
        setHand(crupier, 42);

        assertEquals(Crupier.UnlockWaitResult.INVALID_HAND,
                crupier.awaitStreetForUnlockPhase(
                        Crupier.UNLOCK_PHASE_POCKET, 44, TimeUnit.SECONDS.toMillis(2)));
    }

    @Test
    void tableTerminationCancelsAnInFlightUnlockWaitImmediately()
            throws Exception {
        Crupier crupier = new Crupier();
        setHand(crupier, 41);

        Future<Crupier.UnlockWaitResult> result = executor.submit(() ->
                crupier.awaitStreetForUnlockPhase(
                        Crupier.UNLOCK_PHASE_POCKET, 42,
                        TimeUnit.MINUTES.toMillis(1)));
        assertThrows(TimeoutException.class,
                () -> result.get(150, TimeUnit.MILLISECONDS));

        crupier.setTerminationPending();

        assertEquals(Crupier.UnlockWaitResult.STALE_HAND,
                result.get(1, TimeUnit.SECONDS));
    }

    private static void setHand(Crupier crupier, int hand) throws Exception {
        Method method = Crupier.class.getDeclaredMethod("setContaManoLocal", int.class);
        method.setAccessible(true);
        method.invoke(crupier, hand);
    }
}
