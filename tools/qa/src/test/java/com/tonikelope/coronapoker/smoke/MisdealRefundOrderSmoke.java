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
 * with the exact same operations in the exact same order, because the rule is
 * what was wrong and the rule is what can silently regress.
 *
 * The invariant across the whole game is:
 *
 *   sum(stacks) + leftover == sum(buyins)
 *
 * A voided hand refunds every bet and keeps the leftover, which belongs to the
 * game and not to the hand. Settling assigns the unclaimed pot to the leftover.
 * Both run under the accounting lock, but the "hand is void" flag is raised
 * OUTSIDE it, so an entire settlement fits between the flag and the refund.
 *
 * That is why the settlement asks whether the money HAS ALREADY BEEN GIVEN BACK
 * rather than whether the hand is void: those are not the same question, and
 * answering the second one lost the pot outright in the ordering below named
 * {@code settleBetweenFlagAndRefund}.
 */
class MisdealRefundOrderSmoke {

    private static final double EPS = 0.0001d;

    /**
     * The accounting the two paths share, with one field per thing that moved.
     */
    private static final class Table {

        static final double BUYIN_EACH = 100d;
        static final int SEATS = 3;

        // The 0.03 leftover is money that already left the players' stacks in an
        // earlier hand, so it is NOT in them any more: 269.97 + 30 staked + 0.03
        // parked == the 300 that ever entered the game.
        double[] stack = {89.99d, 89.99d, 89.99d};
        double[] bet = {10d, 10d, 10d};
        // The pot carries the inherited leftover: a hand opens at max(0, leftover)
        // and adds the bets on top.
        double pot_total = 30.03d;
        double leftover = 0.03d;
        boolean refunded = false;

        /** Total money that ever entered the game. */
        static double buyins() {
            return BUYIN_EACH * SEATS;
        }

        /** Money that can be accounted for right now. */
        double onTable() {
            double sum = leftover;

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
            refunded = true;
        }
    }

    @Test
    @DisplayName("Settled first, voided afterwards: the pot survives as leftover")
    void settleThenRefund() {
        Table table = new Table();

        table.settleWithNoWinner();
        table.refund();

        assertEquals(Table.buyins(), table.onTable(), EPS, "money was created or destroyed");
        assertEquals(30.03d, table.leftover, EPS, "the whole pot should have carried over");
    }

    @Test
    @DisplayName("Voided first, settled afterwards: the inherited leftover survives")
    void refundThenSettle() {
        Table table = new Table();

        table.refund();
        table.settleWithNoWinner();

        assertEquals(Table.buyins(), table.onTable(), EPS, "money was created or destroyed");
        assertEquals(0.03d, table.leftover, EPS,
                "settling after a refund must not overwrite the inherited leftover with an emptied pot");
        assertEquals(99.99d, table.stack[0], EPS, "the bet should have gone back to the player");
    }

    /**
     * The ordering that used to lose the pot: the void flag is up, so the old
     * guard skipped the assignment expecting the refund to hand the money back,
     * but the settlement emptied every bet first and the refund then found
     * nothing to give.
     */
    @Test
    @DisplayName("Settled between the void flag and the refund: the pot is still not lost")
    void settleBetweenFlagAndRefund() {
        Table table = new Table();

        // The flag is up here, but the refund has NOT run: nothing has been
        // given back yet, so the settlement must still park the pot somewhere.
        table.settleWithNoWinner();
        table.refund();

        assertEquals(Table.buyins(), table.onTable(), EPS,
                "the pot vanished: it was neither carried over nor given back");
        assertEquals(30.03d, table.leftover, EPS, "the pot should have carried over");
    }

    @Test
    @DisplayName("Asking whether the hand is void instead loses the whole pot")
    void theOldRuleLosesTheMoney() {
        Table table = new Table();

        // Reproduces the old guard: it asked whether the hand was void, and the
        // flag was already up while the money was still on the table.
        boolean hand_is_void = true;

        if (!hand_is_void) {
            table.leftover = table.pot_total;
        }

        table.pot_total = 0d;

        for (int seat = 0; seat < Table.SEATS; seat++) {
            table.bet[seat] = 0d;
        }

        table.refund();

        assertEquals(Table.buyins() - 30d, table.onTable(), EPS,
                "this pins the old behaviour: thirty chips used to disappear here");
    }
}
