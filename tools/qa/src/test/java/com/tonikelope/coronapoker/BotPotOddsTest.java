/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BotPotOddsTest {

    @Test
    void shortAllInCannotWinTheDeeperSidePot() {
        assertEquals(25, ev(0, 50, 200, List.of(seat(100, 0, 0), seat(100, 0, 0)),
                1, 0.7, 0.5), 1e-9);
    }

    @Test
    void evaluatesEachLayerAgainstItsEligibleOpponents() {
        // Three players contest 150. Only two players contest the next 100.
        assertEquals(80, ev(50, 50, 200, List.of(seat(100, 0, 0), seat(50, 0, 0)),
                1, 0.7, 0.4), 1e-9);
    }

    @Test
    void deadChipsAboveTheCapGoToTheDeepestLivePot() {
        assertEquals(70, ev(20, 30, 170, List.of(seat(50, 0, 0), seat(100, 0, 1)),
                1, 0.5), 1e-9);
        assertEquals(45, ev(20, 30, 220, List.of(seat(100, 0, 0), seat(100, 0, 1)),
                1, 0.5), 1e-9);
    }

    @Test
    void returnsTheUncalledPartOfAShoveAndCreditsOnlyTheChipsActuallyMatched() {
        assertEquals(18.5, ev(10, 90, 20, List.of(seat(10, 30, 0.25)),
                1, 0.6), 1e-9);
    }

    @Test
    void whenOneOpponentFoldsTheShortAllInStillContestsTheMainPot() {
        // If the deep seat folds, the hero gets 45 EV. If it calls, the
        // hero gets 60 EV after the 80 wager. Both outcomes include the short seat.
        assertEquals(52.5, ev(20, 80, 60, List.of(seat(20, 0, 0), seat(20, 80, 0.5)),
                1, 0.75, 1.0 / 3), 1e-9);
    }

    @Test
    void addsCarryToTheMainPotEvenWhenTheHeroHasYetToPutChipsIn() {
        assertEquals(2, ev(0, 0, 24, List.of(seat(20, 0, 0)), 1, 0.5), 1e-9);
    }

    @Test
    void pricesTheSinglePotFastPathByNumberOfCallers() {
        var callers = java.util.Collections.nCopies(5, seat(20, 10, 0.5));
        assertEquals(39.375, ev(20, 10, 120, callers,
                1, 0.5, 1.0 / 3, 0.25, 0.2, 1.0 / 6), 1e-9);
    }

    private static BotPotOdds.Seat seat(int committed, int added, double fold) {
        return new BotPotOdds.Seat(committed * 100L, added * 100L, fold);
    }

    private static double ev(int committed, int cost, int pot, List<BotPotOdds.Seat> seats,
            double... equity) {
        return BotPotOdds.ev(committed * 100L, cost * 100L, pot * 100L, seats, equity);
    }
}
