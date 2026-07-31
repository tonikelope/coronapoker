/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.smoke;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Money conservation when a hand is voided while it is being settled.
 *
 * The real settlement lives inside the Crupier state machine, which these tests
 * cannot instantiate (see GameFlowSmoke: the state machine is out of scope on
 * purpose). What is pinned here is the RULE the settlement follows, modelled
 * with the same operations in the same order, because the rule is what can
 * silently regress.
 *
 * The invariant across the whole game is:
 *
 *   sum(stacks) + leftover == sum(buyins)
 *
 * A voided hand refunds every bet and keeps the leftover, which belongs to the
 * game and not to the hand. Settling assigns whatever nobody claimed to the
 * leftover. Both run under the accounting lock, but the "hand is void" flag is
 * raised OUTSIDE it, so a whole settlement fits between the flag and the refund.
 *
 * Hence the settlement asks whether the money HAS ALREADY BEEN GIVEN BACK
 * rather than whether the hand is void: those are different questions, and
 * answering the second one lost the pot outright.
 *
 * Both halves matter, and only one of them needs the question asked:
 *
 * <ul>
 *   <li>with NOBODY left standing, nothing is paid out, so the settlement is
 *       the only chance to park the pot somewhere and it must ask;</li>
 *   <li>with a winner, the payout comes out of pot PLUS leftover, so asking
 *       there and skipping only the sink pays the inherited leftover AND keeps
 *       it. That mistake was made during this audit and is pinned below.</li>
 * </ul>
 */
class MisdealRefundOrderSmoke {

    private static final double EPS = 0.0001d;

    /**
     * The accounting both paths share, with one field per thing that moved.
     */
    private static final class Table {

        static final double BUYIN_EACH = 100d;
        static final int SEATS = 3;

        // The 0.03 leftover is money that already left the players' stacks in an
        // earlier hand, so it is NOT in them any more: 269.97 + 30 staked + 0.03
        // parked == the 300 that ever entered the game.
        double[] stack = {89.99d, 89.99d, 89.99d};
        double[] bet = {10d, 10d, 10d};
        // What the players staked this hand, on its own. The payout adds the
        // leftover to THIS, it does not choose between them.
        double hand_pot = 30d;
        // The running total carries the inherited leftover too: a hand opens at
        // max(0, leftover) and adds the bets on top.
        double pot_total = 30.03d;
        double leftover = 0.03d;
        boolean refunded = false;

        /** Total money that ever entered the game. */
        static double buyins() {
            return BUYIN_EACH * SEATS;
        }

        /**
         * A negative leftover is money nobody holds: every read clamps it at zero
         * (the hand seeds at max(0, leftover) and the displays compare against 0).
         */
        double effectiveLeftover() {
            return Math.max(0d, leftover);
        }

        /** Money that can be accounted for right now. */
        double onTable() {
            double sum = effectiveLeftover();

            for (int seat = 0; seat < SEATS; seat++) {
                sum += stack[seat] + bet[seat];
            }

            return sum;
        }

        /** Settlement, nobody left standing: the pot goes to the leftover. */
        void settleWithNoWinner() {
            if (!refunded) {
                leftover = pot_total;
            }

            pot_total = 0d;
            clearBets();
        }

        /**
         * Settlement with a winner: the payout comes out of pot PLUS leftover, and
         * the sink takes whatever was left unclaimed. NEITHER half asks whether the
         * money came back, on purpose: see the class javadoc.
         */
        void settleWithWinner(int winner) {
            // The real payout is hand pot PLUS leftover, a sum of two separate
            // amounts. Modelling it as a choice would hide the very mistake this
            // pins: with the money already refunded the hand pot is empty, so
            // what gets paid out is the inherited leftover.
            double payout = hand_pot + leftover;

            stack[winner] += payout;
            pot_total -= payout;
            leftover = pot_total;
            hand_pot = 0d;
            clearBets();
        }

        private void clearBets() {
            for (int seat = 0; seat < SEATS; seat++) {
                bet[seat] = 0d;
            }
        }

        /** Void: every bet goes home, the leftover is untouched by design. */
        void refund() {
            for (int seat = 0; seat < SEATS; seat++) {
                stack[seat] += bet[seat];
                bet[seat] = 0d;
            }

            pot_total = 0d;
            hand_pot = 0d;
            refunded = true;
        }
    }

    /**
     * The void arriving from INSIDE the settlement, which is the ordering that
     * makes freezing the answer wrong.
     *
     * The verification barrier runs inside the same lock the settlement holds,
     * and on a client it can end up voiding the hand right there. So the answer
     * to "has the money been given back?" changes AFTER the settlement started.
     * Read on entry, it still says no, and an emptied pot gets written over the
     * inherited leftover.
     */
    @Test
    @DisplayName("Void arriving mid-settlement: reading the answer on entry destroys the leftover")
    void freezingTheAnswerOnEntryDestroysTheLeftover() {
        Table frozen = new Table();
        Table fresh = new Table();

        // Both settle with nobody standing, and in both the barrier voids the hand
        // partway through.
        boolean answer_on_entry = frozen.refunded;   // false, captured too early
        frozen.refund();                             // the barrier voids it here
        if (!answer_on_entry) {
            frozen.leftover = frozen.pot_total;      // pot is 0 by now: leftover wiped
        }
        frozen.pot_total = 0d;
        frozen.clearBets();

        fresh.refund();                              // same barrier, same moment
        fresh.settleWithNoWinner();                  // asks at the point of use

        assertEquals(Table.buyins() - 0.03d, frozen.onTable(), EPS,
                "this pins the cost of freezing: the inherited leftover is destroyed");
        assertEquals(Table.buyins(), fresh.onTable(), EPS,
                "asking at the point of use keeps the books straight");
        assertEquals(0.03d, fresh.leftover, EPS, "the inherited leftover survives");
    }

    @Test
    @DisplayName("Nobody standing, settled before the void: the pot survives as leftover")
    void noWinnerSettledFirst() {
        Table table = new Table();

        table.settleWithNoWinner();
        table.refund();

        assertEquals(Table.buyins(), table.onTable(), EPS, "money was created or destroyed");
        assertEquals(30.03d, table.leftover, EPS, "the whole pot should have carried over");
    }

    @Test
    @DisplayName("Nobody standing, voided first: the inherited leftover survives")
    void noWinnerRefundedFirst() {
        Table table = new Table();

        table.refund();
        table.settleWithNoWinner();

        assertEquals(Table.buyins(), table.onTable(), EPS, "money was created or destroyed");
        assertEquals(0.03d, table.leftover, EPS,
                "settling after a refund must not overwrite the inherited leftover with an emptied pot");
        assertEquals(99.99d, table.stack[0], EPS, "the bet should have gone back to the player");
    }

    /**
     * The ordering that used to lose the pot: the void flag was already up, so
     * the old guard skipped the assignment expecting the refund to hand the money
     * back, but the settlement emptied every bet first and the refund then found
     * nothing to give.
     */
    @Test
    @DisplayName("Nobody standing, settled between the void flag and the refund: the pot is not lost")
    void noWinnerSettledBetweenFlagAndRefund() {
        Table table = new Table();

        // The flag is up here, but the refund has NOT run: nothing has been given
        // back yet, so the settlement must still park the pot somewhere.
        boolean hand_is_void = true;
        assertEquals(false, table.refunded, "precondition: the money is still on the table");

        if (hand_is_void && !table.refunded) {
            table.settleWithNoWinner();
        }

        table.refund();

        assertEquals(Table.buyins(), table.onTable(), EPS,
                "the pot vanished: it was neither carried over nor given back");
        assertEquals(30.03d, table.leftover, EPS, "the pot should have carried over");
    }

    @Test
    @DisplayName("Asking whether the hand is void loses the whole pot")
    void askingTheWrongQuestionLosesTheMoney() {
        Table table = new Table();

        // Reproduces the guard that was wrong: it asked whether the hand was void,
        // and the flag goes up while the money is still on the table.
        boolean hand_is_void = true;

        if (!hand_is_void) {
            table.leftover = table.pot_total;
        }

        table.pot_total = 0d;
        table.clearBets();
        table.refund();

        assertEquals(Table.buyins() - 30d, table.onTable(), EPS,
                "this pins the broken behaviour: thirty chips used to disappear here");
    }

    @Test
    @DisplayName("With a winner and the money already back, the payout must NOT be guarded")
    void winnerPayoutStaysUnguarded() {
        Table table = new Table();

        table.refund();
        table.settleWithWinner(0);

        assertEquals(Table.buyins(), table.onTable(), EPS, "money was created or destroyed");
        assertEquals(99.99d + 0.03d, table.stack[0], EPS,
                "the winner takes the inherited leftover and the sink is left empty");
    }

    /**
     * Guarding the sink but not the payout, which is what a well meaning fix did
     * during this audit: the winner is paid the inherited leftover out of an empty
     * pot AND the leftover is kept, so the money exists twice.
     */
    @Test
    @DisplayName("Guarding only the sink on a winner CREATES chips")
    void guardingOnlyTheSinkCreatesChips() {
        Table table = new Table();

        table.refund();

        // Payout unguarded (as it really was), sink guarded (the mistake).
        double payout = table.pot_total > 0d ? table.pot_total : table.effectiveLeftover();
        table.stack[0] += payout;
        table.pot_total -= payout;
        // if (!refunded) { leftover = pot_total; }  <-- skipped, so leftover stays
        table.clearBets();

        assertEquals(Table.buyins() + 0.03d, table.onTable(), EPS,
                "this pins why the sink must not be guarded on its own: the leftover is paid and kept");
    }
}
