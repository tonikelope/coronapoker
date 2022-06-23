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
 *
 * @author tonikelope
 */
public final class Pot {

    private final ArrayList<Player> players = new ArrayList<>();
    private volatile float diff = 0f;
    private volatile float bet = 0f;
    private volatile Pot sidePot = null;
    private volatile int side_pot_count = 0;

    public int getSide_pot_count() {
        return side_pot_count;
    }

    public Pot(float dif) {
        this.diff = dif;
    }

    public float getBet() {
        return bet;
    }

    public Pot(ArrayList<Player> jugadores, float diff) {
        this.diff = diff;

        for (var jugador : jugadores) {
            addPlayer(jugador);
        }
    }

    public float getTotal() {

        float total = 0f;

        for (Player jugador : players) {
            if (jugador.getDecision() != Player.FOLD && jugador.isActivo()) {
                total += bet;
            } else {
                total += jugador.getBote();
            }
        }

        return total;
    }

    public Pot getSidePot() {
        return sidePot;
    }

    public ArrayList<Player> getPlayers() {
        return players;
    }

    public void addPlayer(Player jugador) {

        if (Helpers.float1DSecureCompare(bet, jugador.getBote() - this.diff) < 0) {
            bet = jugador.getBote() - this.diff;
        }

        if (!players.contains(jugador)) {
            players.add(jugador);
        }
    }

    //ALLIN SIDE POT(S) GENERATOR
    public void genSidePots() {

        if (players.size() > 1) {

            Collections.sort(players, new PotPlayerComparator());

            int i = 0;

            for (Player jugador : players) {

                if (jugador.getDecision() != Player.FOLD && jugador.isActivo()) {
                    break;
                } else {
                    i++;
                }
            }

            if (i < players.size()) {

                //Apuesta_menor es la apuesta del jugador que participa en el bote con la menor cantidad.
                float apuesta_menor = players.get(i).getBote() - this.diff;

                if (Helpers.float1DSecureCompare(apuesta_menor, bet) < 0) {

                    // Sólo hay que generar sidePot si algún jugador está participando con una apuesta por debajo de la apuesta del bote
                    ArrayList<Player> jugadores_hijo = new ArrayList<>();

                    for (var jugador : players) {

                        if (jugador.getDecision() != Player.FOLD && jugador.isActivo() && Helpers.float1DSecureCompare(apuesta_menor, jugador.getBote() - this.diff) < 0) {
                            jugadores_hijo.add(jugador);
                        }

                    }

                    bet = apuesta_menor; // Actualizamos la apuesta del bote

                    sidePot = new Pot(jugadores_hijo, this.diff + bet);

                    side_pot_count++;

                    sidePot.genSidePots();
                }
            }
        }
    }

    private class PotPlayerComparator implements Comparator<Player> {

        @Override
        public int compare(Player jugador1, Player jugador2) {

            float val1 = jugador1.getBote();

            float val2 = jugador2.getBote();

            return Helpers.float1DSecureCompare(val1, val2);
        }
    }

}
