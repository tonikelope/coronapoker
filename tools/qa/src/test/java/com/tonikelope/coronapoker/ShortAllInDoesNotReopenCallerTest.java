package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

public class ShortAllInDoesNotReopenCallerTest {
    @Test
    public void callerWhoAlreadyActedCannotReraiseShortAllIn() {
        BettingRoundState state = BettingRoundFixtures.fourSeats();
        state = BettingRoundFixtures.apply(state, "A", BettingRoundState.Action.RAISE, 100L);
        state = BettingRoundFixtures.apply(state, "B", BettingRoundState.Action.CHECK_CALL, 100L);
        state = BettingRoundFixtures.apply(state, "C", BettingRoundState.Action.ALL_IN, 150L);
        assertFalse(state.legalActions("B").canRaise());
        assertFalse(state.apply("B", BettingRoundState.Action.RAISE, 250L).isAccepted());
    }
}
