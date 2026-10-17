/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class InboundTrafficGuardTest {

    @Test void oneExcessiveWindowOnlyRecordsAWarningSignal() {
        InboundTrafficGuard guard = new InboundTrafficGuard(2, 10L,
                1_000L, 2, 5_000L);
        assertFalse(guard.observeFrame(4L, 1L));
        assertFalse(guard.observeFrame(4L, 2L));
        assertFalse(guard.observeFrame(4L, 3L));
        assertFalse(guard.observeFrame(1L, 4L));
    }

    @Test void onlyThreeConsecutiveExcessiveWindowsAlertTheHost() {
        InboundTrafficGuard guard = new InboundTrafficGuard(2, 100L,
                1_000L, 2, 5_000L);
        assertExcessiveWindowWithoutAlert(guard, 1L);
        assertExcessiveWindowWithoutAlert(guard, 1_001L);

        assertFalse(guard.observeFrame(1L, 2_001L));
        assertFalse(guard.observeFrame(1L, 2_002L));
        assertTrue(guard.observeFrame(1L, 2_003L));
        assertTrue(guard.observeFrame(1L, 2_004L),
                "the caller coalesces repeated samples into one decision");
    }

    @Test void anIdleGapForgetsAnIsolatedBurst() {
        InboundTrafficGuard guard = new InboundTrafficGuard(1, 100L,
                1_000L, 2, 5_000L);
        assertFalse(guard.observeFrame(1L, 1L));
        assertFalse(guard.observeFrame(1L, 2L));
        assertFalse(guard.observeFrame(1L, 3_500L));
        assertFalse(guard.observeFrame(1L, 3_501L));
    }

    @Test void voiceOperationsAreObservedButNeverRejected() {
        InboundTrafficGuard guard = new InboundTrafficGuard(10, 100L,
                1_000L, 2, 5_000L);
        assertFalse(guard.observeVoiceOperation(1L));
        assertFalse(guard.observeVoiceOperation(2L));
        assertTrue(guard.observeVoiceOperation(3L));
        assertTrue(guard.observeVoiceOperation(4L));
        assertFalse(guard.observeVoiceOperation(5_001L));
    }

    @Test void forgivenessResetsEveryObservationCounter() {
        InboundTrafficGuard guard = new InboundTrafficGuard(1, 100L,
                1_000L, 1, 5_000L);
        guard.observeFrame(1L, 1L);
        guard.observeFrame(1L, 2L);
        guard.observeVoiceOperation(1L);
        assertTrue(guard.observeVoiceOperation(2L));

        guard.reset();

        assertFalse(guard.observeFrame(1L, 3L));
        assertFalse(guard.observeVoiceOperation(3L));
    }

    private static void assertExcessiveWindowWithoutAlert(
            InboundTrafficGuard guard, long start) {
        assertFalse(guard.observeFrame(1L, start));
        assertFalse(guard.observeFrame(1L, start + 1L));
        assertFalse(guard.observeFrame(1L, start + 2L));
    }
}
