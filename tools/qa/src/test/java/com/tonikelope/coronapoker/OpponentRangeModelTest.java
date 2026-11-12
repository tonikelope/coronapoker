/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.bot.eval.AlbertaEvaluatorAdapter;
import com.tonikelope.coronapoker.bot.eval.HandStrengthEvaluator.ShowdownOdds;
import com.tonikelope.coronapoker.core.game.GameOpponentStats.PostflopAction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("bot-regression")
class OpponentRangeModelTest {

    private static final int JACK_DIAMONDS = card(11, 1);
    private static final int JACK_SPADES = card(11, 3);
    private static final int[] BOARD = {card(10, 0), card(7, 1), card(2, 2), card(14, 0), card(5, 3)};
    private final AlbertaEvaluatorAdapter evaluator = new AlbertaEvaluatorAdapter();

    @Test
    void aBetAndACheckYieldDifferentRiverRanges() {
        OpponentRangeModel model = new OpponentRangeModel(evaluator, JACK_DIAMONDS, JACK_SPADES, BOARD);
        Bot.OpponentTracker bettor = new Bot.OpponentTracker();
        Bot.OpponentTracker checker = new Bot.OpponentTracker();
        assertNull(model.estimate(checker, false));
        bettor.recordPostflopDecision(Crupier.RIVER, false, false, true, false);
        checker.recordPostflopDecision(Crupier.RIVER, false, false, false, false);

        double uniform = evaluator.showdownOdds(JACK_DIAMONDS, JACK_SPADES, BOARD).equity(1);
        assertTrue(model.estimate(bettor, false).equity(1) < uniform);
        assertTrue(model.estimate(checker, false).equity(1) > uniform);
    }

    @Test
    void theSameRiverBetCanRepresentDifferentEarlierLines() {
        OpponentRangeModel model = new OpponentRangeModel(evaluator, JACK_DIAMONDS, JACK_SPADES, BOARD);
        Bot.OpponentTracker barreled = new Bot.OpponentTracker();
        Bot.OpponentTracker checked = new Bot.OpponentTracker();
        for (int street = Crupier.FLOP; street <= Crupier.TURN; street++) {
            barreled.recordPostflopDecision(street, false, false, true, false);
            checked.recordPostflopDecision(street, false, false, false, false);
        }
        barreled.recordPostflopDecision(Crupier.RIVER, false, false, true, false);
        checked.recordPostflopDecision(Crupier.RIVER, false, false, true, false);
        assertTrue(model.estimate(barreled, false).equity(1) < model.estimate(checked, false).equity(1));
    }

    @Test
    void aRoyalFlushOnTheBoardRemainsAnExactChop() {
        int[] royal = {card(10, 0), card(11, 0), card(12, 0), card(13, 0), card(14, 0)};
        Bot.OpponentTracker opponent = new Bot.OpponentTracker();
        opponent.recordVPIP(1);
        opponent.recordPFR(1);
        opponent.recordPostflopDecision(Crupier.RIVER, true, true, true, false);
        ShowdownOdds odds = new OpponentRangeModel(evaluator, JACK_DIAMONDS, JACK_SPADES, royal)
                .estimate(opponent, true);
        assertEquals(0, odds.win());
        assertEquals(1, odds.tie());
    }

    @Test
    void exactTwoOpponentRangeShareMatchesDisjointHoldingEnumeration() {
        OpponentRangeModel model = new OpponentRangeModel(evaluator, JACK_DIAMONDS, JACK_SPADES, BOARD);
        Bot.OpponentTracker bettor = new Bot.OpponentTracker();
        Bot.OpponentTracker caller = new Bot.OpponentTracker();
        bettor.recordVPIP(1);
        bettor.recordPFR(1);
        bettor.recordPostflopDecision(Crupier.RIVER, false, false, true, false);
        caller.recordPostflopDecision(Crupier.TURN, true, false, false, false);
        var first = model.range(bettor, true);
        var second = model.range(caller, false);
        int[] ranks = evaluator.opponentRanks(BOARD);
        int heroRank = ranks[JACK_DIAMONDS * 52 + JACK_SPADES];
        double total = 0;
        double payout = 0;
        for (int a = 0; a < first.weights().length; a++) {
            if (first.weights()[a] == 0) {
                continue;
            }
            long used = (1L << (a / 52)) | (1L << (a % 52));
            for (int b = 0; b < second.weights().length; b++) {
                if (second.weights()[b] == 0 || (used & ((1L << (b / 52)) | (1L << (b % 52)))) != 0) {
                    continue;
                }
                double mass = first.weights()[a] * second.weights()[b];
                total += mass;
                if (heroRank >= ranks[a] && heroRank >= ranks[b]) {
                    int ties = (heroRank == ranks[a] ? 1 : 0) + (heroRank == ranks[b] ? 1 : 0);
                    payout += mass / (1 + ties);
                }
            }
        }
        assertEquals(payout / total, model.twoOpponentShare(first, second), 1e-9);
        assertEquals(payout / total, model.twoOpponentShare(second, first), 1e-9);
    }

    @Test
    void acceptedAggressionSurvivesALaterCallButResetsNextHand() {
        Bot.OpponentTracker opponent = new Bot.OpponentTracker();
        opponent.recordHandPlayed();
        opponent.recordVPIP(1);
        opponent.recordPFR(1);
        opponent.recordPostflopDecision(Crupier.FLOP, false, false, true, false);
        opponent.recordPostflopDecision(Crupier.FLOP, true, true, true, false);
        opponent.recordPostflopDecision(Crupier.FLOP, true, true, false, false);
        opponent.recordPostflopDecision(Crupier.TURN, false, false, false, false);
        assertEquals(PostflopAction.RAISE, opponent.postflopAction(Crupier.FLOP));
        assertEquals(PostflopAction.CHECK, opponent.postflopAction(Crupier.TURN));
        assertEquals(PostflopAction.NONE, opponent.postflopAction(Crupier.RIVER));
        assertTrue(opponent.raisedPreflopThisHand());
        assertEquals(0.5, opponent.raiseToBetRate(Crupier.FLOP, 0.5));

        opponent.recordHandPlayed();
        assertEquals(PostflopAction.NONE, opponent.postflopAction(Crupier.FLOP));
        assertEquals(PostflopAction.NONE, opponent.postflopAction(Crupier.TURN));
        assertFalse(opponent.raisedPreflopThisHand());
        assertFalse(opponent.playedVoluntarilyPreflop());
        assertEquals(0.5, opponent.raiseToBetRate(Crupier.FLOP, 0.5));
    }

    @Test
    void frequentAggressionOnBothSidesOfTheFlopDisablesSelectiveReads() {
        Bot.OpponentTracker loose = new Bot.OpponentTracker();
        Bot.OpponentTracker selective = new Bot.OpponentTracker();
        for (int hand = 1; hand <= 8; hand++) {
            loose.recordHandPlayed();
            selective.recordHandPlayed();
            if (hand <= 6) {
                loose.recordPFR(hand);
            }
            if (hand <= 2) {
                selective.recordPFR(hand);
            }
        }
        assertFalse(loose.isUnselectiveBettor());
        for (int street = Crupier.FLOP; street <= Crupier.RIVER; street++) {
            loose.recordPostflopDecision(street, false, false, true, false);
            selective.recordPostflopDecision(street, false, false, true, false);
        }
        assertTrue(loose.isUnselectiveBettor());
        assertFalse(selective.isUnselectiveBettor());
    }

    @Test
    void rankTableHasExactlyTheLegalHoldingsAndMatchesTheHandEvaluator() {
        int[] ranks = evaluator.opponentRanks(BOARD);
        int[] hand = Arrays.copyOf(BOARD, 7);
        int valid = 0;
        for (int first = 0; first < 52; first++) {
            for (int second = 0; second < 52; second++) {
                boolean legal = first < second;
                for (int blocked : BOARD) {
                    legal &= first != blocked && second != blocked;
                }
                int rank = ranks[first * 52 + second];
                if (legal) {
                    hand[5] = first;
                    hand[6] = second;
                    assertEquals(evaluator.rankHand(hand), rank);
                    valid++;
                } else {
                    assertEquals(-1, rank);
                }
            }
        }
        assertEquals(47 * 46 / 2, valid);
    }

    @Test
    void sampledPreflopEquityRespectsHoleCardBlockersAndOpponentWeights() {
        int aceClubs = card(14, 0);
        int aceDiamonds = card(14, 1);
        int kingHearts = card(13, 2);
        int kingSpades = card(13, 3);
        double[][] kings = new double[52][52];
        kings[kingHearts][kingSpades] = 1;
        double equity = evaluator.preflopAllInEquity(aceClubs, aceDiamonds, kings);
        assertTrue(equity > 0.77 && equity < 0.87, () -> "AA vs KK equity: " + equity);

        // A blocked holding must not enter the sample regardless of its weight.
        kings[aceClubs][aceDiamonds] = 1000;
        assertEquals(equity, evaluator.preflopAllInEquity(aceDiamonds, aceClubs, kings));
        assertTrue(Double.isNaN(evaluator.preflopAllInEquity(kingHearts, aceClubs, kings)));
    }

    private static int card(int rank, int suit) {
        return rank - 2 + suit * 13;
    }
}
