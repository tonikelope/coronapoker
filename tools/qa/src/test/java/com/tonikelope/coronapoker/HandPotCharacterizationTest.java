/*
 * Characterization (golden) test for HandPot side-pot formation.
 *
 * HandPot.genSidePots() / getTotal() turn each player's total committed chips
 * (Player.getBote()) into the layered main/side pots and the dead money from
 * folded players. Nothing else in the suite pins this algorithm, yet it is the
 * exact seam an ANTE feature perturbs (antes are extra committed/dead money).
 *
 * This test freezes the CURRENT behaviour so that, once ante/straddle land with
 * their options OFF, any accidental drift in the pot algorithm is caught. The
 * scenarios are hand-traced against the implementation and assert both the pot
 * structure (counts, per-layer totals/caps) and money conservation (every cent
 * committed lands in exactly one pot layer). Top pot built like production:
 * new HandPot(0) + addPlayer() per player (see Crupier.NUEVA_MANO).
 */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class HandPotCharacterizationTest {

    private static final double EPS = 1e-9;

    private static FakePotPlayer p(String nick, double bote, int decision, boolean activo) {
        return new FakePotPlayer(nick, bote, decision, activo);
    }

    private static HandPot topPot(FakePotPlayer... players) {
        HandPot pot = new HandPot(0); // production builds the top pot with diff 0
        for (FakePotPlayer pl : players) {
            pot.addPlayer(pl);
        }
        pot.genSidePots();
        return pot;
    }

    private static double sumAllPots(HandPot pot) {
        double total = 0;
        for (HandPot layer = pot; layer != null; layer = layer.getSidePot()) {
            total += layer.getTotal();
        }
        return total;
    }

    @Test
    void noAllInNoSidePots() {
        // Three players who all reached the same committed amount: a single pot.
        HandPot pot = topPot(
                p("a", 6.55, Player.BET, true),
                p("b", 6.55, Player.BET, true),
                p("c", 6.55, Player.BET, true));

        assertEquals(0, pot.getSide_pot_count(), "no all-in -> no side pots");
        assertEquals(6.55, pot.getBet(), EPS, "main pot caps at the common bet");
        assertEquals(19.65, pot.getTotal(), EPS, "3 x 6.55");
        assertEquals(19.65, sumAllPots(pot), EPS, "conservation");
    }

    @Test
    void oneShortAllInMakesOneSidePot() {
        // a is all-in for 2; b and c contest 10 each. Main pot = 2 from each of
        // the three (6); side pot = the 8 excess from b and c (16).
        HandPot pot = topPot(
                p("a", 2.0, Player.ALLIN, true),
                p("b", 10.0, Player.BET, true),
                p("c", 10.0, Player.BET, true));

        assertEquals(1, pot.getSide_pot_count(), "one short all-in -> one side pot");
        assertEquals(2.0, pot.getBet(), EPS, "main pot capped at the all-in amount");
        assertEquals(6.0, pot.getTotal(), EPS, "main pot = 2 x 3");

        HandPot side = pot.getSidePot();
        assertEquals(8.0, side.getBet(), EPS, "side pot cap = 10 - 2");
        assertEquals(16.0, side.getTotal(), EPS, "side pot = 8 x 2");

        assertEquals(22.0, sumAllPots(pot), EPS, "conservation: 2 + 10 + 10");
    }

    @Test
    void twoDifferentAllInsMakeTwoLayeredSidePots() {
        // a all-in 2, b all-in 5, c contests 10. Layers: main 2x3=6,
        // side1 (5-2=3) x2 = 6, side2 (10-5=5) x1 = 5.
        HandPot pot = topPot(
                p("a", 2.0, Player.ALLIN, true),
                p("b", 5.0, Player.ALLIN, true),
                p("c", 10.0, Player.BET, true));

        assertEquals(2, pot.getSide_pot_count(), "two distinct all-ins -> two side pots");
        assertEquals(6.0, pot.getTotal(), EPS, "main pot = 2 x 3");

        HandPot side1 = pot.getSidePot();
        assertEquals(6.0, side1.getTotal(), EPS, "first side pot = 3 x 2");

        HandPot side2 = side1.getSidePot();
        assertEquals(5.0, side2.getTotal(), EPS, "second side pot = 5 x 1");

        assertEquals(17.0, sumAllPots(pot), EPS, "conservation: 2 + 5 + 10");
    }

    @Test
    void foldedPlayerChipsAreDeadMoneyInTheMainPot() {
        // a posts 2 then folds; b and c contest 10 each. a's 2 is dead money that
        // stays in the main pot (the key invariant for folded anteers later).
        HandPot pot = topPot(
                p("a", 2.0, Player.FOLD, false),
                p("b", 10.0, Player.BET, true),
                p("c", 10.0, Player.BET, true));

        assertEquals(0, pot.getSide_pot_count(), "equal live contenders -> no side pot");
        assertEquals(10.0, pot.getBet(), EPS, "main pot caps at the live bet");
        assertEquals(22.0, pot.getTotal(), EPS, "10 + 10 live + 2 dead from the folder");
        assertEquals(22.0, sumAllPots(pot), EPS, "conservation: 2 + 10 + 10");
    }
}
