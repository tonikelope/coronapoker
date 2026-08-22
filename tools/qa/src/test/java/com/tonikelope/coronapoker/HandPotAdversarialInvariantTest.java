/*
 * Copyright (C) 2026 tonikelope
 *
 * Deterministic adversarial/property coverage for the pot builder.  This is
 * deliberately separate from the hand-traced characterization tests: its
 * job is to generate hostile but valid committed-chip layouts and check the
 * invariants that must hold for every one of them.
 */
package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class HandPotAdversarialInvariantTest {

    private static final double EPS = 1e-7;
    private static final long SEED = 0x2345ADDE5L;

    private static double committed(FakePotPlayer[] players) {
        double total = 0;
        for (FakePotPlayer player : players) {
            total += player.getBote();
        }
        return total;
    }

    private static double sumLayers(HandPot top) {
        double total = 0;
        for (HandPot layer = top; layer != null; layer = layer.getSidePot()) {
            total += layer.getTotal();
        }
        return total;
    }

    private static boolean eligible(Player player) {
        return player.getDecision() != Player.FOLD
                && (player.isActivo() || player.getDecision() == Player.ALLIN);
    }

    @Test
    void hostileValidCommitmentsConserveEveryChipAndKeepEverySidePotClaimable() {
        Random random = new Random(SEED);
        int generated = 2000;

        for (int hand = 0; hand < generated; hand++) {
            int playerCount = hand == 0 ? 10 : 2 + random.nextInt(9);
            FakePotPlayer[] players = new FakePotPlayer[playerCount];
            for (int seat = 0; seat < playerCount; seat++) {
                // Integer cents avoid introducing input-side floating point noise while
                // still exercising zero, tiny, equal and sharply layered commitments.
                int cents = random.nextInt(2001);
                int decisionRoll = random.nextInt(10);
                int decision;
                boolean active;
                if (decisionRoll < 2) {
                    decision = Player.FOLD;
                    active = false;
                } else if (decisionRoll < 6) {
                    decision = Player.ALLIN;
                    // Half of the all-ins model a disconnect after committing chips.
                    active = random.nextBoolean();
                } else {
                    decision = random.nextBoolean() ? Player.BET : Player.CHECK;
                    active = true;
                }
                players[seat] = new FakePotPlayer("adv-" + hand + "-" + seat,
                        cents / 100.0, decision, active);
            }

            HandPot top = new HandPot(0);
            for (FakePotPlayer player : players) {
                top.addPlayer(player);
            }
            top.genSidePots();

            double expected = committed(players);
            double actual = sumLayers(top);
            assertEquals(expected, actual, EPS,
                    "money conservation failed for seed " + SEED + ", hand " + hand);

            int layers = 0;
            for (HandPot layer = top; layer != null; layer = layer.getSidePot()) {
                layers++;
                assertTrue(layer.getBet() >= -EPS,
                        "negative layer cap for seed " + SEED + ", hand " + hand);
                assertTrue(layer.getTotal() >= -EPS,
                        "negative layer total for seed " + SEED + ", hand " + hand);
                if (layer != top) {
                    assertTrue(layer.getPlayers().stream().anyMatch(HandPotAdversarialInvariantTest::eligible),
                            "side pot without a claimant for seed " + SEED + ", hand " + hand);
                }
            }
            assertEquals(layers - 1, top.getSide_pot_count(),
                    "side-pot count disagrees with its chain for seed " + SEED + ", hand " + hand);
            assertTrue(layers <= playerCount,
                    "more pot layers than players for seed " + SEED + ", hand " + hand);
        }
    }
}
