package com.tonikelope.coronapoker.e2e;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

final class RealGameProgressWatchdogTest {

    @Test
    void failsImmediatelyWhenTableEndsBeforeRequestedHands() {
        String failure = RealGameNodeMain.progressFailure(
                12, 20, true, 13, false, true,
                0L, Duration.ofSeconds(120).toNanos());

        assertTrue(failure.startsWith("premature table end: completed=12 requested=20"));
    }

    @Test
    void failsAcceleratedRunAfterBoundedLackOfCompletedHandProgress() {
        String failure = RealGameNodeMain.progressFailure(
                12, 20, false, 13, false, true,
                Duration.ofSeconds(120).toNanos(), Duration.ofSeconds(120).toNanos());

        assertTrue(failure.startsWith("accelerated game made no completed-hand progress"));
    }

    @Test
    void doesNotApplyShortStallLimitToProductionTiming() {
        assertNull(RealGameNodeMain.progressFailure(
                12, 20, false, 13, false, false,
                Duration.ofMinutes(10).toNanos(), Duration.ofSeconds(120).toNanos()));
    }

    @Test
    void permitsExpectedForceRecoverTableReplacementButKeepsStallBound() {
        assertNull(RealGameNodeMain.progressFailure(
                0, 3, true, 1, true, true,
                Duration.ofSeconds(1).toNanos(), Duration.ofSeconds(120).toNanos()));

        String failure = RealGameNodeMain.progressFailure(
                0, 3, true, 1, true, true,
                Duration.ofSeconds(120).toNanos(), Duration.ofSeconds(120).toNanos());
        assertTrue(failure.startsWith("accelerated game made no completed-hand progress"));
    }
}
