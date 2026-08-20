package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

public class HeadsUpShortRaiseMatrixTest {
    @Test
    public void headsUpShortAllInDoesNotReopenOriginalBettor() {
        LinkedHashMap<String, Long> committed = new LinkedHashMap<>();
        committed.put("button", 0L);
        committed.put("blind", 0L);
        BettingRoundState state = BettingRoundState.start(committed, 0L, 100L);
        state = BettingRoundFixtures.apply(state, "button", BettingRoundState.Action.RAISE, 100L);
        state = BettingRoundFixtures.apply(state, "blind", BettingRoundState.Action.ALL_IN, 150L);
        assertFalse(state.legalActions("button").canRaise());
    }
}
