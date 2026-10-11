/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class AuthenticatedHotJoinAdmissionGuardTest {

    @Test void repeatedAdmissionsThrottleOnlyThePersistentIdentity() {
        AuthenticatedHotJoinAdmissionGuard guard = guard();
        for (int attempt = 0; attempt < 3; attempt++) {
            assertTrue(guard.acquire("identity-a", attempt).admitted());
        }

        var denied = guard.acquire("identity-a", 3L);
        assertEquals(
                AuthenticatedHotJoinAdmissionGuard.Denial.IDENTITY_RATE,
                denied.denial());
        assertTrue(denied.retryAfterMillis() > 0L);
        assertTrue(guard.acquire("identity-b", 3L).admitted());
        assertTrue(guard.acquire("identity-a", 10_004L).admitted());
    }

    @Test void globalRateBoundsManyValidDisposableIdentities() {
        AuthenticatedHotJoinAdmissionGuard guard = guard();
        assertTrue(guard.acquire("identity-a", 1L).admitted());
        assertTrue(guard.acquire("identity-b", 2L).admitted());
        assertTrue(guard.acquire("identity-c", 3L).admitted());
        assertTrue(guard.acquire("identity-d", 4L).admitted());

        var denied = guard.acquire("identity-e", 5L);
        assertEquals(
                AuthenticatedHotJoinAdmissionGuard.Denial.GLOBAL_RATE,
                denied.denial());
        assertTrue(guard.acquire("identity-e", 10_002L).admitted());
    }

    @Test void trackedIdentityStateIsStrictlyBounded() {
        AuthenticatedHotJoinAdmissionGuard guard
                = new AuthenticatedHotJoinAdmissionGuard(3, 10, 2,
                        1_000L, 2_000L, 2_000L);
        assertTrue(guard.acquire("identity-a", 1L).admitted());
        assertTrue(guard.acquire("identity-b", 1L).admitted());
        assertEquals(
                AuthenticatedHotJoinAdmissionGuard.Denial.STATE_CAPACITY,
                guard.acquire("identity-c", 2L).denial());
        assertTrue(guard.acquire("identity-c", 3_002L).admitted());
    }

    private static AuthenticatedHotJoinAdmissionGuard guard() {
        return new AuthenticatedHotJoinAdmissionGuard(3, 4, 100,
                10_000L, 10_000L, 20_000L);
    }
}
