package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class RemoteRebuyBarrierTest {

    @Test
    void waitsForEveryPreviouslyReceivedRelayEvenWhenTasksFinishOutOfOrder() throws Exception {
        RemoteRebuyBarrier barrier = new RemoteRebuyBarrier();
        barrier.register(41L);
        barrier.register(42L);

        CompletableFuture<Boolean> waiting = CompletableFuture.supplyAsync(
                () -> barrier.awaitThrough(42L, () -> false));

        barrier.complete(42L);
        assertThrows(TimeoutException.class, () -> waiting.get(100, TimeUnit.MILLISECONDS),
                "later completion must not overtake an earlier pending relay");

        barrier.complete(41L);
        assertTrue(waiting.get(1, TimeUnit.SECONDS));
    }

    @Test
    void cutoffDoesNotWaitForRelaysReceivedAfterTheBoundary() {
        RemoteRebuyBarrier barrier = new RemoteRebuyBarrier();
        barrier.register(7L);
        barrier.register(8L);
        barrier.complete(7L);

        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> assertTrue(barrier.awaitThrough(7L, () -> false)));
        assertTrue(barrier.hasPendingThrough(8L));
    }

    @Test
    void cancellationWakesAnIndefiniteBarrierWithoutPretendingSuccess() throws Exception {
        RemoteRebuyBarrier barrier = new RemoteRebuyBarrier();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        barrier.register(3L);

        CompletableFuture<Boolean> waiting = CompletableFuture.supplyAsync(
                () -> barrier.awaitThrough(3L, cancelled::get));
        cancelled.set(true);
        barrier.signalStateChange();

        assertFalse(waiting.get(1, TimeUnit.SECONDS));
    }
}
