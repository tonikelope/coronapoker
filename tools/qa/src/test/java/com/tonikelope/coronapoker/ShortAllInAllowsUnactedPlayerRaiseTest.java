package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class ShortAllInAllowsUnactedPlayerRaiseTest {
    @Test
    public void playerYetToActRetainsRaiseEntitlement() {
        BettingRoundState state = BettingRoundFixtures.fourSeats();
        state = BettingRoundFixtures.apply(state, "A", BettingRoundState.Action.RAISE, 100L);
        state = BettingRoundFixtures.apply(state, "B", BettingRoundState.Action.CHECK_CALL, 100L);
        state = BettingRoundFixtures.apply(state, "C", BettingRoundState.Action.ALL_IN, 150L);
        assertTrue(state.legalActions("D").canRaise());
    }
}
