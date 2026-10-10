/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class InboundTrafficGuardTest {

    @Test void frameAndByteBudgetsResetWithoutLeakingAcrossWindows() {
        InboundTrafficGuard guard = new InboundTrafficGuard(3, 10L,
                1_000L, 2, 2, 5_000L);
        assertEquals(InboundTrafficGuard.FrameDecision.ALLOW,
                guard.frameDecision(4L, 1L));
        assertEquals(InboundTrafficGuard.FrameDecision.ALLOW,
                guard.frameDecision(4L, 2L));
        assertEquals(InboundTrafficGuard.FrameDecision.DROP,
                guard.frameDecision(4L, 3L));
        assertEquals(InboundTrafficGuard.FrameDecision.ALLOW,
                guard.frameDecision(10L, 1_001L));
        assertEquals(InboundTrafficGuard.FrameDecision.DROP,
                guard.frameDecision(1L, 1_002L));
    }

    @Test void onlySustainedImpossibleTrafficDisconnectsAuthenticatedPeer() {
        InboundTrafficGuard guard = new InboundTrafficGuard(2, 100L,
                1_000L, 2, 2, 5_000L);
        assertEquals(InboundTrafficGuard.FrameDecision.ALLOW,
                guard.frameDecision(1L, 1L));
        assertEquals(InboundTrafficGuard.FrameDecision.ALLOW,
                guard.frameDecision(1L, 2L));
        assertEquals(InboundTrafficGuard.FrameDecision.DROP,
                guard.frameDecision(1L, 3L));

        assertEquals(InboundTrafficGuard.FrameDecision.ALLOW,
                guard.frameDecision(1L, 1_001L));
        assertEquals(InboundTrafficGuard.FrameDecision.ALLOW,
                guard.frameDecision(1L, 1_002L));
        assertEquals(InboundTrafficGuard.FrameDecision.DROP,
                guard.frameDecision(1L, 1_003L));

        assertEquals(InboundTrafficGuard.FrameDecision.ALLOW,
                guard.frameDecision(1L, 2_001L));
        assertEquals(InboundTrafficGuard.FrameDecision.ALLOW,
                guard.frameDecision(1L, 2_002L));
        assertEquals(InboundTrafficGuard.FrameDecision.DISCONNECT,
                guard.frameDecision(1L, 2_003L));
    }

    @Test void anIsolatedBurstIsForgottenBeforeAnotherBurst() {
        InboundTrafficGuard guard = new InboundTrafficGuard(1, 100L,
                1_000L, 2, 2, 5_000L);
        assertEquals(InboundTrafficGuard.FrameDecision.ALLOW,
                guard.frameDecision(1L, 1L));
        assertEquals(InboundTrafficGuard.FrameDecision.DROP,
                guard.frameDecision(1L, 2L));
        assertEquals(InboundTrafficGuard.FrameDecision.ALLOW,
                guard.frameDecision(1L, 3_500L));
        assertEquals(InboundTrafficGuard.FrameDecision.DROP,
                guard.frameDecision(1L, 3_501L));
    }

    @Test void featureBudgetsBoundChatAndBinaryTaskFloods() {
        InboundTrafficGuard guard = new InboundTrafficGuard(10, 100L,
                1_000L, 2, 2, 5_000L);
        assertTrue(guard.allowChat(1L));
        assertTrue(guard.allowChat(2L));
        assertFalse(guard.allowChat(3L));
        assertTrue(guard.allowBinaryOperation(3L));
        assertTrue(guard.allowBinaryOperation(4L));
        assertFalse(guard.allowBinaryOperation(5L));
        assertTrue(guard.allowChat(5_001L));
        assertTrue(guard.allowBinaryOperation(5_001L));
    }
}
