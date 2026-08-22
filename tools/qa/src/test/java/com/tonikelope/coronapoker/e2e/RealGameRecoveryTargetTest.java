package com.tonikelope.coronapoker.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RealGameRecoveryTargetTest {

    @Test
    void freshNewcomerCountsOnlyPostRecoveryHandsStoredLocally() {
        assertEquals(1, RealGameLoopbackE2EIT.freshNewcomerTargetHands(2));
        assertEquals(4, RealGameLoopbackE2EIT.freshNewcomerTargetHands(5));
    }

    @Test
    void freshNewcomerRequiresAtLeastOnePostRecoveryHand() {
        assertThrows(IllegalArgumentException.class,
                () -> RealGameLoopbackE2EIT.freshNewcomerTargetHands(1));
    }
}
