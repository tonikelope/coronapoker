package com.tonikelope.coronapoker.core.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ActionControlStateTest {

    @Test
    void calculatesAlignedOpenRaiseAndCallControls() {
        ActionControlState open = ActionControlState.forTurn(
                0d, 0d, 2d, 1d, 0d, 100d, 3, true, 0);
        assertEquals(ActionControlState.CallAction.CHECK, open.callAction());
        assertEquals(ActionControlState.RaiseAction.BET, open.raiseAction());
        assertEquals(2d, open.raiseMinimum());
        assertEquals(100d, open.raiseMaximum());
        assertEquals(1d, open.raiseStep());

        ActionControlState reraise = ActionControlState.forTurn(
                7.5d, 3d, 2d, 1d, 2d, 28d, 3, true, 1);
        assertEquals(ActionControlState.CallAction.CALL, reraise.callAction());
        assertEquals(5.5d, reraise.callAmount());
        assertEquals(ActionControlState.RaiseAction.RERAISE,
                reraise.raiseAction());
        assertEquals(3.5d, reraise.raiseMinimum());
        assertEquals(22.5d, reraise.raiseMaximum());
        assertTrue(reraise.allInEnabled());
    }

    @Test
    void disablesRaiseAndAllInWhenOnlyAnotherAllInCanBeCalled() {
        ActionControlState controls = ActionControlState.forTurn(
                10d, 4d, 2d, 1d, 5d, 20d, 1, false, 0);
        assertEquals(ActionControlState.CallAction.CALL, controls.callAction());
        assertEquals(ActionControlState.RaiseAction.DISABLED,
                controls.raiseAction());
        assertFalse(controls.allInEnabled());
        assertTrue(controls.foldEnabled());
    }

    @Test
    void voluntaryShowIsAnIndependentPostTurnAction() {
        ActionControlState controls = ActionControlState.disabled()
                .withShowCards(true);

        assertTrue(controls.showCards());
        assertFalse(controls.foldEnabled());
        assertEquals(ActionControlState.CallAction.DISABLED,
                controls.callAction());
        assertEquals(ActionControlState.RaiseAction.DISABLED,
                controls.raiseAction());
        assertFalse(controls.allInEnabled());
        assertEquals(ActionControlState.disabled(),
                controls.withShowCards(false));
    }
}
