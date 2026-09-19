package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TurnTimeoutCoordinatorTest {

    @Test
    void expiresOnceAndEmitsOneHurryWarning() {
        AtomicLong clock = new AtomicLong();
        AtomicInteger hurry = new AtomicInteger();
        AtomicInteger timeout = new AtomicInteger();

        TurnTimeoutCoordinator.run(200_000_000L, 50_000_000L,
                () -> true, () -> false, () -> { }, clock::get,
                millis -> clock.addAndGet(millis * 1_000_000L),
                hurry::incrementAndGet, timeout::incrementAndGet);

        assertEquals(1, hurry.get());
        assertEquals(1, timeout.get());
        assertTrue(clock.get() >= 200_000_000L);
    }

    @Test
    void timeSpentPausedDoesNotConsumeTheDeadline() {
        AtomicLong clock = new AtomicLong();
        AtomicBoolean paused = new AtomicBoolean(true);
        AtomicInteger resumeWaits = new AtomicInteger();
        AtomicInteger timeout = new AtomicInteger();

        TurnTimeoutCoordinator.run(200_000_000L, 50_000_000L,
                () -> true, paused::get,
                () -> {
                    resumeWaits.incrementAndGet();
                    clock.addAndGet(10_000_000_000L);
                    paused.set(false);
                }, clock::get,
                millis -> clock.addAndGet(millis * 1_000_000L),
                () -> { }, timeout::incrementAndGet);

        assertEquals(1, resumeWaits.get());
        assertEquals(1, timeout.get());
        assertTrue(clock.get() >= 10_200_000_000L);
    }

    @Test
    void staleTurnCancelsWithoutWarningOrDecision() {
        AtomicLong clock = new AtomicLong();
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicBoolean warned = new AtomicBoolean();
        AtomicBoolean timedOut = new AtomicBoolean();

        TurnTimeoutCoordinator.run(200_000_000L, 50_000_000L,
                active::get, () -> false, () -> { }, clock::get,
                millis -> {
                    clock.addAndGet(millis * 1_000_000L);
                    active.set(false);
                }, () -> warned.set(true), () -> timedOut.set(true));

        assertFalse(warned.get());
        assertFalse(timedOut.get());
    }

    @Test
    void manualDecisionAfterHurryAlwaysRunsAudioCleanup() {
        AtomicLong clock = new AtomicLong();
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicBoolean warned = new AtomicBoolean();
        AtomicBoolean timedOut = new AtomicBoolean();
        AtomicInteger cleanup = new AtomicInteger();

        TurnTimeoutCoordinator.run(200_000_000L, 100_000_000L,
                active::get, () -> false, () -> { }, clock::get,
                millis -> {
                    clock.addAndGet(millis * 1_000_000L);
                    if (warned.get()) active.set(false);
                }, () -> warned.set(true), () -> timedOut.set(true),
                cleanup::incrementAndGet);

        assertTrue(warned.get());
        assertFalse(timedOut.get());
        assertEquals(1, cleanup.get());
    }
}
