/*
 * No-Limit Texas Hold'em betting minimums. Documents and guards the rules the
 * engine enforces: open = big blind, raise increment = last raise (or BB), and
 * the full-vs-partial(all-in) raise threshold at cent resolution.
 */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class BetRulesTest {

    @Test
    void minOpenIsTheBigBlind() {
        assertEquals(0.20f, BetRules.minOpen(0.20f), 0f);
        assertEquals(0.50f, BetRules.minOpen(0.50f), 0f); // 0.25/0.50 game
        assertEquals(2f, BetRules.minOpen(2f), 0f);
    }

    @Test
    void minRaiseIncrementIsTheBigBlindWhenNoRaiseYet() {
        assertEquals(0.50f, BetRules.minRaiseIncrement(0f, 0.50f), 0f);
        assertEquals(2f, BetRules.minRaiseIncrement(0f, 2f), 0f);
    }

    @Test
    void minRaiseIncrementIsTheLastRaiseWhenThereWasOne() {
        // After a raise of 3 over a BB of 2, the next raise must increase by >= 3.
        assertEquals(3f, BetRules.minRaiseIncrement(3f, 2f), 0f);
        assertEquals(0.75f, BetRules.minRaiseIncrement(0.75f, 0.50f), 0f);
    }

    @Test
    void minRaiseToIsCurrentBetPlusIncrement() {
        // Preflop BB=2, current bet 2 -> first raise to at least 4 (2 + 2).
        assertEquals(4f, BetRules.minRaiseTo(2f, 0f, 2f), 0f);
        // Bet at 5 after a raise increment of 3 -> next raise to at least 8.
        assertEquals(8f, BetRules.minRaiseTo(5f, 3f, 2f), 0f);
        // 0.25/0.50: bet at the BB 0.50, no prior raise -> raise to at least 1.00.
        assertEquals(1.00f, BetRules.minRaiseTo(0.50f, 0f, 0.50f), 0f);
    }

    @Test
    void fullRaiseReopensOnlyWhenIncrementMeetsTheMinimum() {
        assertTrue(BetRules.isFullRaise(0.50f, 0.50f));  // exactly the minimum
        assertTrue(BetRules.isFullRaise(0.75f, 0.50f));  // more than the minimum
        // An all-in for less than the minimum increment is a partial raise.
        assertFalse(BetRules.isFullRaise(0.30f, 0.50f));
        assertFalse(BetRules.isFullRaise(0.45f, 0.50f));
    }

    @Test
    void fullRaiseComparesAtCentResolution() {
        assertTrue(BetRules.isFullRaise(0.499999f, 0.50f)); // same cent -> full
        assertFalse(BetRules.isFullRaise(0.49f, 0.50f));    // one cent short -> partial
    }

    // ----- double money overloads: float -> double migration safety net -------

    @Test
    void doubleOverloadsAgreeWithFloatBelowCeiling() {
        double[] bbs = {0.05, 0.10, 0.20, 0.50, 1.0, 2.0, 5.0, 12.34};
        double[] lasts = {0.0, 0.05, 0.50, 0.75, 3.0};
        double[] bets = {0.0, 0.50, 2.0, 5.0, 100.0};
        for (double bb : bbs) {
            assertEquals(BetRules.minOpen((float) bb), (float) BetRules.minOpen(bb), 0f,
                    "minOpen must agree bb=" + bb);
            for (double last : lasts) {
                assertEquals(BetRules.minRaiseIncrement((float) last, (float) bb),
                        (float) BetRules.minRaiseIncrement(last, bb), 0f,
                        "minRaiseIncrement must agree last=" + last + " bb=" + bb);
                for (double bet : bets) {
                    assertEquals(BetRules.minRaiseTo((float) bet, (float) last, (float) bb),
                            (float) BetRules.minRaiseTo(bet, last, bb), 0f,
                            "minRaiseTo must agree bet=" + bet + " last=" + last + " bb=" + bb);
                }
            }
        }
    }

    @Test
    void doubleFullRaiseComparesAtCentResolution() {
        assertTrue(BetRules.isFullRaise(0.50, 0.50));      // exactly the minimum
        assertTrue(BetRules.isFullRaise(0.499999, 0.50));  // same cent -> full
        assertFalse(BetRules.isFullRaise(0.49, 0.50));     // one cent short -> partial
        assertFalse(BetRules.isFullRaise(0.30, 0.50));
    }

    @Test
    void doubleMinimumsAreExactAboveTheFloatCeiling() {
        // A deep-stack raise above ~131072 chips: the minimum-to is exact in double
        // where the float overload would round the sum off the cent grid.
        double bb = 2.0;
        double currentBet = 200000.05;
        double lastRaise = 150.0;
        assertEquals(200150.05, BetRules.minRaiseTo(currentBet, lastRaise, bb), 0.0);
        assertEquals(150.0, BetRules.minRaiseIncrement(lastRaise, bb), 0.0);
        // A full-vs-partial decision still resolves at the cent above the ceiling.
        assertTrue(BetRules.isFullRaise(150.00, 150.00));
        assertFalse(BetRules.isFullRaise(149.99, 150.00));
    }
}
