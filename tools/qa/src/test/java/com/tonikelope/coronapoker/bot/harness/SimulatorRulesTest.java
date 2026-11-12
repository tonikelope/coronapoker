/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.bot.harness;

import com.tonikelope.coronapoker.BetRules;
import com.tonikelope.coronapoker.Bot;
import com.tonikelope.coronapoker.Crupier;
import com.tonikelope.coronapoker.bot.eval.BotEvaluator;
import com.tonikelope.coronapoker.bot.eval.MemoizedAlbertaEvaluator;
import com.tonikelope.coronapoker.core.AnteMode;
import com.tonikelope.coronapoker.core.game.GamePlayerController;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("bot-regression")
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
    void tableAnteGoesToTheMainPotWithoutChangingSidePotEligibility() {
        TestBotPlayer[] players = {
            paidSeat("short", 2, 12, 25),
            paidSeat("kings", 100, 11, 24),
            paidSeat("ante-payer", 102, 10, 23)
        };
        TestDealer dealer = river(204);
        dealer.recordTableAnte(players[2], 2);
        assertEquals(100, dealer.matchedContribution(players[2]));
        assertEquals(Set.of(0, 1), SimulatedShowdown.settle(players, dealer, evaluator));
        assertEquals(8, players[0].getStack());
        assertEquals(196, players[1].getStack());
        assertEquals(0, players[2].getStack());
    }

    @Test
    void straddlerHasTheLastOptionAndTheAnteDoesNotIncreaseTheWager() {
        var sim = new MultiwaySimulator(6, 20261112, 100, 2, evaluator);
        int button = sim.currentButton();
        int straddler = (button + 3) % 6;
        int first = (straddler + 1) % 6;
        List<Integer> preflop = new ArrayList<>();
        sim.setForcedBets(AnteMode.BIG_BLIND, true);
        for (int seat = 0; seat < sim.numSeats(); seat++) {
            int index = seat;
            sim.setBot(seat, new Bot(sim.player(seat)) {
                @Override
                public int calculateBotDecision(int opponents) {
                    TestDealer dealer = sim.dealerView();
                    if (dealer.getStreet() == Crupier.PREFLOP) {
                        if (preflop.isEmpty()) {
                            assertEquals(sim.player(first).getNickname(), dealer.getStraddleUtgNick());
                            assertEquals(sim.player(straddler).getNickname(), dealer.getUtg_nick());
                            assertEquals(9, dealer.getBote_total());
                            assertEquals(4, dealer.getApuesta_actual());
                            assertEquals(6, BetRules.minRaiseTo(dealer.getApuesta_actual(),
                                    dealer.getUltimo_raise(), dealer.getCiega_grande()));
                            assertEquals(0, dealer.getConta_bet());
                        }
                        preflop.add(index);
                    }
                    return GamePlayerController.CHECK;
                }
            });
        }
        sim.playOneHand();
        assertEquals(List.of(first, (first + 1) % 6, (first + 2) % 6, (first + 3) % 6,
                (first + 4) % 6, straddler), preflop);
        assertEquals(0, sim.stats(straddler).handsWithPreflopRaise);
    }

    @Test
    void foldsKeepTableSeatsAndAreClearedAtTheNextHand() {
        TestBotPlayer player = new TestBotPlayer("button", 100);
        TestDealer dealer = new TestDealer();
        dealer.setSeating(List.of(player));
        player.fold();
        assertEquals(1, dealer.getJugadoresActivos());
        assertFalse(dealer.isContender(player));
        player.resetHandContribution();
        assertTrue(dealer.isContender(player));
    }

    @Test
    void higherStraightFlushWinsTheSimulatedPot() {
        TestBotPlayer[] players = {
            paidSeat("six-high", 100, card("2h"), card("8d")),
            paidSeat("seven-high", 100, card("7h"), card("9c"))
        };
        TestDealer dealer = new TestDealer();
        dealer.setStreet(Crupier.RIVER);
        dealer.setPot(200);
        for (String card : new String[]{"3h", "4h", "5h", "6h", "7c"}) {
            dealer.appendBoardCard(card(card));
        }

        assertEquals(Set.of(1), SimulatedShowdown.settle(players, dealer, evaluator));
        assertEquals(0, players[0].getStack());
        assertEquals(200, players[1].getStack());
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
    void aFlopLeadTakesAwayThePreflopAggressorsCbetOpportunity() {
        Bot.TRACKER_MEMORY.clear();
        try {
            var headsUp = new HeadsUpSimulator(42, 100, 2, evaluator);
            headsUp.setBotA(new ScriptedBot(headsUp.playerA(), 0, 6));
            headsUp.setBotB(new ScriptedBot(headsUp.playerB(), Crupier.FLOP, 0, 4));
            headsUp.playOneHand(true);
            assertEquals(1, headsUp.statsA().handsWithPreflopRaise);
            assertEquals(0, headsUp.statsA().cbetOpportunities);
            assertEquals(0, headsUp.statsB().cbetExecuted);

            var multiway = new MultiwaySimulator(3, 42, 100, 2, evaluator);
            multiway.setBot(0, new ScriptedBot(multiway.player(0), 0, 6));
            multiway.setBot(1, new ScriptedBot(multiway.player(1), Crupier.FLOP, 0, 4));
            multiway.setBot(2, new ScriptedBot(multiway.player(2), -1, 0));
            multiway.playOneHand();
            assertEquals(1, multiway.stats(0).handsWithPreflopRaise);
            assertEquals(0, multiway.stats(0).cbetOpportunities);
            assertEquals(0, multiway.stats(1).cbetExecuted);
        } finally {
            Bot.TRACKER_MEMORY.clear();
        }
    }

    @Test
    void aSingleSessionCannotEstablishADifficultyAdvantage() {
        assertFalse(SessionEstimate.of(new double[]{100}).clears(50, 2));
        assertTrue(SessionEstimate.of(new double[]{90, 100, 110}).clears(50, 2));
    }

    @Test
    void independentTablesKeepTheirOwnOpponentObservations() {
        TestDealer first = new TestDealer();
        TestDealer second = new TestDealer();
        first.trackerFor("shared-nickname").recordBlindDefense(true);
        assertTrue(first.getOpponentStats("shared-nickname").foldToBlindStealRate() > 0.5);
        assertEquals(0.5, second.trackerFor("shared-nickname").foldToBlindStealRate());
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
                assertEquals("opener", dealer.getLast_aggressor().getNickname());
                assertEquals("short", dealer.getLastWagerAggressor().getNickname());
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

    private static int card(String notation) {
        return new org.alberta.poker.Card(notation).getIndex();
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
        private final int street;
        private final int raiseAt;
        private final double target;

        ScriptedBot(TestBotPlayer player, int raiseAt, double target) {
            this(player, Crupier.PREFLOP, raiseAt, target);
        }

        ScriptedBot(TestBotPlayer player, int street, int raiseAt, double target) {
            super(player, new Random(42));
            this.street = street;
            this.raiseAt = raiseAt;
            this.target = target;
        }

        @Override
        public int calculateBotDecision(int opponentsCount) {
            return dealer().getStreet() == street && dealer().getConta_bet() == raiseAt
                    ? GamePlayerController.BET : GamePlayerController.CHECK;
        }

        @Override
        public double getBetSize() {
            return target;
        }
    }
}
