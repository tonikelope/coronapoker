/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.bot.eval.BotEvaluator;
import com.tonikelope.coronapoker.bot.eval.Potential;
import com.tonikelope.coronapoker.bot.harness.TestBotPlayer;
import com.tonikelope.coronapoker.bot.harness.TestDealer;
import com.tonikelope.coronapoker.core.game.GameOpponentStats;
import com.tonikelope.coronapoker.core.game.GamePlayerController;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("bot-regression")
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
    void smallPairsCanSetMineAtAFavorablePriceAtEveryTableSize() {
        for (int players = 2; players <= 6; players++) {
            TestBotPlayer hero = new TestBotPlayer("hero", 500);
            TestBotPlayer villain = new TestBotPlayer("villain", 500);
            hero.setHoleCards(3, 16); // 55
            hero.commit(15);
            villain.commit(30);

            List<TestBotPlayer> seats = new ArrayList<>(List.of(hero, villain));
            for (int seat = 2; seat < players; seat++) {
                TestBotPlayer extra = new TestBotPlayer("extra-" + seat, 500);
                extra.commit(15);
                seats.add(extra);
            }

            TestDealer dealer = new TestDealer();
            dealer.setBlinds(1, 2);
            dealer.setRoles("villain", "villain", players > 2 ? "extra-2" : "hero", "villain");
            dealer.setSeating(seats);
            dealer.setStreet(Crupier.PREFLOP);
            dealer.setBetCount(2);
            dealer.setPot(45 + (players - 2) * 15);
            dealer.setCurrentBet(30);
            dealer.setLastRaise(15);

            Bot bot = hardBot(hero, dealer);
            assertEquals(GamePlayerController.CHECK, bot.calculateBotDecision(players - 1),
                    "favorable set-mine should remain available with " + players + " live players");
            hero.setStack(40);
            assertEquals(GamePlayerController.FOLD, bot.calculateBotDecision(players - 1),
                    "unfavorable set-mine should fold with " + players + " live players");
        }
    }

    @Test
    void nitOpensPremiumHandsFromTheSmallBlind() {
        int[][] holdings = {{12, 25}, {11, 24}, {10, 23}, {12, 11}, {12, 24}};
        String[] labels = {"AA", "KK", "QQ", "AKs", "AKo"};
        for (int i = 0; i < holdings.length; i++) {
            TestBotPlayer hero = new TestBotPlayer("hero", 100);
            TestBotPlayer villain = new TestBotPlayer("villain", 100);
            hero.setHoleCards(holdings[i][0], holdings[i][1]);
            hero.commit(1);
            villain.commit(2);
            TestDealer dealer = headsUp(hero, villain);
            dealer.setStreet(Crupier.PREFLOP);
            dealer.setPot(3);
            dealer.setCurrentBet(2);
            dealer.setLastRaise(2);

            Bot bot = deterministicBot(hero, dealer, Bot.Difficulty.HARD, Bot.Profile.NIT, true);
            assertEquals(GamePlayerController.BET, bot.calculateBotDecision(1), labels[i]);

            TestBotPlayer limper = new TestBotPlayer("limper", 100);
            limper.commit(2);
            dealer.setRoles("limper", "hero", "villain", "limper");
            dealer.setSeating(List.of(limper, hero, villain));
            dealer.setLimpersCount(1);
            dealer.setPot(5);
            bot.resetBot();
            assertEquals(GamePlayerController.BET, bot.calculateBotDecision(2), labels[i] + " vs limper");
        }
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
        TestBotPlayer villain = new TestBotPlayer("float-target", 300);
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
        Bot.OpponentTracker stats = new Bot.OpponentTracker();
        for (int i = 0; i < 20; i++) {
            stats.recordPostflopDecision(Crupier.TURN, true, false, false, true);
        }
        Bot.TRACKER_MEMORY.put(villain.getNickname(), stats);
        try {
            assertEquals(GamePlayerController.BET, bot.calculateBotDecision(1));
        } finally {
            Bot.TRACKER_MEMORY.remove(villain.getNickname());
        }
    }

    @Test
    void prefersAnImmediateRaiseWhenItOutearnsTheFloat() {
        TestBotPlayer hero = new TestBotPlayer("hero", 300);
        TestBotPlayer villain = new TestBotPlayer("raise-fold-target", 300);
        TestDealer dealer = postflopFacingBet(hero, villain, 50);
        Bot bot = hardBot(hero, dealer);
        bot.setContext(dealer, new FixedOdds(0.34, 0, new Potential(0.19, 0.08)));
        forceAggressiveRolls(bot);
        Bot.OpponentTracker stats = new Bot.OpponentTracker();
        for (int i = 0; i < 20; i++) {
            stats.recordPostflopDecision(Crupier.FLOP, true, false, false, true);
            stats.recordPostflopDecision(Crupier.FLOP, true, true, false, false);
        }
        Bot.TRACKER_MEMORY.put(villain.getNickname(), stats);
        try {
            // Folds to an opening bet are not evidence that a raise will work.
            assertEquals(GamePlayerController.CHECK, bot.calculateBotDecision(1));
            for (int i = 0; i < 50; i++) {
                stats.recordPostflopDecision(Crupier.FLOP, true, true, false, true);
            }
            assertEquals(GamePlayerController.BET, bot.calculateBotDecision(1));
        } finally {
            Bot.TRACKER_MEMORY.remove(villain.getNickname());
        }
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

    @Test
    void certainProfitableRiverChopCannotBeFoldedByPersonality() {
        TestBotPlayer hero = new TestBotPlayer("hero", 700);
        TestBotPlayer villain = new TestBotPlayer("villain", 700);
        hero.setHoleCards(13, 26);
        hero.commit(100);
        hero.setBet(0);
        villain.commit(400);
        villain.setBet(300);

        TestDealer dealer = headsUp(hero, villain);
        dealer.setBlinds(5, 10);
        dealer.setStreet(Crupier.RIVER);
        for (int card : new int[]{8, 9, 10, 11, 12}) {
            dealer.appendBoardCard(card);
        }
        dealer.setPot(500);
        dealer.setCurrentBet(300);
        dealer.setLastRaise(300);
        dealer.setBetCount(1);
        dealer.setLastAggressor(villain);

        Bot bot = deterministicBot(hero, dealer, Bot.Difficulty.HARD, Bot.Profile.NIT, true);
        assertEquals(GamePlayerController.CHECK, bot.calculateBotDecision(1));

        // The bot can only call 80. A short all-in plays the main pot and
        // the bettor's unmatched chips are outside the bot's eligible pots.
        hero.setStack(80);
        TestBotPlayer shortStack = new TestBotPlayer("short", 60);
        shortStack.commit(60);
        shortStack.setBet(0);
        dealer.setSeating(List.of(hero, villain, shortStack));
        dealer.setPot(560);
        dealer.setBlinds(1, 2);
        bot.resetBot();
        assertEquals(GamePlayerController.CHECK, bot.calculateBotDecision(2));
    }

    @Test
    void unbeatableRiverHandThatCanTieStillCallsAnOverbet() {
        TestBotPlayer hero = new TestBotPlayer("hero", 1000);
        TestBotPlayer villain = new TestBotPlayer("villain", 1000);
        TestDealer dealer = postflopFacingBet(hero, villain, 40);
        villain.commit(260);
        villain.setBet(300);
        dealer.setPot(360);
        dealer.setCurrentBet(300);
        dealer.setStreet(Crupier.RIVER);
        dealer.appendBoardCard(16);
        dealer.appendBoardCard(33);
        dealer.setRaiseAllowed(false);
        Bot bot = deterministicBot(hero, dealer, Bot.Difficulty.HARD, Bot.Profile.NIT, true);
        bot.setContext(dealer, new FixedOdds(0.02, 0.98, Potential.ZERO));
        assertEquals(GamePlayerController.CHECK, bot.calculateBotDecision(1));
    }

    @Test
    void drawPotentialCannotPayForANegativeEvMultiwayAllIn() {
        TestBotPlayer hero = new TestBotPlayer("hero", 100);
        TestBotPlayer villain = new TestBotPlayer("villain", 300);
        TestDealer dealer = postflopFacingBet(hero, villain, 50);
        hero.setStack(50);
        TestBotPlayer[] others = {new TestBotPlayer("second", 300),
            new TestBotPlayer("third", 300), new TestBotPlayer("fourth", 300)};
        for (TestBotPlayer player : others) {
            player.commit(75);
            player.setBet(50);
        }
        dealer.setSeating(List.of(hero, villain, others[0], others[1], others[2]));
        dealer.setPot(325);
        Bot bot = hardBot(hero, dealer);
        bot.setContext(dealer, new FixedOdds(0.05, 0, new Potential(0.35, 0.05)));
        assertEquals(GamePlayerController.FOLD, bot.calculateBotDecision(4));
    }

    @Test
    void recreationalTiltChangesTheProfileInTheSameHand() throws ReflectiveOperationException {
        TestBotPlayer hero = new TestBotPlayer("hero", 100);
        TestBotPlayer villain = new TestBotPlayer("villain", 100);
        hero.setHoleCards(12, 25);
        TestDealer dealer = headsUp(hero, villain);
        Bot bot = deterministicBot(hero, dealer, Bot.Difficulty.EASY, Bot.Profile.STATION, false);
        bot.recordHandResult(false);
        bot.recordHandResult(false);

        bot.setRng(fixedIntRandom(false));
        bot.resetBot();
        assertEquals(true, privateField(bot, "onTilt"));
        assertEquals(Bot.Profile.LAG, privateField(bot, "currentProfile"));

        bot.setRng(fixedIntRandom(true));
        bot.resetBot();
        assertEquals(false, privateField(bot, "onTilt"));
        assertEquals(Bot.Profile.STATION, privateField(bot, "currentProfile"));
    }

    @Test
    void hardSharkRespectsNegativeCallEvAgainstAKnownNit() {
        TestBotPlayer hero = new TestBotPlayer("hero", 300);
        TestBotPlayer villain = new TestBotPlayer("nit-mdf-regression", 300);
        TestDealer dealer = postflopFacingBet(hero, villain, 30);
        dealer.setStreet(Crupier.RIVER);
        dealer.appendBoardCard(16);
        dealer.appendBoardCard(33);
        dealer.setRaiseAllowed(false);
        dealer.setLastAggressor(villain);

        Bot bot = hardBot(hero, dealer);
        bot.setContext(dealer, new FixedOdds(0.35));
        Bot.OpponentTracker stats = new Bot.OpponentTracker();
        for (int hand = 0; hand < 12; hand++) {
            stats.recordHandPlayed();
        }
        Bot.TRACKER_MEMORY.put(villain.getNickname(), stats);
        try {
            assertTrue(stats.isNit());
            assertEquals(GamePlayerController.FOLD, bot.calculateBotDecision(1));
        } finally {
            Bot.TRACKER_MEMORY.remove(villain.getNickname());
        }
    }

    @Test
    void opponentReadsUseStreetAndTheActionThePlayerFaced() {
        Bot.OpponentTracker stats = new Bot.OpponentTracker();
        stats.recordPostflopDecision(Crupier.FLOP, true, false, false, true);
        stats.recordPostflopDecision(Crupier.FLOP, true, false, false, false);
        stats.recordPostflopDecision(Crupier.FLOP, true, false, true, false);
        stats.recordPostflopDecision(Crupier.FLOP, true, true, false, false);
        stats.recordPostflopDecision(Crupier.TURN, false, false, true, false);
        stats.recordPostflopDecision(Crupier.TURN, false, false, false, false);
        assertEquals(1.0 / 15, stats.foldToBetRate(Crupier.FLOP, false, 0), 1e-12);
        assertEquals(0, stats.foldToBetRate(Crupier.FLOP, true, 0));
        assertEquals(0, stats.foldToBetRate(Crupier.TURN, false, 0));
        assertEquals(1.0 / 8, stats.openingBetRate(Crupier.TURN, 0), 1e-12);
        assertEquals(0, stats.openingBetRate(Crupier.FLOP, 0));
        assertEquals((12 * 0.35 + 0.30) / 12.90, stats.foldToBetRate(Crupier.RIVER, false, 0.35), 1e-12);
    }

    @Test
    void hardDistinguishesASeldomSeenRiverBetFromAFrequentOne() {
        TestBotPlayer hero = new TestBotPlayer("hero", 300);
        TestBotPlayer villain = new TestBotPlayer("river-bettor", 300);
        TestDealer dealer = postflopFacingBet(hero, villain, 60);
        dealer.setStreet(Crupier.RIVER);
        dealer.appendBoardCard(16);
        dealer.appendBoardCard(33);
        dealer.setRaiseAllowed(false);
        dealer.setLastAggressor(villain);
        Bot bot = hardBot(hero, dealer);
        bot.setContext(dealer, new FixedOdds(0.42));
        Bot.OpponentTracker stats = new Bot.OpponentTracker();
        Bot.TRACKER_MEMORY.put(villain.getNickname(), stats);
        try {
            assertEquals(GamePlayerController.CHECK, bot.calculateBotDecision(1));
            for (int i = 0; i < 24; i++) {
                stats.recordPostflopDecision(Crupier.RIVER, false, false, i < 3, false);
            }
            assertEquals(GamePlayerController.FOLD, bot.calculateBotDecision(1));
        } finally {
            Bot.TRACKER_MEMORY.remove(villain.getNickname());
        }
    }

    @Test
    void hardPricesTheBettorsVoluntaryPreflopInvolvement() {
        TestBotPlayer hero = new TestBotPlayer("hero", 300);
        TestBotPlayer villain = new TestBotPlayer("range-bettor", 300);
        TestDealer dealer = postflopFacingBet(hero, villain, 60);
        dealer.setStreet(Crupier.RIVER);
        dealer.appendBoardCard(16);
        dealer.appendBoardCard(33);
        dealer.setRaiseAllowed(false);
        dealer.setLastAggressor(villain);
        Bot bot = hardBot(hero, dealer);
        bot.setContext(dealer, new FixedOdds(0.42, 0, Potential.ZERO, 0.20));
        Bot.OpponentTracker stats = new Bot.OpponentTracker();
        Bot.TRACKER_MEMORY.put(villain.getNickname(), stats);
        try {
            assertEquals(GamePlayerController.CHECK, bot.calculateBotDecision(1));
            stats.recordVPIP(1);
            assertEquals(GamePlayerController.FOLD, bot.calculateBotDecision(1));
            stats.recordHandPlayed();
            assertEquals(GamePlayerController.CHECK, bot.calculateBotDecision(1));
        } finally {
            Bot.TRACKER_MEMORY.remove(villain.getNickname());
        }
    }

    @Test
    void hardBluffsTheNutFlushBlockerIntoFoldsAndChecksAgainstACaller() {
        TestBotPlayer hero = new TestBotPlayer("hero", 300);
        TestBotPlayer villain = new TestBotPlayer("blocker-target", 300);
        hero.setHoleCards(12, 28);
        hero.commit(50);
        villain.commit(50);
        hero.setBet(0);
        villain.setBet(0);
        TestDealer dealer = headsUp(hero, villain);
        dealer.setStreet(Crupier.RIVER);
        dealer.setPot(100);
        for (int card : new int[]{8, 9, 10, 36, 13}) {
            dealer.appendBoardCard(card);
        }
        Bot bot = hardBot(hero, dealer);
        bot.setContext(dealer, new FixedOdds(0.20));
        forceAggressiveRolls(bot);
        Bot.OpponentTracker folds = new Bot.OpponentTracker();
        Bot.OpponentTracker calls = new Bot.OpponentTracker();
        for (int i = 0; i < 20; i++) {
            folds.recordPostflopDecision(Crupier.RIVER, true, false, false, true);
            calls.recordPostFlopCall();
            calls.recordPostflopDecision(Crupier.RIVER, true, false, false, false);
        }
        try {
            Bot.TRACKER_MEMORY.put(villain.getNickname(), folds);
            assertEquals(GamePlayerController.BET, bot.calculateBotDecision(1));
            Bot.TRACKER_MEMORY.put(villain.getNickname(), calls);
            assertEquals(GamePlayerController.CHECK, bot.calculateBotDecision(1));
        } finally {
            Bot.TRACKER_MEMORY.remove(villain.getNickname());
        }
    }
    @Test
    void hardStealsTheSmallBlindOnlyAfterEnoughFoldsAtAProfitablePrice() {
        TestBotPlayer hero = new TestBotPlayer("hero", 200);
        TestBotPlayer villain = new TestBotPlayer("blind-defense", 200);
        hero.setHoleCards(0, 18);
        hero.commit(1);
        villain.commit(2);
        TestDealer dealer = headsUp(hero, villain);
        dealer.setStreet(Crupier.PREFLOP);
        dealer.setPot(3);
        dealer.setCurrentBet(2);
        dealer.setLastRaise(2);
        Bot bot = deterministicBot(hero, dealer, Bot.Difficulty.HARD, Bot.Profile.TAG, true);
        Bot.OpponentTracker stats = new Bot.OpponentTracker();
        Bot.TRACKER_MEMORY.put(villain.getNickname(), stats);
        try {
            stats.recordBlindDefense(true);
            assertEquals(GamePlayerController.FOLD, bot.calculateBotDecision(1));
            for (int i = 0; i < 24; i++) {
                stats.recordBlindDefense(true);
            }
            assertEquals(GamePlayerController.BET, bot.calculateBotDecision(1));
            assertEquals(5, bot.getBetSize());
            dealer.setRaiseAllowed(false);
            assertEquals(GamePlayerController.FOLD, bot.calculateBotDecision(1));
        } finally {
            Bot.TRACKER_MEMORY.remove(villain.getNickname());
        }
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

    private static Random fixedIntRandom(boolean high) {
        return new Random(1) {
            @Override
            public int nextInt(int bound) {
                return high ? bound - 1 : 0;
            }
        };
    }

    private static Object privateField(Bot bot, String name) throws ReflectiveOperationException {
        Field field = Bot.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(bot);
    }

    private record FixedOdds(double win, double tie, Potential draw, double rangeStrength) implements BotEvaluator {
        private FixedOdds(double win) {
            this(win, 0, Potential.ZERO);
        }

        private FixedOdds(double win, double tie, Potential draw) {
            this(win, tie, draw, win + tie / 2);
        }

        @Override
        public ShowdownOdds showdownOdds(int hole1, int hole2, int[] board) {
            return new ShowdownOdds(win, tie);
        }

        @Override
        public double handStrength(int hole1, int hole2, int[] board) {
            return win + tie / 2;
        }

        @Override
        public double handStrengthVsRange(int hole1, int hole2, int[] board, double[][] rangeWeights) {
            return rangeStrength;
        }

        @Override
        public Potential potential(int hole1, int hole2, int[] board, boolean fullLookahead) {
            return draw;
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
        return deterministicBot(hero, dealer, Bot.Difficulty.HARD, Bot.Profile.TAG, true);
    }

    private static Bot deterministicBot(TestBotPlayer hero, TestDealer dealer,
            Bot.Difficulty difficulty, Bot.Profile profile, boolean highRolls) {
        Bot bot = new Bot(hero, fixedIntRandom(highRolls));
        bot.setContext(dealer, null);
        bot.configure(difficulty, profile);
        if (hero.getHoleCard1Index() >= 0) {
            bot.resetBot();
        }
        return bot;
    }
}
