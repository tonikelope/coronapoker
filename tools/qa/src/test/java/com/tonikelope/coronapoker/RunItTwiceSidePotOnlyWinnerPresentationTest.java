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
        assertEquals(List.of(1), plan.wonPotIndexes().get("alice"));
        assertEquals(List.of(2), plan.wonPotIndexes().get("bob"));
        assertTrue(plan.winners().containsKey("bob"));
        assertFalse(plan.losers().containsKey("bob"));
    }

    @Test
    void winnerCarriesEveryPotIndexInSettlementOrder() {
        SettlementPresentation.Plan<String, String> plan
                = SettlementPresentation.plan(
                        Map.of("alice", "main"), Map.of("alice", "main"),
                        List.of(Map.of("alice", "side 1"),
                                Map.of("alice", "side 2")),
                        List.of(Map.of("alice", "side 1"),
                                Map.of("alice", "side 2")));

        assertEquals(List.of(1, 2, 3),
                plan.wonPotIndexes().get("alice"));
    }

    @Test
    void ordinaryPotAndUncontestedRefundDoNotAddPotLabels() {
        SettlementPresentation.Plan<String, String> ordinary
                = SettlementPresentation.plan(
                        Map.of("alice", "main"), Map.of("alice", "main"),
                        List.of(), List.of());
        SettlementPresentation.Plan<String, String> withRefund
                = SettlementPresentation.plan(
                        Map.of("alice", "main", "bob", "loser"),
                        Map.of("alice", "main"),
                        List.of(Map.of()), List.of(Map.of()));

        assertTrue(ordinary.wonPotIndexes().isEmpty());
        assertEquals(List.of(1), withRefund.wonPotIndexes().get("alice"));
        assertFalse(withRefund.wonPotIndexes().containsKey("bob"));
    }

    @Test
    void presentationPlanIsImmutableAfterConstruction() {
        SettlementPresentation.Plan<String, String> plan = SettlementPresentation.plan(
                Map.of("alice", "win"), Map.of("alice", "win"), List.of(), List.of());

        assertThrows(UnsupportedOperationException.class, () -> plan.winners().put("mallory", "fake"));
        assertThrows(UnsupportedOperationException.class, () -> plan.losers().put("mallory", "fake"));
        assertThrows(UnsupportedOperationException.class,
                () -> plan.wonPotIndexes().put("mallory", List.of(2)));
    }
}
