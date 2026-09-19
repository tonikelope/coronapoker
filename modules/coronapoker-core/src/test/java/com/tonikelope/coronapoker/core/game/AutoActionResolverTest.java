package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class AutoActionResolverTest {

    @Test
    void matchesFoldCheckCallAndAllInRules() {
        ActionControlState free = ActionControlState.forTurn(
                10d, 10d, 10d, 5d, 10d, 100d, 3, true, 0);
        ActionControlState paid = ActionControlState.forTurn(
                20d, 10d, 10d, 5d, 10d, 100d, 3, true, 0);
        ActionControlState bigBlindOption = ActionControlState.forTurn(
                10d, 10d, 10d, 5d, 5d, 100d, 3, true, 0);
        ActionControlState allInCall = ActionControlState.forTurn(
                100d, 10d, 10d, 5d, 0d, 50d, 3, true, 0);

        assertEquals(AutoActionResolver.Target.CHECK_OR_CALL, resolve(
                AutoActionResolver.QueuedAction.FOLD_OR_CHECK, free,
                false, false, 0d));
        assertEquals(AutoActionResolver.Target.FOLD, resolve(
                AutoActionResolver.QueuedAction.FOLD_OR_CHECK, paid,
                false, false, 0d));
        assertEquals(AutoActionResolver.Target.NONE, resolve(
                AutoActionResolver.QueuedAction.CHECK_OR_CALL, paid,
                false, false, 0d));
        assertEquals(AutoActionResolver.Target.CHECK_OR_CALL, resolve(
                AutoActionResolver.QueuedAction.CHECK_OR_CALL, bigBlindOption,
                true, false, 0d));
        assertEquals(AutoActionResolver.Target.CHECK_OR_CALL, resolve(
                AutoActionResolver.QueuedAction.CHECK_OR_CALL, paid,
                false, true, 10d));
        assertEquals(AutoActionResolver.Target.ALL_IN, resolve(
                AutoActionResolver.QueuedAction.CHECK_OR_CALL, allInCall,
                false, true, 50d));

        assertEquals(AutoActionResolver.Target.NONE, resolve(
                AutoActionResolver.QueuedAction.CHECK_OR_CALL, paid,
                false, true, 9.99d));
        assertEquals(AutoActionResolver.Target.NONE, resolve(
                AutoActionResolver.QueuedAction.CHECK_OR_CALL, allInCall,
                false, true, 49.99d));
        assertEquals(AutoActionResolver.Target.CHECK_OR_CALL, resolve(
                AutoActionResolver.QueuedAction.CHECK_OR_CALL, paid,
                false, true, 0d));
    }

    @Test
    void failsClosedWhenNoLegalTargetExists() {
        ActionControlState disabled = ActionControlState.disabled();
        assertEquals(AutoActionResolver.Target.NONE, resolve(
                AutoActionResolver.QueuedAction.NONE, disabled,
                false, true, 0d));
        assertEquals(AutoActionResolver.Target.NONE, resolve(
                AutoActionResolver.QueuedAction.FOLD_OR_CHECK, disabled,
                false, true, 0d));
        assertEquals(AutoActionResolver.Target.NONE, resolve(
                AutoActionResolver.QueuedAction.CHECK_OR_CALL, disabled,
                false, true, 0d));
    }

    private static AutoActionResolver.Target resolve(
            AutoActionResolver.QueuedAction queued,
            ActionControlState controls, boolean preflop,
            boolean autoCallEnabled, double autoCallMaximum) {
        return AutoActionResolver.resolve(queued, controls, preflop, 10d,
                autoCallEnabled, autoCallMaximum);
    }
}
