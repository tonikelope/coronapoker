package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

public class MultipleShortRaisesPolicyTest {
    @Test
    public void cumulativeShortRaisesAreMeasuredSinceEachSeatsLastAction() {
        LinkedHashMap<String, Long> committed = new LinkedHashMap<>();
        for (String seat : new String[]{"A", "B", "C", "D", "E"}) {
            committed.put(seat, 0L);
        }
        BettingRoundState state = BettingRoundState.start(committed, 0L, 100L);
        state = BettingRoundFixtures.apply(state, "A", BettingRoundState.Action.RAISE, 100L);
        state = BettingRoundFixtures.apply(state, "B", BettingRoundState.Action.CHECK_CALL, 100L);
        state = BettingRoundFixtures.apply(state, "C", BettingRoundState.Action.ALL_IN, 150L);
        state = BettingRoundFixtures.apply(state, "D", BettingRoundState.Action.CHECK_CALL, 150L);
        state = BettingRoundFixtures.apply(state, "E", BettingRoundState.Action.ALL_IN, 200L);

        assertTrue(state.legalActions("B").canRaise(), "B has faced 100 since acting");
        assertFalse(state.legalActions("D").canRaise(), "D has faced only 50 since acting");
    }
}
