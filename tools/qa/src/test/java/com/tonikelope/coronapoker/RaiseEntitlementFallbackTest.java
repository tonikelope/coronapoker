package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RaiseEntitlementFallbackTest {

    @Test
    void localProducerGetsANormalFoldThatCanBeSignedAndBroadcast() {
        Object[] action = Crupier.rejectedRaiseFallback(true);
        assertEquals(3, action.length);
        assertEquals(Player.FOLD, action[0]);
        assertFalse(Crupier.isUnverifiedSynthFold(action));
    }

    @Test
    void invalidRemoteProducerGetsAnExplicitUnverifiedSyntheticFold() {
        Object[] action = Crupier.rejectedRaiseFallback(false);
        assertEquals(Player.FOLD, action[0]);
        assertTrue(Crupier.isUnverifiedSynthFold(action));
    }
}
