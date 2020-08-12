package com.tonikelope.coronapoker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/*
 * Copyright (C) 2020 tonikelope
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
/**
 *
 * @author tonikelope
 */
public class Pot {

    private float diferencial = 0f;
    private float apuesta = 0f;
    private final ArrayList<PlayerInterface> jugadores = new ArrayList<>();
    private Pot hijo = null;

    public Pot(float dif) {
        this.diferencial = dif;
    }

    public Pot(ArrayList<PlayerInterface> jugadores, float dif) {
        this.diferencial = dif;
        jugadores.forEach((jugador) -> {
            this.insertarJugador(jugador);
        });
    }

    public float getTotal() {

        float total = 0f;

        for (PlayerInterface jugador : jugadores) {
            if (jugador.getDecision() != PlayerInterface.FOLD && !jugador.isExit() && !jugador.isSpectator()) {
                total += apuesta;
            } else {
                total += jugador.getBote();
            }
        }

        return total;
    }

    public Pot getHijo() {
        return hijo;
    }

    public float getApuesta() {
        return apuesta;
    }

    public ArrayList<PlayerInterface> getJugadores() {
        return jugadores;
    }

    public void insertarJugador(PlayerInterface jugador) {

        if (Helpers.float1DSecureCompare(apuesta, jugador.getBote() - this.diferencial) < 0) {
            apuesta = jugador.getBote() - this.diferencial;
        }

        if (!jugadores.contains(jugador)) {
            //Nuevo jugador en el bote
            jugadores.add(jugador);
        }
    }

    public void calcularBotesDerivados() {
        if (jugadores.size() > 1) {
            Collections.sort(jugadores, new PotPlayerComparator());
            int i = 0;
            for (PlayerInterface jugador : jugadores) {

                if (jugador.getDecision() != PlayerInterface.FOLD && !jugador.isExit() && !jugador.isSpectator()) {
                    break;
                } else {
                    i++;
                }
            }
            if (i < jugadores.size()) {

                float pivote = jugadores.get(i).getBote() - this.diferencial;

                if (Helpers.float1DSecureCompare(pivote, apuesta) < 0) {
                    // Sólo hay que generar bote hijo si algún jugador está participando con una apuesta menor (sin ser FOLD)
                    ArrayList<PlayerInterface> jugadores_hijo = new ArrayList<>();
                    jugadores.stream().filter((jugador) -> (Helpers.float1DSecureCompare(pivote, jugador.getBote() - this.diferencial) < 0 && jugador.getDecision() != PlayerInterface.FOLD && !jugador.isExit() && !jugador.isSpectator())).forEachOrdered((jugador) -> {
                        jugadores_hijo.add(jugador);
                    });
                    apuesta = pivote; // Actualizamos la apuesta del padre
                    hijo = new Pot(jugadores_hijo, this.diferencial + apuesta);
                    hijo.calcularBotesDerivados();
                }
            }
        }
    }

    private class PotPlayerComparator implements Comparator<PlayerInterface> {

        @Override
        public int compare(PlayerInterface jugador1, PlayerInterface jugador2) {

            float val1 = jugador1.getBote();

            float val2 = jugador2.getBote();

            return Helpers.float1DSecureCompare(val1, val2);
        }
    }

}
