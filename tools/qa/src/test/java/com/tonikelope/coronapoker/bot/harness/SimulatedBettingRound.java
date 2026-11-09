/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.bot.harness;

import com.tonikelope.coronapoker.BetRules;
import com.tonikelope.coronapoker.BettingRoundState;
import com.tonikelope.coronapoker.Bot;
import com.tonikelope.coronapoker.Crupier;
import com.tonikelope.coronapoker.core.game.GamePlayerController;
import com.tonikelope.coronapoker.core.game.MoneyMath;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runs a QA betting street through the same action reducer as the table. */
final class SimulatedBettingRound {

    private SimulatedBettingRound() {}

    interface Observer {
        void accepted(int seat, boolean folded, boolean raised, long paid, long toCall);
    }

    /** Returns true if the hand ended by folding. */
    static boolean play(TestDealer dealer, TestBotPlayer[] players, Bot[] bots, int firstToAct,
            int limit, Observer observer) {
        Map<String, Long> committed = new LinkedHashMap<>();
        boolean[] pending = new boolean[players.length];
        for (int i = 0; i < players.length; i++) {
            TestBotPlayer player = players[i];
            if (player.isActivo() && cents(player.getStack()) > 0) {
                committed.put(player.getNickname(), cents(player.getBet()));
                pending[i] = true;
            }
        }
        long wager = cents(dealer.getApuesta_actual());
        if (committed.isEmpty()) {
            return false;
        }
        long minRaise = cents(BetRules.minRaiseIncrement(dealer.getUltimo_raise(), dealer.getCiega_grande()));
        BettingRoundState round = BettingRoundState.start(committed, wager, minRaise);
        dealer.setBettingRound(round);
        try {
            int cursor = firstToAct;
            int actions = 0;
            while (true) {
                int active = 0;
                int canAct = 0;
                for (TestBotPlayer player : players) {
                    if (player.isActivo()) {
                        active++;
                        if (cents(player.getStack()) > 0) {
                            canAct++;
                        }
                    }
                }
                if (active <= 1) {
                    return true;
                }
                int seat = nextActor(players, pending, cursor, wager, canAct);
                if (seat < 0) {
                    return false;
                }
                if (actions++ == limit) {
                    throw new IllegalStateException("Action limit on street " + dealer.getStreet()
                            + " with " + players[seat].getNickname() + " still to act");
                }
                TestBotPlayer player = players[seat];
                Bot bot = bots[seat];
                long previous = cents(player.getBet());
                long stack = cents(player.getStack());
                long toCall = wager - previous;
                long target = previous + Math.min(toCall, stack);
                BettingRoundState.Action action = toCall >= stack
                        ? BettingRoundState.Action.ALL_IN : BettingRoundState.Action.CHECK_CALL;
                int decision = bot.calculateBotDecision(active - 1);
                switch (decision) {
                    case GamePlayerController.FOLD:
                        if (toCall > 0) {
                            target = previous;
                            action = BettingRoundState.Action.FOLD;
                        }
                        break;
                    case GamePlayerController.BET:
                        if (stack > toCall && anotherSeatCanMatch(players, seat, wager)) {
                            if (!dealer.canPlayerRaise(player.getNickname())) {
                                // The live host rejects a raise without entitlement as a fold.
                                target = previous;
                                action = BettingRoundState.Action.FOLD;
                            } else {
                                long requested = cents(bot.getBetSize());
                                if (MoneyMath.compare(money(stack) * 0.75, money(requested - previous)) <= 0) {
                                    target = previous + stack;
                                    action = BettingRoundState.Action.ALL_IN;
                                } else {
                                    target = requested;
                                    action = BettingRoundState.Action.RAISE;
                                }
                            }
                        }
                        break;
                    case GamePlayerController.CHECK:
                        break;
                    default:
                        throw new IllegalStateException("Unknown bot action: " + decision);
                }
                BettingRoundState.Transition accepted = round.apply(player.getNickname(), action, target);
                if (!accepted.isAccepted()) {
                    throw new IllegalStateException("Rejected " + action + " by " + player.getNickname()
                            + " to " + target + " on street " + dealer.getStreet() + ": " + accepted.error());
                }
                round = accepted.state();
                dealer.setBettingRound(round);
                boolean folded = action == BettingRoundState.Action.FOLD;
                long paid = target - previous;
                if (folded) {
                    player.setActivo(false);
                } else if (paid > 0) {
                    player.commit(money(paid));
                    dealer.setPot(money(cents(dealer.getBote_total()) + paid));
                }
                boolean raised = target > wager;
                pending[seat] = false;
                if (raised) {
                    wager = round.toCallCents();
                    dealer.setCurrentBet(money(wager));
                    dealer.setLastRaise(money(round.lastFullRaiseSizeCents()));
                    dealer.setBetCount(dealer.getConta_bet() + 1);
                    dealer.recordRaise(player, accepted.isFullRaise());
                    for (int i = 0; i < players.length; i++) {
                        if (i != seat && players[i].isActivo() && cents(players[i].getStack()) > 0
                                && cents(players[i].getBet()) < wager) {
                            pending[i] = true;
                        }
                    }
                }
                if (dealer.getStreet() == Crupier.PREFLOP) {
                    if (raised) {
                        dealer.setLimpersCount(0);
                    } else if (paid > 0 && dealer.getConta_bet() == 0
                            && !player.getNickname().equals(dealer.getSb_nick())
                            && !player.getNickname().equals(dealer.getBb_nick())) {
                        dealer.setLimpersCount(dealer.getLimpersCount() + 1);
                    }
                }
                observer.accepted(seat, folded, raised, paid, toCall);
                cursor = (seat + 1) % players.length;
            }
        } finally {
            dealer.setBettingRound(null);
        }
    }

    private static int nextActor(TestBotPlayer[] players, boolean[] pending,
            int cursor, long wager, int canAct) {
        for (int offset = 0; offset < players.length; offset++) {
            int i = (cursor + offset) % players.length;
            TestBotPlayer player = players[i];
            if (player.isActivo() && cents(player.getStack()) > 0) {
                boolean owesChips = cents(player.getBet()) < wager;
                if (!pending[i] && owesChips) {
                    throw new IllegalStateException(player.getNickname() + " owes chips but has no pending action");
                }
                if (pending[i] && (canAct > 1 || owesChips)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean anotherSeatCanMatch(TestBotPlayer[] players, int actor, long wager) {
        for (int i = 0; i < players.length; i++) {
            TestBotPlayer player = players[i];
            if (i != actor && player.isActivo() && cents(player.getBet()) + cents(player.getStack()) > wager) {
                return true;
            }
        }
        return false;
    }

    static long cents(double amount) {
        return Math.round(amount * 100);
    }

    static double money(long cents) {
        return cents / 100.0;
    }
}
