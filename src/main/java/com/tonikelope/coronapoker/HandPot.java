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
public final class HandPot {

    private final ArrayList<Player> players = new ArrayList<>();
    // Dinero muerto que llega de un bote de nivel inferior: jugadores retirados que
    // pusieron MÁS que el tope de aquel bote, así que su exceso sigue vivo en este.
    // Van aparte de players porque APORTAN pero NO COMPITEN: getPlayers() decide quién
    // cobra el bote (Crupier lo usa para pagar y para calcular jugadas) y ahí no pintan
    // nada. En el bote principal esta lista está vacía: allí los retirados ya vienen en
    // players, tal y como los mete el crupier.
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
     * ¿este jugador COMPITE por el bote? El que se retiró (o ya no está activo) deja
     * su dinero, pero no puede cobrarlo.
     */
    private static boolean compite(Player jugador) {
        return jugador.getDecision() != Player.FOLD && jugador.isActivo();
    }

    /**
     * Lo que aporta a ESTE bote un jugador que ya no compite. Se cuenta desde el suelo
     * del bote (diff) y se capa a su tope (bet): lo que puso por encima NO es de este
     * bote, sigue vivo en los derivados, donde solo pueden cobrarlo los que llegaron a
     * ese nivel. Sin ese cap, todo el dinero muerto caía en el bote principal y se lo
     * llevaba un all-in corto que jamás igualó esa cantidad.
     *
     * El bote más profundo (sin derivados) no capa: ahí ya no hay ningún nivel superior
     * al que mandar el resto, así que lo absorbe y la suma de los botes sigue siendo
     * exactamente lo que se apostó.
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
     * Añade dinero muerto heredado de un bote de nivel inferior. NO toca {@code bet}:
     * un retirado no fija el tope de un bote, solo aporta lo que le corresponda.
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

    //ALLIN SIDE POT(S) GENERATOR
    public void genSidePots() {

        if (players.size() > 1 && sidePot == null) {

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
                double lowest_player_bet = players.get(i).getBote() - this.diff;

                if (Helpers.doubleSecureCompare(lowest_player_bet, bet) < 0) {

                    bet = lowest_player_bet; // Actualizamos la apuesta del bote 

                    // Sólo hay que generar sidePot si algún jugador está participando con una apuesta por debajo de la apuesta del bote
                    ArrayList<Player> sidepot_players = new ArrayList<>();

                    for (var jugador : players) {

                        if (jugador.getDecision() != Player.FOLD && jugador.isActivo() && Helpers.doubleSecureCompare(bet, jugador.getBote() - this.diff) < 0) {

                            //Si el jugador está participando en el bote con una apuesta MAYOR que la del bote lo añadimos al bote derivado hijo
                            sidepot_players.add(jugador);
                        }
                    }

                    // SIN competidores por encima del suelo no se crea bote derivado. Uno
                    // sin nadie que compita no lo puede cobrar NADIE (getPlayers vacio), y
                    // llevarle dinero muerto seria hacerlo desaparecer del reparto. Al no
                    // crearlo, este bote se queda como el mas profundo y absorbe ese dinero
                    // entero, que es exactamente lo que pasaba antes de repartir por niveles.
                    if (!sidepot_players.isEmpty()) {

                        sidePot = new HandPot(sidepot_players, bet + this.diff);

                        // El dinero de los retirados que pasa del tope de este bote sigue
                        // vivo en el derivado: viaja como dinero muerto (aporta, no compite).
                        // Sin esto, capar la aportación en getTotal haría desaparecer ese
                        // exceso del reparto.
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
