/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ZeroTrustIncidentContractTest {

    @Test
    void everyFatalFamilyRequiresEvidenceAcknowledgementAndFailureClosure() {
        for (ZeroTrustIncidentContract family
                : ZeroTrustIncidentContract.values()) {
            assertTrue(family.requiresEvidence(), family.name());
            assertTrue(family.requiresAcknowledgementBeforeClose(),
                    family.name());
            assertTrue(family.closesAsFailure(), family.name());
        }
    }

    @Test
    void preservationMatchesTheFinancialPhase() {
        assertTrue(ZeroTrustIncidentContract.SECURITY_LOCKDOWN
                .preserveOpenHand());
        assertTrue(ZeroTrustIncidentContract.SETTLEMENT_CONSENSUS
                .preserveOpenHand());
        assertFalse(ZeroTrustIncidentContract.PRE_SETTLEMENT_ABORT
                .preserveOpenHand());
        assertFalse(ZeroTrustIncidentContract.HAND_BOUNDARY
                .preserveOpenHand());
    }
}
