package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunItTwiceSidePotOnlyWinnerPresentationTest {

    @Test
    void sidePotOnlyWinnerCannotAlsoBePresentedAsLoser() {
        Map<String, String> mainHands = Map.of(
                "alice", "main winner", "bob", "main loser", "carol", "main loser");
        Map<String, String> mainWinners = Map.of("alice", "main winner");
        Map<String, String> sideHands = Map.of("bob", "side winner", "carol", "side loser");
        Map<String, String> sideWinners = Map.of("bob", "side winner");

        SettlementPresentation.Plan<String, String> plan = SettlementPresentation.plan(
                mainHands, mainWinners, List.of(sideHands), List.of(sideWinners));

        assertEquals(Map.of("alice", "main winner", "bob", "side winner"), plan.winners());
        assertEquals(Map.of("carol", "side loser"), plan.losers());
        assertTrue(plan.winners().containsKey("bob"));
        assertFalse(plan.losers().containsKey("bob"));
    }

    @Test
    void presentationPlanIsImmutableAfterConstruction() {
        SettlementPresentation.Plan<String, String> plan = SettlementPresentation.plan(
                Map.of("alice", "win"), Map.of("alice", "win"), List.of(), List.of());

        assertThrows(UnsupportedOperationException.class, () -> plan.winners().put("mallory", "fake"));
        assertThrows(UnsupportedOperationException.class, () -> plan.losers().put("mallory", "fake"));
    }
}
