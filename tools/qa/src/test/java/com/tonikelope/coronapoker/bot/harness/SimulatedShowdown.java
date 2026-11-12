/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.bot.harness;

import com.tonikelope.coronapoker.bot.eval.BotEvaluator;
import com.tonikelope.coronapoker.core.game.CoreGamePot;
import com.tonikelope.coronapoker.core.game.CorePlayerController;
import com.tonikelope.coronapoker.core.game.GamePlayerController;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SimulatedShowdown {

    private SimulatedShowdown() {}

    static Set<Integer> settle(TestBotPlayer[] players, TestDealer dealer, BotEvaluator evaluator) {
        if (dealer.getBoardSize() != 5) {
            throw new IllegalStateException("Showdown without a complete board");
        }
        CoreGamePot main = new CoreGamePot(0);
        List<GamePlayerController> potSeats = new ArrayList<>(players.length);
        int[] ranks = new int[players.length];
        int[] hand = new int[7];
        for (int i = 0; i < 5; i++) {
            hand[i + 2] = dealer.getBoardCardIndex(i);
        }
        for (int seat = 0; seat < players.length; seat++) {
            TestBotPlayer player = players[seat];
            CorePlayerController potSeat = CorePlayerController.remote(player.getNickname());
            potSeat.getState().setPotContribution(player.getBote());
            if (!player.isContending()) {
                potSeat.markFoldedOnRecover();
            } else {
                hand[0] = player.getHoleCard1Index();
                hand[1] = player.getHoleCard2Index();
                ranks[seat] = evaluator.rankHand(hand);
            }
            potSeats.add(potSeat);
            main.addPlayerController(potSeat);
        }
        for (int seat = 0; seat < players.length; seat++) {
            double tableAnte = dealer.tableAntePaidBy(players[seat]);
            if (tableAnte > 0) {
                main.recordTableAnte(potSeats.get(seat), tableAnte);
                break;
            }
        }
        main.genSidePots();

        long total = 0;
        Set<Integer> winners = new HashSet<>();
        for (CoreGamePot layer = main; layer != null; layer = layer.getSidePot()) {
            long chips = SimulatedBettingRound.cents(layer.getTotal());
            total += chips;
            long uncalled = SimulatedBettingRound.cents(layer.getUncalledBet());
            if (uncalled > 0) {
                pay(players[potSeats.indexOf(layer.getPlayerControllers().get(0))], uncalled);
                chips -= uncalled;
            }
            if (chips == 0) {
                continue;
            }
            int best = Integer.MIN_VALUE;
            List<Integer> layerWinners = new ArrayList<>();
            for (GamePlayerController contender : layer.getPlayerControllers()) {
                int seat = potSeats.indexOf(contender);
                if (!players[seat].isContending()) {
                    continue;
                }
                if (ranks[seat] > best) {
                    best = ranks[seat];
                    layerWinners.clear();
                }
                if (ranks[seat] == best) {
                    layerWinners.add(seat);
                }
            }
            if (layerWinners.isEmpty()) {
                throw new IllegalStateException("No eligible seat for a funded pot");
            }
            // Hands in the statistical lane have independent bankrolls.
            // Assign odd cents in seat order instead of carrying them forward.
            layerWinners.sort(Integer::compare);
            long share = chips / layerWinners.size();
            long remainder = chips % layerWinners.size();
            for (int i = 0; i < layerWinners.size(); i++) {
                int seat = layerWinners.get(i);
                pay(players[seat], share + (i < remainder ? 1 : 0));
                winners.add(seat);
            }
        }
        if (total != SimulatedBettingRound.cents(dealer.getBote_total()) || winners.isEmpty()) {
            throw new IllegalStateException("Showdown did not account for the complete pot");
        }
        return winners;
    }

    private static void pay(TestBotPlayer player, long chips) {
        player.setStack(SimulatedBettingRound.money(SimulatedBettingRound.cents(player.getStack()) + chips));
    }
}
