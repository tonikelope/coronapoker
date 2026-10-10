/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.tonikelope.coronapoker.core.NetworkBlock;
import org.junit.jupiter.api.Test;

final class ConnectionAdmissionGuardTest {

    @Test void boundsSlowHandshakesGloballyAndPerAddress() {
        ConnectionAdmissionGuard guard = guard();
        var first = guard.acquire("10.0.0.1", 1L);
        var second = guard.acquire("10.0.0.1", 1L);
        var perAddress = guard.acquire("10.0.0.1", 1L);
        var third = guard.acquire("10.0.0.2", 1L);
        var global = guard.acquire("10.0.0.3", 1L);

        assertTrue(first.admitted());
        assertTrue(second.admitted());
        assertEquals(ConnectionAdmissionGuard.Denial.ADDRESS_CAPACITY,
                perAddress.denial());
        assertTrue(third.admitted());
        assertEquals(ConnectionAdmissionGuard.Denial.GLOBAL_CAPACITY,
                global.denial());
        assertEquals(3, guard.globalPending());

        guard.release(first, 2L);
        guard.release(second, 2L);
        guard.release(third, 2L);
        assertEquals(0, guard.globalPending());
    }

    @Test void repeatedRejectedJoinsCauseARecoverableTemporaryIpBan() {
        ConnectionAdmissionGuard guard = guard();
        long now = 1_000L;
        for (int attempt = 0; attempt < 3; attempt++) {
            var admission = guard.acquire("203.0.113.9", now + attempt);
            assertTrue(admission.admitted());
            guard.rejected(admission, now + attempt);
            guard.release(admission, now + attempt);
        }

        assertTrue(guard.banned("203.0.113.9", now + 3));
        assertEquals(ConnectionAdmissionGuard.Denial.BANNED,
                guard.acquire("203.0.113.9", now + 3).denial());
        assertFalse(guard.banned("203.0.113.9", now + 10_003L));
        assertTrue(guard.acquire("203.0.113.9", now + 10_003L)
                .admitted());
    }

    @Test void aSuccessfulAuthenticatedJoinClearsPriorRejections() {
        ConnectionAdmissionGuard guard = guard();
        var rejected = guard.acquire("198.51.100.7", 1L);
        guard.rejected(rejected, 1L);
        guard.release(rejected, 1L);
        var accepted = guard.acquire("198.51.100.7", 2L);
        guard.accepted(accepted, 2L);
        guard.release(accepted, 2L);

        for (int attempt = 0; attempt < 2; attempt++) {
            var admission = guard.acquire("198.51.100.7", 3L + attempt);
            guard.rejected(admission, 3L + attempt);
            guard.release(admission, 3L + attempt);
        }
        assertFalse(guard.banned("198.51.100.7", 6L));
    }

    @Test void capacityRefusalsNeverTurnSharedNatClientsIntoAnIpBan() {
        ConnectionAdmissionGuard guard = guard();
        var first = guard.acquire("198.51.100.80", 1L);
        var second = guard.acquire("198.51.100.80", 1L);

        for (int attempt = 0; attempt < 20; attempt++) {
            assertEquals(ConnectionAdmissionGuard.Denial.ADDRESS_CAPACITY,
                    guard.acquire("198.51.100.80", 2L + attempt).denial());
        }

        assertFalse(guard.banned("198.51.100.80", 30L));
        assertTrue(guard.activeBlocks(30L).isEmpty());
        guard.release(first, 31L);
        guard.release(second, 31L);
        assertTrue(guard.acquire("198.51.100.80", 32L).admitted());
    }

    @Test void activeBlocksCanBeInspectedAndRemovedByTheHost() {
        ConnectionAdmissionGuard guard = guard();
        long now = 5_000L;
        for (int attempt = 0; attempt < 3; attempt++) {
            var admission = guard.acquire("192.0.2.45", now + attempt);
            guard.rejected(admission, now + attempt);
            guard.release(admission, now + attempt);
        }

        var blocks = guard.activeBlocks(now + 3L);
        assertEquals(1, blocks.size());
        assertEquals("192.0.2.45", blocks.get(0).address());
        assertEquals(NetworkBlock.Reason.CONNECTION_ABUSE,
                blocks.get(0).reason());
        assertTrue(blocks.get(0).remainingMillis(now + 3L) > 0L);

        assertTrue(guard.unblock("192.0.2.45", now + 4L));
        assertTrue(guard.activeBlocks(now + 4L).isEmpty());
        assertFalse(guard.unblock("192.0.2.45", now + 5L));
        assertTrue(guard.acquire("192.0.2.45", now + 5L).admitted());
    }

    private static ConnectionAdmissionGuard guard() {
        return new ConnectionAdmissionGuard(3, 2, 3, 1_000L,
                10_000L, 20_000L);
    }
}
