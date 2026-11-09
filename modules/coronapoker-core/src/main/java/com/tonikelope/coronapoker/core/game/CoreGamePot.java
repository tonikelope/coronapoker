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
    private final boolean mainPot;
    private GamePlayerController tableAntePayer;
    private double tableAnte;
    private double bet;
    private CoreGamePot sidePot;

    public CoreGamePot(double difference) {
        this.difference = difference;
        this.mainPot = true;
    }

    private CoreGamePot(List<GamePlayerController> players, double difference,
            GamePlayerController tableAntePayer, double tableAnte) {
        this.difference = difference;
        this.mainPot = false;
        this.tableAntePayer = tableAntePayer;
        this.tableAnte = tableAnte;
        players.forEach(this::addPlayerController);
    }

    @Override public double getBet() { return bet; }

    @Override
    public double getTotal() {
        double total = mainPot ? tableAnte : 0d;
        for (GamePlayerController player : players) {
            total += competes(player) ? bet : deadContribution(player);
        }
        for (GamePlayerController player : deadMoney) {
            total += deadContribution(player);
        }
        return MoneyMath.clean(total);
    }

    @Override
    public double getUncalledBet() {
        if (players.size() != 1) {
            return 0d;
        }
        double highestOtherContribution = 0d;
        for (GamePlayerController player : deadMoney) {
            highestOtherContribution = Math.max(highestOtherContribution,
                    deadContribution(player));
        }
        return MoneyMath.clean(Math.max(0d,
                bet - highestOtherContribution));
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
        if (MoneyMath.compare(bet, matchedContribution(player) - difference) < 0) {
            bet = MoneyMath.clean(matchedContribution(player) - difference);
        }
        if (!players.contains(player)) players.add(player);
    }

    @Override
    public void recordTableAnte(GamePlayerController payer, double amount) {
        tableAntePayer = payer;
        tableAnte = amount;
        // postAnte registers the full amount for stack/refund accounting.
        // Recompute the live contribution ceiling with the shared ante removed.
        bet = players.stream().mapToDouble(this::matchedContribution)
                .max().orElse(0d);
    }

    @Override
    public double matchedContribution(GamePlayerController player) {
        return player == tableAntePayer
                ? MoneyMath.clean(player.getBote() - tableAnte) : player.getBote();
    }

    @Override
    public void genSidePots() {
        if (players.size() <= 1 || sidePot != null) return;
        players.sort(Comparator.comparingDouble(this::matchedContribution));
        int firstCompetitor = 0;
        while (firstCompetitor < players.size()
                && !competes(players.get(firstCompetitor))) firstCompetitor++;
        if (firstCompetitor >= players.size()) return;

        double lowest = MoneyMath.clean(
                matchedContribution(players.get(firstCompetitor)) - difference);
        if (MoneyMath.compare(lowest, bet) >= 0) return;
        bet = lowest;

        ArrayList<GamePlayerController> deeper = new ArrayList<>();
        for (GamePlayerController player : players) {
            if (competes(player)
                    && MoneyMath.compare(bet, matchedContribution(player) - difference) < 0) {
                deeper.add(player);
            }
        }
        if (deeper.isEmpty()) return;

        double ceiling = MoneyMath.clean(bet + difference);
        sidePot = new CoreGamePot(deeper, ceiling, tableAntePayer, tableAnte);
        for (GamePlayerController player : players) {
            if (!competes(player) && MoneyMath.compare(matchedContribution(player), ceiling) > 0) {
                sidePot.addDeadMoney(player);
            }
        }
        for (GamePlayerController player : deadMoney) {
            if (MoneyMath.compare(matchedContribution(player), ceiling) > 0) {
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
                MoneyMath.clean(matchedContribution(player) - difference));
        return sidePot == null ? contribution : Math.min(contribution, bet);
    }

    private static boolean competes(GamePlayerController player) {
        return player.getDecision() != GamePlayerController.FOLD
                && (player.isActivo()
                || player.getDecision() == GamePlayerController.ALLIN);
    }
}
