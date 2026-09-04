/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Frontend-independent main/side-pot repository. */
public final class CoreGamePot implements GamePot {

    private final ArrayList<GamePlayerController> players = new ArrayList<>();
    private final ArrayList<GamePlayerController> deadMoney = new ArrayList<>();
    private final double difference;
    private double bet;
    private CoreGamePot sidePot;

    public CoreGamePot(double difference) {
        this.difference = difference;
    }

    private CoreGamePot(List<? extends GamePlayerController> players,
            double difference) {
        this(difference);
        players.forEach(this::addPlayerController);
    }

    @Override public double getBet() { return bet; }

    @Override
    public double getTotal() {
        double total = 0d;
        for (GamePlayerController player : players) {
            total += competes(player) ? bet : deadContribution(player);
        }
        for (GamePlayerController player : deadMoney) {
            total += deadContribution(player);
        }
        return MoneyMath.clean(total);
    }

    @Override
    public int getSide_pot_count() {
        int count = 0;
        for (CoreGamePot current = sidePot; current != null;
                current = current.sidePot) count++;
        return count;
    }

    @Override public CoreGamePot getSidePot() { return sidePot; }
    @Override public List<GamePlayerController> getPlayerControllers() {
        return players;
    }

    @Override
    public void addPlayerController(GamePlayerController player) {
        if (MoneyMath.compare(bet, player.getBote() - difference) < 0) {
            bet = MoneyMath.clean(player.getBote() - difference);
        }
        if (!players.contains(player)) players.add(player);
    }

    @Override
    public void genSidePots() {
        if (players.size() <= 1 || sidePot != null) return;
        players.sort(Comparator.comparingDouble(GamePlayerController::getBote));
        int firstCompetitor = 0;
        while (firstCompetitor < players.size()
                && !competes(players.get(firstCompetitor))) firstCompetitor++;
        if (firstCompetitor >= players.size()) return;

        double lowest = MoneyMath.clean(
                players.get(firstCompetitor).getBote() - difference);
        if (MoneyMath.compare(lowest, bet) >= 0) return;
        bet = lowest;

        ArrayList<GamePlayerController> deeper = new ArrayList<>();
        for (GamePlayerController player : players) {
            if (competes(player)
                    && MoneyMath.compare(bet, player.getBote() - difference) < 0) {
                deeper.add(player);
            }
        }
        if (deeper.isEmpty()) return;

        double ceiling = MoneyMath.clean(bet + difference);
        sidePot = new CoreGamePot(deeper, ceiling);
        for (GamePlayerController player : players) {
            if (!competes(player) && MoneyMath.compare(player.getBote(), ceiling) > 0) {
                sidePot.addDeadMoney(player);
            }
        }
        for (GamePlayerController player : deadMoney) {
            if (MoneyMath.compare(player.getBote(), ceiling) > 0) {
                sidePot.addDeadMoney(player);
            }
        }
        sidePot.genSidePots();
    }

    private void addDeadMoney(GamePlayerController player) {
        if (!deadMoney.contains(player) && !players.contains(player)) {
            deadMoney.add(player);
        }
    }

    private double deadContribution(GamePlayerController player) {
        double contribution = Math.max(0d,
                MoneyMath.clean(player.getBote() - difference));
        return sidePot == null ? contribution : Math.min(contribution, bet);
    }

    private static boolean competes(GamePlayerController player) {
        return player.getDecision() != GamePlayerController.FOLD
                && (player.isActivo()
                || player.getDecision() == GamePlayerController.ALLIN);
    }
}
