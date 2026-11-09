/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.bot.eval.BotEvaluator;
import com.tonikelope.coronapoker.bot.eval.Potential;
import com.tonikelope.coronapoker.bot.harness.TestBotPlayer;
import com.tonikelope.coronapoker.bot.harness.TestDealer;
import com.tonikelope.coronapoker.core.game.GameOpponentStats;
import com.tonikelope.coronapoker.core.game.GamePlayerController;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotDecisionAuditTest {

    @Test
    void callBreaksEvenAtOneThirdEquityWhenPotIs100AndCallIs50() {
        assertEquals(0, Bot.showdownEv(1.0 / 3, 100, 50), 1e-9);
        assertTrue(Bot.showdownEv(0.30, 100, 50) < 0);
    }

    @Test
    void botReadsItsDealersObservationsAndAllInCallsDoNotRaiseAggression() {
        Crupier dealer = new Crupier();
        TestBotPlayer hero = new TestBotPlayer("audit-hero", 100);
        TestBotPlayer villain = new TestBotPlayer("audit-villain", 100);
        Bot bot = new Bot(hero, new Random(1));
        bot.setContext(dealer, null);

        GameOpponentStats recorded = dealer.getOpponentStats(villain.getNickname());
        for (int hand = 1; hand <= 12; hand++) {
            recorded.recordHandPlayed();
            assertFalse(Crupier.recordOpponentAction(recorded, Crupier.PREFLOP, hand, 20, 1, 15));
            assertFalse(Crupier.recordOpponentAction(recorded, Crupier.FLOP, hand, 20, 0, 20));
        }
        assertSame(recorded, bot.opponentStats(villain));
        assertTrue(bot.opponentStats(villain).isStation());
        assertEquals(0, ((Bot.OpponentTracker) recorded).getAF());
        assertFalse(new Crupier().getOpponentStats(villain.getNickname()).hasEnoughData());

        for (int hand = 1; hand <= 3; hand++) {
            assertTrue(Crupier.recordOpponentAction(recorded, Crupier.PREFLOP, hand, 20, 1, 21));
            assertTrue(Crupier.recordOpponentAction(recorded, Crupier.FLOP, hand, 20, 0, 21));
        }
        assertFalse(recorded.isStation());
        assertEquals(0.25, ((Bot.OpponentTracker) recorded).getAF());
    }

    @Test
    void pocketFivesThatFlopASetCanRaiseOnAceKingFive() {
        TestBotPlayer hero = new TestBotPlayer("hero", 300);
        TestBotPlayer villain = new TestBotPlayer("villain", 260);
        TestDealer dealer = headsUp(hero, villain);
        hero.setHoleCards(3, 16);
        dealer.appendBoardCard(12);
        dealer.appendBoardCard(24);
        dealer.appendBoardCard(29);
        dealer.setStreet(Crupier.FLOP);
        dealer.setPot(120);
        dealer.setCurrentBet(40);
        dealer.setBetCount(1);
        dealer.setLastAggressor(villain);
        hero.commit(40);
        hero.setBet(0);
        villain.commit(80);
        villain.setBet(40);
        Bot bot = hardBot(hero, dealer);
        assertEquals(GamePlayerController.BET, bot.calculateBotDecision(1));
    }

    @Test
    void setMiningRequiresEnoughPotOrEffectiveStackToCoverTheCall() {
        TestBotPlayer hero = new TestBotPlayer("hero", 500);
        TestBotPlayer villain = new TestBotPlayer("villain", 500);
        TestDealer dealer = headsUp(hero, villain);
        hero.setHoleCards(3, 16);
        dealer.setStreet(Crupier.PREFLOP);
        dealer.setBetCount(2);
        dealer.setPot(45);
        dealer.setCurrentBet(30);
        villain.commit(30);
        hero.commit(15);
        Bot bot = hardBot(hero, dealer);
        assertEquals(GamePlayerController.CHECK, bot.calculateBotDecision(1));

        villain.setStack(25);
        assertEquals(GamePlayerController.FOLD, bot.calculateBotDecision(1));
    }

    @Test
    void positionFollowsLiveSeatsInClockwiseOrderIncludingAFoldedButton() {
        TestBotPlayer hero = new TestBotPlayer("hero", 100);
        TestBotPlayer villain = new TestBotPlayer("villain", 100);
        TestBotPlayer button = new TestBotPlayer("button", 100);
        TestDealer dealer = headsUp(hero, villain);
        dealer.setSeating(List.of(button, villain, hero));
        dealer.setRoles("button", "villain", "hero", "button");
        Bot bot = hardBot(hero, dealer);
        assertFalse(bot.isInPositionPostflop());

        button.setActivo(false);
        assertTrue(bot.isInPositionPostflop());

        button.setActivo(true);
        button.setStack(0);
        assertTrue(bot.isInPositionPostflop());
    }

    @ParameterizedTest
    @EnumSource(value = Bot.Difficulty.class, names = {"EASY", "MEDIUM"})
    void preflopMistakeUsesThePriceForTrash(Bot.Difficulty difficulty) {
        TestBotPlayer hero = new TestBotPlayer("hero", 300);
        TestBotPlayer villain = new TestBotPlayer("villain", 300);
        hero.setHoleCards(5, 13); // 72o
        TestDealer dealer = headsUp(hero, villain);
        dealer.setStreet(Crupier.PREFLOP);
        dealer.setBetCount(1);
        dealer.setPot(100);
        dealer.setCurrentBet(20);
        Bot bot = hardBot(hero, dealer);
        bot.configure(difficulty, Bot.Profile.TAG);
        bot.setRng(new Random(1) {
            @Override
            public double nextDouble() {
                return 0; // Always trigger the difficulty's mistake roll.
            }
        });
        assertEquals(GamePlayerController.CHECK, bot.calculateBotDecision(1));
        dealer.setCurrentBet(80);
        assertEquals(GamePlayerController.FOLD, bot.calculateBotDecision(1));

        dealer.setBetCount(3);
        hero.setHoleCards(8, 7); // T9s is speculative rather than pure trash.
        bot.resetBot();
        assertEquals(GamePlayerController.CHECK, bot.calculateBotDecision(1));

        hero.setHoleCards(5, 13);
        bot.resetBot();
        dealer.setBetCount(1);
        bot.configure(Bot.Difficulty.HARD, Bot.Profile.TAG);
        dealer.setCurrentBet(20);
        assertEquals(GamePlayerController.FOLD, bot.calculateBotDecision(1));
    }

    @Test
    void cannotCheckRaiseWhenAShortAllInDidNotReopenBetting() {
        TestBotPlayer hero = new TestBotPlayer("hero", 300);
        TestBotPlayer villain = new TestBotPlayer("villain", 300);
        TestDealer dealer = postflopFacingBet(hero, villain, 30);
        dealer.setRaiseAllowed(false);
        Bot bot = hardBot(hero, dealer);
        bot.setContext(dealer, new FixedOdds(0.94));
        assertEquals(GamePlayerController.CHECK, bot.calculateBotDecision(1));
    }

    @Test
    void positiveRaiseEvDoesNotJustifyRaisingWhenCallingEarnsMore() {
        TestBotPlayer hero = new TestBotPlayer("hero", 300);
        TestBotPlayer villain = new TestBotPlayer("station-ev-regression", 300);
        TestDealer dealer = postflopFacingBet(hero, villain, 30);
        dealer.setStreet(Crupier.RIVER);
        dealer.appendBoardCard(16);
        dealer.appendBoardCard(33);
        Bot bot = hardBot(hero, dealer);
        bot.setContext(dealer, new FixedOdds(0.47));
        forceAggressiveRolls(bot);
        Bot.OpponentTracker stats = new Bot.OpponentTracker();
        for (int i = 0; i < 4; i++) {
            stats.recordPostFlopCall();
        }
        Bot.TRACKER_MEMORY.put(villain.getNickname(), stats);
        try {
            assertEquals(GamePlayerController.CHECK, bot.calculateBotDecision(1));
        } finally {
            Bot.TRACKER_MEMORY.remove(villain.getNickname());
        }
    }

    @Test
    void riverValueBetMustImproveOnChecking() {
        TestBotPlayer hero = new TestBotPlayer("hero", 300);
        TestBotPlayer villain = new TestBotPlayer("station-check-ev", 300);
        TestDealer dealer = postflopFacingBet(hero, villain, 0);
        dealer.setRoles("station-check-ev", "station-check-ev", "hero", "hero");
        dealer.setBetCount(0);
        Bot bot = hardBot(hero, dealer);
        bot.setContext(dealer, new FixedOdds(0.90));
        bot.calculateBotDecision(1);

        dealer.setStreet(Crupier.RIVER);
        dealer.appendBoardCard(16);
        dealer.appendBoardCard(33);
        bot.setContext(dealer, new FixedOdds(0.65));
        Bot.OpponentTracker stats = new Bot.OpponentTracker();
        for (int i = 0; i < 3; i++) {
            stats.recordPostFlopCall();
        }
        Bot.TRACKER_MEMORY.put(villain.getNickname(), stats);
        try {
            // The scare-card and position adjustments leave 50% equity.
            // A caller who never folds makes betting and checking equivalent.
            assertEquals(GamePlayerController.CHECK, bot.calculateBotDecision(1));
        } finally {
            Bot.TRACKER_MEMORY.remove(villain.getNickname());
        }
    }

    @Test
    void willNotPayForAFloatThatCannotEarnBackTheFlopCall() {
        TestBotPlayer hero = new TestBotPlayer("hero", 300);
        TestBotPlayer villain = new TestBotPlayer("villain", 300);
        TestDealer dealer = postflopFacingBet(hero, villain, 60);
        Bot bot = hardBot(hero, dealer);
        bot.setContext(dealer, new FixedOdds(0.26));
        forceAggressiveRolls(bot);
        assertEquals(GamePlayerController.FOLD, bot.calculateBotDecision(1));
    }

    @Test
    void anAffordableFloatCanFollowThroughWhenTheTurnIsChecked() {
        TestBotPlayer hero = new TestBotPlayer("hero", 300);
        TestBotPlayer villain = new TestBotPlayer("villain", 300);
        TestDealer dealer = postflopFacingBet(hero, villain, 50);
        dealer.setRaiseAllowed(false);
        Bot bot = hardBot(hero, dealer);
        bot.setContext(dealer, new FixedOdds(0.40));
        forceAggressiveRolls(bot);
        assertEquals(GamePlayerController.CHECK, bot.calculateBotDecision(1));

        dealer.setStreet(Crupier.TURN);
        dealer.setRaiseAllowed(true);
        dealer.appendBoardCard(16);
        dealer.setBetCount(0);
        dealer.setCurrentBet(0);
        dealer.setPot(150);
        hero.commit(50);
        hero.setBet(0);
        villain.setBet(0);
        assertEquals(GamePlayerController.BET, bot.calculateBotDecision(1));
    }

    @Test
    void prefersAnImmediateRaiseWhenItOutearnsTheFloat() {
        TestBotPlayer hero = new TestBotPlayer("hero", 300);
        TestBotPlayer villain = new TestBotPlayer("villain", 300);
        TestDealer dealer = postflopFacingBet(hero, villain, 50);
        Bot bot = hardBot(hero, dealer);
        bot.setContext(dealer, new FixedOdds(0.40));
        forceAggressiveRolls(bot);
        assertEquals(GamePlayerController.BET, bot.calculateBotDecision(1));
    }

    @Test
    void squeezeCanUseFavorableEvWithoutRequiringImpossibleFoldEquity() {
        TestBotPlayer hero = new TestBotPlayer("hero", 300);
        TestBotPlayer villain = new TestBotPlayer("villain", 300);
        TestDealer dealer = postflopFacingBet(hero, villain, 20);
        TestBotPlayer second = new TestBotPlayer("second", 300);
        TestBotPlayer third = new TestBotPlayer("third", 300);
        for (TestBotPlayer player : List.of(second, third)) {
            player.commit(60);
            player.setBet(20);
        }
        dealer.setSeating(List.of(hero, villain, second, third));
        dealer.setPot(220);
        Bot bot = hardBot(hero, dealer);
        bot.setContext(dealer, new FixedOdds(0.88));
        forceAggressiveRolls(bot);
        assertEquals(GamePlayerController.BET, bot.calculateBotDecision(3));

        dealer.setRaiseAllowed(false);
        assertEquals(GamePlayerController.CHECK, bot.calculateBotDecision(3));
    }

    @Test
    void aRoyalFlushOnTheBoardSplitsBySeatCount() {
        var odds = Bot.EVALUATOR.showdownOdds(13, 26, new int[]{8, 9, 10, 11, 12});
        assertEquals(1, odds.tie());
        assertEquals(0.5, odds.equity(1));
        assertEquals(0.2, odds.equity(4));
    }

    private static TestDealer postflopFacingBet(TestBotPlayer hero, TestBotPlayer villain, int cost) {
        hero.setHoleCards(11, 23);
        TestDealer dealer = headsUp(hero, villain);
        dealer.setStreet(Crupier.FLOP);
        dealer.appendBoardCard(0);
        dealer.appendBoardCard(17);
        dealer.appendBoardCard(34);
        dealer.setPot(100);
        dealer.setCurrentBet(cost);
        dealer.setBetCount(1);
        double previous = (100 - cost) / 2.0;
        hero.commit(previous);
        hero.setBet(0);
        villain.commit(previous + cost);
        villain.setBet(cost);
        return dealer;
    }

    private static void forceAggressiveRolls(Bot bot) {
        bot.setRng(new Random(1) {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        });
    }

    private record FixedOdds(double win) implements BotEvaluator {
        @Override
        public ShowdownOdds showdownOdds(int hole1, int hole2, int[] board) {
            return new ShowdownOdds(win, 0);
        }

        @Override
        public double handStrength(int hole1, int hole2, int[] board) {
            return win;
        }

        @Override
        public double handStrengthVsRange(int hole1, int hole2, int[] board, double[][] rangeWeights) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Potential potential(int hole1, int hole2, int[] board, boolean fullLookahead) {
            return Potential.ZERO;
        }

        @Override
        public Potential potentialVsRange(int hole1, int hole2, int[] board,
                double[][] rangeWeights, boolean fullLookahead) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int rankHand(int[] cards) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOnePair(int[] cards) {
            return false;
        }

        @Override
        public int compareHands(int[] cards1, int[] cards2) {
            throw new UnsupportedOperationException();
        }
    }

    private static TestDealer headsUp(TestBotPlayer hero, TestBotPlayer villain) {
        TestDealer dealer = new TestDealer();
        dealer.setBlinds(1, 2);
        dealer.setRoles(hero.getNickname(), hero.getNickname(), villain.getNickname(), villain.getNickname());
        dealer.setSeating(List.of(hero, villain));
        return dealer;
    }

    private static Bot hardBot(TestBotPlayer hero, TestDealer dealer) {
        Bot bot = new Bot(hero, new Random(1) {
            @Override
            public int nextInt(int bound) {
                return bound - 1;
            }
        });
        bot.setContext(dealer, null);
        bot.configure(Bot.Difficulty.HARD, Bot.Profile.TAG);
        if (hero.getHoleCard1Index() >= 0) {
            bot.resetBot();
        }
        return bot;
    }
}
