/*
 * Copyright (C) 2020 tonikelope
 _              _ _        _                  
| |_ ___  _ __ (_) | _____| | ___  _ __   ___ 
| __/ _ \| '_ \| | |/ / _ \ |/ _ \| '_ \ / _ \
| || (_) | | | | |   <  __/ | (_) | |_) |  __/
 \__\___/|_| |_|_|_|\_\___|_|\___/| .__/ \___|
 ____    ___  ____    ___  
|___ \  / _ \|___ \  / _ \ 
  __) || | | | __) || | | |
 / __/ | |_| |/ __/ | |_| |
|_____| \___/|_____| \___/ 

https://github.com/tonikelope/coronapoker
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tonikelope.coronapoker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * One pot in a hand's pot/side-pot chain. Tracks who is contesting the pot and how
 * much it takes to be in it; {@link #genSidePots()} splits off child pots for players
 * who went all-in for less than everyone else.
 *
 * @author tonikelope
 */
public final class HandPot {

    private final ArrayList<Player> players = new ArrayList<>();
    // Dead money carried over from a lower-level pot: folded players who put in MORE
    // than that pot's cap, so their excess is still live in this one. Kept separate
    // from players because it CONTRIBUTES but does NOT COMPETE: getPlayers() decides
    // who collects the pot (Crupier uses it both to pay out and to work out hands),
    // and dead money has no say there. In the main pot this list is always empty:
    // folded players already sit in players there, same as the dealer puts them.
    private final ArrayList<Player> dead_money = new ArrayList<>();
    private volatile double diff = 0;
    private volatile double bet = 0;
    private volatile HandPot sidePot = null;

    public int getSide_pot_count() {

        if (sidePot == null) {
            return 0;
        }

        int s = 1;
        HandPot spot = sidePot.getSidePot();

        while (spot != null) {
            s++;
            spot = spot.getSidePot();
        }

        return s;
    }

    public HandPot(double dif) {
        this.diff = dif;
    }

    public double getBet() {
        return bet;
    }

    public HandPot(ArrayList<Player> jugadores, double diff) {
        this.diff = diff;

        for (var jugador : jugadores) {
            addPlayer(jugador);
        }
    }

    /**
     * Does this player COMPETE for the pot? A folded (or otherwise inactive) player
     * leaves their money in, but can't collect it.
     */
    private static boolean compite(Player jugador) {
        // An all-in has already put its chips at risk. A later disconnect must
        // not turn it into dead money nor remove its claim from the pots it matched.
        return jugador.getDecision() != Player.FOLD
                && (jugador.isActivo() || jugador.getDecision() == Player.ALLIN);
    }

    /**
     * What a non-competing player contributes to THIS pot. Counted from the pot's
     * floor ({@code diff}) and capped at its ceiling ({@code bet}): anything put in
     * above that belongs to a deeper side pot, collectable only by players who reached
     * that level. Without the cap, all dead money would fall into the main pot and get
     * swept up by a short all-in that never matched it.
     *
     * The deepest pot (no side pot below it) doesn't cap: there's no higher level left
     * to send the remainder to, so it absorbs it all and the pots still sum to exactly
     * what was bet.
     */
    private double aportacionMuerta(Player jugador) {

        double aportado = Math.max(0, jugador.getBote() - this.diff);

        return (sidePot != null) ? Math.min(aportado, bet) : aportado;
    }

    public double getTotal() {

        double total = 0;

        for (Player jugador : players) {
            if (compite(jugador)) {
                total += bet;
            } else {
                total += aportacionMuerta(jugador);
            }
        }

        for (Player jugador : dead_money) {
            total += aportacionMuerta(jugador);
        }

        return total;
    }

    public HandPot getSidePot() {
        return sidePot;
    }

    public ArrayList<Player> getPlayers() {
        return players;
    }

    /**
     * Adds dead money inherited from a lower-level pot. Does NOT touch {@code bet}:
     * a folded player doesn't set a pot's ceiling, they only contribute their share.
     */
    void addDeadMoney(Player jugador) {

        if (!dead_money.contains(jugador) && !players.contains(jugador)) {
            dead_money.add(jugador);
        }
    }

    public void addPlayer(Player jugador) {

        if (Helpers.doubleSecureCompare(bet, jugador.getBote() - this.diff) < 0) {
            bet = jugador.getBote() - this.diff;
        }

        if (!players.contains(jugador)) {
            players.add(jugador);
        }
    }

    /**
     * Splits off a side pot when some player is contesting this pot with a smaller
     * bet than the rest (i.e. went all-in short); recurses to build the full chain.
     */
    public void genSidePots() {

        if (players.size() > 1 && sidePot == null) {

            Collections.sort(players, new PotPlayerComparator());

            int i = 0;

            for (Player jugador : players) {

                if (compite(jugador)) {
                    break;
                } else {
                    i++;
                }
            }

            if (i < players.size()) {

                // Bet of the player contesting this pot with the smallest contribution.
                double lowest_player_bet = players.get(i).getBote() - this.diff;

                if (Helpers.doubleSecureCompare(lowest_player_bet, bet) < 0) {

                    bet = lowest_player_bet;

                    // A side pot is only needed if some player is contesting with a bet
                    // above this pot's bet.
                    ArrayList<Player> sidepot_players = new ArrayList<>();

                    for (var jugador : players) {

                        if (compite(jugador) && Helpers.doubleSecureCompare(bet, jugador.getBote() - this.diff) < 0) {

                            // Contesting this pot with MORE than its bet: goes into the child side pot.
                            sidepot_players.add(jugador);
                        }
                    }

                    // No side pot is created without competitors above the floor. One
                    // with nobody competing couldn't be collected by ANYONE (getPlayers
                    // would be empty), and routing dead money to it would make that
                    // money vanish from the payout. Skipping it leaves this pot as the
                    // deepest one, so it absorbs that money whole — exactly what used
                    // to happen before pots were split by level.
                    if (!sidepot_players.isEmpty()) {

                        sidePot = new HandPot(sidepot_players, bet + this.diff);

                        // Folded players' money above this pot's ceiling stays alive in
                        // the side pot, carried over as dead money (contributes, doesn't
                        // compete). Without this, capping the contribution in getTotal
                        // would make that excess disappear from the payout.
                        double techo = bet + this.diff;

                        for (var jugador : players) {
                            if (!compite(jugador) && Helpers.doubleSecureCompare(jugador.getBote(), techo) > 0) {
                                sidePot.addDeadMoney(jugador);
                            }
                        }

                        for (var jugador : dead_money) {
                            if (Helpers.doubleSecureCompare(jugador.getBote(), techo) > 0) {
                                sidePot.addDeadMoney(jugador);
                            }
                        }

                        sidePot.genSidePots();
                    }
                }
            }
        }
    }

    private class PotPlayerComparator implements Comparator<Player> {

        @Override
        public int compare(Player jugador1, Player jugador2) {

            double val1 = jugador1.getBote();

            double val2 = jugador2.getBote();

            return Helpers.doubleSecureCompare(val1, val2);
        }
    }

}
