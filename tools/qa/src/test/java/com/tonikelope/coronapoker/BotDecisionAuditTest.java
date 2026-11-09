/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.bot.harness.TestBotPlayer;
import com.tonikelope.coronapoker.bot.harness.TestDealer;
import com.tonikelope.coronapoker.core.game.GameOpponentStats;
import com.tonikelope.coronapoker.core.game.GamePlayerController;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
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
        villain.setBet(30);
        hero.setBet(15);
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
