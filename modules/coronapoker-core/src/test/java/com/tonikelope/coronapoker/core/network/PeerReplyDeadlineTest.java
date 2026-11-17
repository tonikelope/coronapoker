/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.network;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PeerReplyDeadlineTest {
    private static long ms(long value) { return TimeUnit.MILLISECONDS.toNanos(value); }

    @Test
    void aSilentPeerExhaustsInactivityGrace() {
        var deadline = new PeerReplyDeadline(0, 10_000, 180_000);
        assertTrue(deadline.remainingNanos(ms(9_999), 0) > 0);
        assertEquals(0L, deadline.remainingNanos(ms(10_000), 0));
    }

    @Test
    void ongoingReceptionExtendsIdleGraceButCannotDefeatTheHardCap() {
        var deadline = new PeerReplyDeadline(0, 10_000, 40_000);
        for (long now = 8_000; now < 40_000; now += 8_000) {
            assertTrue(deadline.remainingNanos(ms(now), ms(now)) > 0);
        }
        assertEquals(0L, deadline.remainingNanos(ms(40_000), ms(40_000)));
        assertTrue(deadline.remainingNanos(ms(40_001), ms(40_001)) < 0);
    }

    @Test
    void staleOrFutureActivityCannotExtendAWait() {
        var deadline = new PeerReplyDeadline(ms(10_000), 10_000, 60_000);
        assertEquals(0L, deadline.remainingNanos(ms(20_000), ms(9_000)));
        assertEquals(0L, deadline.remainingNanos(ms(20_000), ms(30_000)));
    }

    @Test
    void aLargeProofUsesFiniteTransferGraceWhileSmallCommandsKeepTheirIdleBudget() {
        assertEquals(10_000L, NetworkLobbyGateway.gameAcknowledgementTimeoutMillis(500, 10));
        assertEquals(24_000L, NetworkLobbyGateway.gameAcknowledgementTimeoutMillis(28_825, 6_000));
        assertEquals(180_000L, NetworkLobbyGateway.gameAcknowledgementTimeoutMillis(324_295, 6_000));
    }

    @Test
    void startupAndHighRttHaveGraceWhileFastLinksKeepTheirShortIdleBudget() {
        assertEquals(30_000L, PeerReplyDeadline.timeoutMillis(10_000, -1));
        assertEquals(10_000L, PeerReplyDeadline.timeoutMillis(10_000, 15));
        assertEquals(24_000L, PeerReplyDeadline.timeoutMillis(10_000, 6_000));
        assertEquals(60_000L, PeerReplyDeadline.timeoutMillis(10_000, Long.MAX_VALUE));
    }
}
