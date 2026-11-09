/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.bot.harness;

import com.tonikelope.coronapoker.Bot;
import com.tonikelope.coronapoker.Crupier;
import com.tonikelope.coronapoker.bot.eval.BotEvaluator;
import com.tonikelope.coronapoker.bot.eval.MemoizedAlbertaEvaluator;
import com.tonikelope.coronapoker.core.game.GamePlayerController;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatorRulesTest {

    private final BotEvaluator evaluator = new MemoizedAlbertaEvaluator();

    @Test
    void shortStackWinsOnlyTheMainPotAndFoldedChipsStillPayOut() {
        TestBotPlayer[] players = {
            paidSeat("short", 10, 12, 25),
            paidSeat("kings", 100, 11, 24),
            paidSeat("queens", 100, 10, 23),
            paidSeat("folded", 20, 8, 21)
        };
        players[3].setActivo(false);
        TestDealer dealer = river(230);
        assertEquals(Set.of(0, 1), SimulatedShowdown.settle(players, dealer, evaluator));
        assertEquals(40, players[0].getStack());
        assertEquals(190, players[1].getStack());
        assertEquals(0, players[2].getStack());
        assertEquals(0, players[3].getStack());
    }

    @Test
    void refundsUncalledChipsWithoutAwardingThemAsAWin() {
        TestBotPlayer[] players = {
            paidSeat("short", 10, 12, 25),
            paidSeat("queens", 100, 10, 23),
            paidSeat("kings", 50, 11, 24)
        };
        assertEquals(Set.of(0, 2), SimulatedShowdown.settle(players, river(160), evaluator));
        assertEquals(30, players[0].getStack());
        assertEquals(50, players[1].getStack());
        assertEquals(80, players[2].getStack());
    }

    @Test
    void pfrCountsBothRaisersButCbetBelongsToTheLastInHeadsUp() {
        Bot.TRACKER_MEMORY.clear();
        try {
            var sim = new HeadsUpSimulator(42, 100, 2, evaluator);
            sim.setBotA(new ScriptedBot(sim.playerA(), 0, 6));
            sim.setBotB(new ScriptedBot(sim.playerB(), 1, 12));
            sim.playOneHand(true);
            assertEquals(1, sim.statsA().handsWithPreflopRaise);
            assertEquals(1, sim.statsB().handsWithPreflopRaise);
            assertEquals(0, sim.statsA().cbetOpportunities);
            assertEquals(1, sim.statsB().cbetOpportunities);
        } finally {
            Bot.TRACKER_MEMORY.clear();
        }
    }

    @Test
    void pfrCountsBothRaisersButCbetBelongsToTheLastInMultiway() {
        Bot.TRACKER_MEMORY.clear();
        try {
            var sim = new MultiwaySimulator(3, 42, 100, 2, evaluator);
            sim.setBot(0, new ScriptedBot(sim.player(0), 0, 6));
            sim.setBot(1, new ScriptedBot(sim.player(1), 1, 12));
            sim.setBot(2, new ScriptedBot(sim.player(2), -1, 0));
            sim.playOneHand();
            assertEquals(1, sim.stats(0).handsWithPreflopRaise);
            assertEquals(1, sim.stats(1).handsWithPreflopRaise);
            assertEquals(0, sim.stats(2).handsWithPreflopRaise);
            assertEquals(0, sim.stats(0).cbetOpportunities);
            assertEquals(1, sim.stats(1).cbetOpportunities);
        } finally {
            Bot.TRACKER_MEMORY.clear();
        }
    }

    @Test
    void shortRaiseDoesNotReopenBettingForPriorActors() {
        TestBotPlayer[] players = {new TestBotPlayer("opener", 100), new TestBotPlayer("caller", 100),
            new TestBotPlayer("short", 15), new TestBotPlayer("unacted", 100)};
        TestDealer dealer = preflop(players);
        Bot[] bots = {new ScriptedBot(players[0], 0, 10), new ScriptedBot(players[1], -1, 0),
            new ScriptedBot(players[2], 1, 15), new ScriptedBot(players[3], -1, 0)};
        for (Bot bot : bots) {
            bot.setContext(dealer, evaluator);
        }
        assertFalse(SimulatedBettingRound.play(dealer, players, bots, 0, 20, (seat, folded, raised, paid, toCall) -> {
            if (seat == 2 && raised) {
                assertEquals(8, dealer.getUltimo_raise());
                assertFalse(dealer.canPlayerRaise("opener"));
                assertFalse(dealer.canPlayerRaise("caller"));
                assertTrue(dealer.canPlayerRaise("unacted"));
            }
        }));
        assertEquals(15, dealer.getApuesta_actual());
        for (TestBotPlayer player : players) {
            assertEquals(15, player.getBet());
        }
    }

    @Test
    void rejectsASubminimumNonAllInRaise() {
        TestBotPlayer[] players = {new TestBotPlayer("opener", 100), new TestBotPlayer("other", 100)};
        TestDealer dealer = preflop(players);
        Bot[] bots = {new ScriptedBot(players[0], 0, 3), new ScriptedBot(players[1], -1, 0)};
        for (Bot bot : bots) {
            bot.setContext(dealer, evaluator);
        }
        var failure = assertThrows(IllegalStateException.class, () -> SimulatedBettingRound.play(
                dealer, players, bots, 0, 10, (seat, folded, raised, paid, toCall) -> {}));
        assertTrue(failure.getMessage().contains("ILLEGAL_RAISE"));
        assertEquals(2, dealer.getBote_total());
    }

    @Test
    void exhaustedActionBudgetFailsInsteadOfCompletingTheStreet() {
        TestBotPlayer[] players = {new TestBotPlayer("first", 100), new TestBotPlayer("other", 100)};
        TestDealer dealer = preflop(players);
        Bot[] bots = {new ScriptedBot(players[0], -1, 0), new ScriptedBot(players[1], -1, 0)};
        for (Bot bot : bots) {
            bot.setContext(dealer, evaluator);
        }
        var failure = assertThrows(IllegalStateException.class, () -> SimulatedBettingRound.play(
                dealer, players, bots, 0, 1, (seat, folded, raised, paid, toCall) -> {}));
        assertTrue(failure.getMessage().contains("Action limit"));
    }

    @Test
    void headsUpPreflopAllInDealsFiveDistinctCommunityCards() {
        Bot.TRACKER_MEMORY.clear();
        try {
            var sim = new HeadsUpSimulator(42, 2, 2, evaluator);
            sim.setBotA(new ScriptedBot(sim.playerA(), -1, 0));
            sim.setBotB(new ScriptedBot(sim.playerB(), -1, 0));
            sim.playOneHand(true);
            TestDealer dealer = sim.dealerView();
            Set<Integer> board = new HashSet<>();
            for (int i = 0; i < dealer.getBoardSize(); i++) {
                board.add(dealer.getBoardCardIndex(i));
            }
            assertEquals(5, board.size());
            assertEquals(4, sim.playerA().getStack() + sim.playerB().getStack());
        } finally {
            Bot.TRACKER_MEMORY.clear();
        }
    }

    @Test
    void repeatsTheSameUnevenStackSessionWithTheSameSeed() {
        assertEquals(playUnevenStackSession(), playUnevenStackSession());
    }

    private List<Double> playUnevenStackSession() {
        Bot.TRACKER_MEMORY.clear();
        try {
            var sim = new MultiwaySimulator(3, 20261109, 100, 2, evaluator);
            sim.setSeatDifficulties(Bot.Difficulty.HARD, Bot.Difficulty.MEDIUM, Bot.Difficulty.EASY);
            sim.player(0).setStack(37);
            sim.player(1).setStack(63);
            var results = new java.util.ArrayList<Double>();
            for (int hand = 0; hand < 12; hand++) {
                results.add(sim.playOneHand().pot);
                double total = 0;
                boolean busted = false;
                for (int seat = 0; seat < 3; seat++) {
                    double stack = sim.player(seat).getStack();
                    results.add(stack);
                    total += stack;
                    busted |= stack == 0;
                }
                assertEquals(200, total, 1e-9);
                if (busted) {
                    break;
                }
            }
            assertTrue(results.size() >= 8);
            return results;
        } finally {
            Bot.TRACKER_MEMORY.clear();
        }
    }

    private static TestBotPlayer paidSeat(String name, int chips, int first, int second) {
        var player = new TestBotPlayer(name, chips);
        player.setHoleCards(first, second);
        player.commit(chips);
        return player;
    }

    private static TestDealer river(int pot) {
        TestDealer dealer = new TestDealer();
        dealer.setStreet(Crupier.RIVER);
        dealer.setPot(pot);
        for (int card : new int[]{0, 16, 31, 46, 9}) {
            dealer.appendBoardCard(card);
        }
        return dealer;
    }

    private static TestDealer preflop(TestBotPlayer[] players) {
        TestDealer dealer = new TestDealer();
        dealer.setBlinds(1, 2);
        dealer.setStreet(Crupier.PREFLOP);
        dealer.setSeating(List.of(players));
        dealer.setRoles("button", "sb", players[players.length - 1].getNickname(), "utg");
        players[players.length - 1].commit(2);
        dealer.setCurrentBet(2);
        dealer.setLastRaise(2);
        dealer.setPot(2);
        return dealer;
    }

    private static final class ScriptedBot extends Bot {
        private final int raiseAt;
        private final double target;

        ScriptedBot(TestBotPlayer player, int raiseAt, double target) {
            super(player, new Random(42));
            this.raiseAt = raiseAt;
            this.target = target;
        }

        @Override
        public int calculateBotDecision(int opponentsCount) {
            return dealer().getStreet() == Crupier.PREFLOP && dealer().getConta_bet() == raiseAt
                    ? GamePlayerController.BET : GamePlayerController.CHECK;
        }

        @Override
        public double getBetSize() {
            return target;
        }
    }
}
