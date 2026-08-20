package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class HandverifyTriggerWaitIsBoundedTest {

    @Test
    public void activeWaitExpiresAtTheDeclaredProgressBudget() {
        assertTrue(Crupier.HANDVERIFY_TRIGGER_PROGRESS_TIMEOUT_MS
                >= Crupier.RECON_CHURN_HARD_CAP_MS + Crupier.BROADCAST_PROGRESS_TIMEOUT_MS);
        assertFalse(Crupier.handverifyTriggerWaitExpired(
                Crupier.HANDVERIFY_TRIGGER_PROGRESS_TIMEOUT_MS - 1L));
        assertTrue(Crupier.handverifyTriggerWaitExpired(
                Crupier.HANDVERIFY_TRIGGER_PROGRESS_TIMEOUT_MS));
    }
}
