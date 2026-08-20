package com.tonikelope.coronapoker;

import java.util.LinkedHashMap;

final class BettingRoundFixtures {
    private BettingRoundFixtures() {
    }

    static BettingRoundState fourSeats() {
        LinkedHashMap<String, Long> committed = new LinkedHashMap<>();
        committed.put("A", 0L);
        committed.put("B", 0L);
        committed.put("C", 0L);
        committed.put("D", 0L);
        return BettingRoundState.start(committed, 0L, 100L);
    }

    static BettingRoundState apply(BettingRoundState state, String seat,
            BettingRoundState.Action action, long total) {
        BettingRoundState.Transition transition = state.apply(seat, action, total);
        if (!transition.isAccepted()) {
            throw new AssertionError(transition.error());
        }
        return transition.state();
    }
}
