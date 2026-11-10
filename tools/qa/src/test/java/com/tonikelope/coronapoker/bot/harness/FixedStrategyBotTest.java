/* Copyright (C) 2026 tonikelope, GPLv3 or later. */
package com.tonikelope.coronapoker.bot.harness;

import com.tonikelope.coronapoker.Crupier;
import com.tonikelope.coronapoker.core.game.GamePlayerController;
import java.util.List;
import org.alberta.poker.Card;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("bot-regression")
class FixedStrategyBotTest {

    @Test
    void tagRangeContainsApproximatelyTwentyFivePercentOfStartingCombos() {
        int combos = 0;
        for (int first = 0; first < 52; first++) {
            for (int second = first + 1; second < 52; second++) {
                int rank1 = first % 13;
                int rank2 = second % 13;
                if (FixedStrategyBot.isTop25(rank1 == rank2, Math.max(rank1, rank2),
                        Math.min(rank1, rank2), first / 13 == second / 13)) {
                    combos++;
                }
            }
        }
        assertEquals(330, combos);
        assertEquals(0.25, combos / 1326.0, 0.002);
    }

    @Test
    void tagRaisesAkInAnUnopenedBlindPot() {
        TestBotPlayer hero = new TestBotPlayer("tag", 100);
        TestBotPlayer villain = new TestBotPlayer("villain", 100);
        hero.setHoleCards(card("Ah"), card("Kd"));
        hero.commit(1);
        villain.commit(2);
        TestDealer dealer = preflop(hero, villain, 0, 2, 3);

        FixedStrategyBot bot = new FixedStrategyBot(hero, FixedStrategyBot.Strategy.TAG, 1);
        bot.setContext(dealer, null);
        assertEquals(GamePlayerController.BET, bot.calculateBotDecision(1));
    }

    @Test
    void tagThreeBetDefenseActuallyMixesCallsAndFolds() {
        int calls = 0;
        int folds = 0;
        for (int seed = 0; seed < 100; seed++) {
            TestBotPlayer hero = new TestBotPlayer("tag", 500);
            TestBotPlayer villain = new TestBotPlayer("villain", 500);
            hero.setHoleCards(card("Ah"), card("Qd"));
            hero.commit(6);
            villain.commit(18);
            TestDealer dealer = preflop(hero, villain, 2, 18, 24);
            FixedStrategyBot bot = new FixedStrategyBot(hero, FixedStrategyBot.Strategy.TAG, seed);
            bot.setContext(dealer, null);
            int decision = bot.calculateBotDecision(1);
            calls += decision == GamePlayerController.CHECK ? 1 : 0;
            folds += decision == GamePlayerController.FOLD ? 1 : 0;
        }
        assertTrue(calls >= 35 && calls <= 65, "calls=" + calls);
        assertEquals(100, calls + folds);
    }

    @Test
    void tagContinuesWithSetsFlushesAndStraights() {
        assertCallsMadeHand("6c", "6d", new String[]{"Ac", "Kd", "6h"});
        assertCallsMadeHand("Ah", "Qh", new String[]{"Kh", "8h", "2h"});
        assertCallsMadeHand("Kc", "Qd", new String[]{"Jh", "Ts", "9c"});
    }

    private static void assertCallsMadeHand(String first, String second, String[] board) {
        TestBotPlayer hero = new TestBotPlayer("tag", 300);
        TestBotPlayer villain = new TestBotPlayer("villain", 300);
        hero.setHoleCards(card(first), card(second));
        villain.commit(20);
        TestDealer dealer = new TestDealer();
        dealer.setBlinds(1, 2);
        dealer.setRoles("villain", "villain", "tag", "villain");
        dealer.setSeating(List.of(hero, villain));
        dealer.setStreet(Crupier.FLOP);
        dealer.setPot(100);
        dealer.setCurrentBet(20);
        dealer.setLastRaise(20);
        dealer.setBetCount(1);
        dealer.setLastAggressor(villain);
        for (String notation : board) {
            dealer.appendBoardCard(card(notation));
        }

        FixedStrategyBot bot = new FixedStrategyBot(hero, FixedStrategyBot.Strategy.TAG, 1);
        bot.setContext(dealer, null);
        assertEquals(GamePlayerController.CHECK, bot.calculateBotDecision(1), first + second);
    }

    private static TestDealer preflop(TestBotPlayer hero, TestBotPlayer villain,
            int betCount, double currentBet, double pot) {
        TestDealer dealer = new TestDealer();
        dealer.setBlinds(1, 2);
        dealer.setRoles(hero.getNickname(), hero.getNickname(), villain.getNickname(), villain.getNickname());
        dealer.setSeating(List.of(hero, villain));
        dealer.setStreet(Crupier.PREFLOP);
        dealer.setBetCount(betCount);
        dealer.setCurrentBet(currentBet);
        dealer.setLastRaise(currentBet);
        dealer.setPot(pot);
        dealer.setLastAggressor(villain);
        return dealer;
    }

    private static int card(String notation) {
        return new Card(notation).getIndex();
    }
}
