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
}
