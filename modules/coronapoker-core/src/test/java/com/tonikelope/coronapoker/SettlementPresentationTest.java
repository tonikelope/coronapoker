package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SettlementPresentationTest {

    @Test
    void mainAndResidualSidePotAreReportedAsOneWinnerVerdict() {
        SettlementPresentation.Plan<String, String> plan
                = SettlementPresentation.plan(
                        Map.of("ana", "COLOR", "bea", "PAREJA"),
                        Map.of("ana", "COLOR"),
                        List.of(Map.of("ana", "COLOR")),
                        List.of(Map.of("ana", "COLOR")));

        assertEquals(List.of(1, 2), plan.wonPotIndexes().get("ana"));
        assertEquals("COLOR", plan.winners().get("ana"));
        assertEquals(Map.of("bea", "PAREJA"), plan.losers());
    }

    @Test
    void sidePotOnlyWinnerNeverKeepsTheMainPotLossVerdict() {
        SettlementPresentation.Plan<String, String> plan
                = SettlementPresentation.plan(
                        Map.of("ana", "PAREJA", "bea", "COLOR"),
                        Map.of("bea", "COLOR"),
                        List.of(Map.of("ana", "PAREJA")),
                        List.of(Map.of("ana", "PAREJA")));

        assertEquals(List.of(2), plan.wonPotIndexes().get("ana"));
        assertEquals("PAREJA", plan.winners().get("ana"));
        assertFalse(plan.losers().containsKey("ana"));
    }
}
