package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CrupierTerminationTailTest {

    @Test
    void naturalClientCompletionPreservesTheAuthenticatedFinalRoster() {
        assertFalse(Crupier.shouldSendLocalExitAtDealerTail(
                false, false, false, false, false));
    }

    @Test
    void explicitlyArmedClientExitStillSendsItsTestament() {
        assertTrue(Crupier.shouldSendLocalExitAtDealerTail(
                false, false, false, false, true));
    }

    @Test
    void authoritativeAndRecoveryTeardownsNeverAnswerWithAClientExit() {
        assertFalse(Crupier.shouldSendLocalExitAtDealerTail(
                false, false, true, false, true));
        assertFalse(Crupier.shouldSendLocalExitAtDealerTail(
                false, true, false, false, true));
        assertFalse(Crupier.shouldSendLocalExitAtDealerTail(
                false, false, false, true, true));
        assertFalse(Crupier.shouldSendLocalExitAtDealerTail(
                true, false, false, false, true));
    }
}
