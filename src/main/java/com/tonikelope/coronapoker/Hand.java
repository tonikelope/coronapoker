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
import java.util.HashMap;

public class Hand {

    public static final String[] NOMBRES_JUGADAS_ES = new String[]{"CARTA ALTA", "PAREJA", "DOBLE PAREJA", "TRÍO", "ESCALERA", "COLOR", "FULL", "PÓKER", "ESCALERA DE COLOR", "ESCALERA REAL"};
    public static final String[] NOMBRES_JUGADAS_EN = new String[]{"HIGH CARD", "ONE PAIR", "TWO PAIR", "THREE OF A KIND", "STRAIGHT", "FLUSH", "FULL HOUSE", "FOUR OF A KIND", "STRAIGHT FLUSH", "ROYAL FLUSH"};
    public static volatile String[] NOMBRES_JUGADAS = GameFrame.LANGUAGE.equals("es") ? NOMBRES_JUGADAS_ES : NOMBRES_JUGADAS_EN;
    public static final int CARTA_ALTA = 1;
    public static final int PAREJA = 2;
    public static final int DOBLE_PAREJA = 3;
    public static final int TRIO = 4;
    public static final int ESCALERA = 5;
    public static final int COLOR = 6;
    public static final int FULL = 7;
    public static final int POKER = 8;
    public static final int ESCALERA_COLOR = 9;
    public static final int ESCALERA_COLOR_REAL = 10;

    public static int handnameToHandValue(String name) {

        int i = 1;

        for (String s : NOMBRES_JUGADAS) {

            if (s.equals(name)) {
                return i;
            }

            i++;
        }

        return -1;
    }

    //Returns a new Card collection with highest value cards
    private static ArrayList<Card> buscarCartasValoresMasAltos(ArrayList<Card> candidatas, int size) {

        if (candidatas == null || size == 0) {
            return null;
        }

        ArrayList<Card> cartas = new ArrayList<>(candidatas);

        Card.sortCollection(cartas);

        return cartas.size() <= size ? cartas : new ArrayList<>(cartas.subList(0, size));
    }

    //Returns a new Card collection of EXACT size with the highest repeated value cards or null if not found
    private static ArrayList<Card> buscarCartasValoresRepetidos(ArrayList<Card> candidatas, int size) {

        if (candidatas == null || candidatas.size() < size || size < 2) {
            return null;
        }

        ArrayList<Card> cartas = new ArrayList<>(candidatas);

        Card.sortCollection(cartas);

        Card pivote = cartas.get(0);

        int i;

        ArrayList<Card> repetidas = new ArrayList<>();

        i = 1;

        repetidas.add(pivote);

        while (repetidas.size() < size && i < cartas.size()) {

            if (pivote.getValorNumerico() == cartas.get(i).getValorNumerico()) {

                repetidas.add(cartas.get(i));

            } else {

                pivote = cartas.get(i);
                repetidas.clear();
                repetidas.add(pivote);
            }

            i++;
        }

        return (repetidas.size() == size) ? repetidas : null;

    }

    //Returns a new Card collection of EXACT size with straight values cards (descending order) or null if not found 
    private static ArrayList<Card> buscarCartasValoresCorrelativos(ArrayList<Card> candidatas, boolean sort_ace_low) {

        if (candidatas == null || candidatas.size() < Crupier.CARTAS_ESCALERA) {
            return null;
        }

        ArrayList<Card> cartas = new ArrayList<>(candidatas);

        if (sort_ace_low) {
            Card.sortAceLowCollection(cartas);
        } else {
            Card.sortCollection(cartas);
        }

        int i, last_card_value;

        ArrayList<Card> escalera = new ArrayList<>();

        Card pivote = cartas.get(0);

        i = 1;

        escalera.add(pivote);

        last_card_value = pivote.getValorNumerico(sort_ace_low);

        while (escalera.size() < Crupier.CARTAS_ESCALERA && i < cartas.size()) {

            while (i < cartas.size() && last_card_value == cartas.get(i).getValorNumerico(sort_ace_low)) {
                i++; //Skip duplicated values (collection MUST BE ORDERED)
            }

            if (i < cartas.size()) {

                if (pivote.getValorNumerico(sort_ace_low) - cartas.get(i).getValorNumerico(sort_ace_low) == escalera.size()) {

                    escalera.add(cartas.get(i));
                    last_card_value = cartas.get(i).getValorNumerico(sort_ace_low);

                } else {

                    pivote = cartas.get(i);
                    escalera.clear();
                    escalera.add(pivote);
                    last_card_value = pivote.getValorNumerico(sort_ace_low);
                }

                i++;
            }
        }

        return (escalera.size() == Crupier.CARTAS_ESCALERA) ? escalera : null;
    }

    //Returns a new Card collection of size [5-7] with same suit cards (descending order) or null if not found
    private static ArrayList<Card> buscarCartasMismoPalo(ArrayList<Card> candidatas) {

        if (candidatas == null || candidatas.size() < Crupier.CARTAS_COLOR) {
            return null;
        }

        HashMap<String, ArrayList<Card>> palos = new HashMap<>();
        palos.put("P", new ArrayList<>());
        palos.put("D", new ArrayList<>());
        palos.put("T", new ArrayList<>());
        palos.put("C", new ArrayList<>());
        ArrayList<Card> color = new ArrayList<>();

        for (Card carta : candidatas) {

            ArrayList<Card> palo = palos.get(carta.getPalo());

            palo.add(carta);

            if (palo.size() > color.size()) {
                //Keep largest suited subcollection
                color = palo;
            }
        }

        Card.sortCollection(color);

        return color.size() >= Crupier.CARTAS_COLOR ? color : null;
    }

    public static ArrayList<Card> hayPareja(ArrayList<Card> candidatas) {

        return buscarCartasValoresRepetidos(candidatas, Crupier.CARTAS_PAREJA);

    }

    public static ArrayList<Card> hayTrio(ArrayList<Card> candidatas) {

        return buscarCartasValoresRepetidos(candidatas, Crupier.CARTAS_TRIO);

    }

    public static ArrayList<Card> hayPoker(ArrayList<Card> candidatas) {

        return buscarCartasValoresRepetidos(candidatas, Crupier.CARTAS_POKER);

    }

    public static ArrayList<Card> hayFull(ArrayList<Card> candidatas) {

        ArrayList<Card> cartas = new ArrayList<>(candidatas);

        ArrayList<Card> posible_full = hayTrio(cartas);

        if (posible_full != null) {

            cartas.removeAll(posible_full);

            ArrayList<Card> pareja = hayPareja(cartas);

            if (pareja != null) {

                posible_full.addAll(pareja);

                return posible_full;
            }

        }

        return null;

    }

    public static ArrayList<Card> hayDoblePareja(ArrayList<Card> candidatas) {

        ArrayList<Card> cartas = new ArrayList<>(candidatas);

        if (hayPoker(cartas) == null) {

            ArrayList<Card> posible_doble_pareja = hayPareja(cartas);

            if (posible_doble_pareja != null) {

                cartas.removeAll(posible_doble_pareja);

                ArrayList<Card> pareja2 = hayPareja(cartas);

                if (pareja2 != null) {
                    posible_doble_pareja.addAll(pareja2);
                    return posible_doble_pareja;
                }
            }

        }

        return null;

    }

    public static ArrayList<Card> hayEscalera(ArrayList<Card> candidatas) {

        ArrayList<Card> escalera_alta = buscarCartasValoresCorrelativos(candidatas, false);

        if (escalera_alta != null) {

            return escalera_alta;
        }

        return buscarCartasValoresCorrelativos(candidatas, true);
    }

    public static ArrayList<Card> hayEscaleraColor(ArrayList<Card> candidatas) {

        return buscarCartasValoresCorrelativos(buscarCartasMismoPalo(candidatas), true);

    }

    public static ArrayList<Card> hayEscaleraReal(ArrayList<Card> candidatas) {

        ArrayList<Card> posible_escalera_real = buscarCartasValoresCorrelativos(buscarCartasMismoPalo(candidatas), false);

        return (posible_escalera_real != null && posible_escalera_real.get(0).getValor().equals("A")) ? posible_escalera_real : null;

    }

    public static ArrayList<Card> hayColor(ArrayList<Card> candidatas) {

        ArrayList<Card> posible_color = buscarCartasMismoPalo(candidatas);

        return (posible_color != null && posible_color.size() >= Crupier.CARTAS_COLOR) ? new ArrayList<>(posible_color.subList(0, Crupier.CARTAS_COLOR)) : null;
    }

    private volatile ArrayList<Card> cartas_utilizables = null;
    private volatile ArrayList<Card> mano = null;
    private volatile ArrayList<Card> winners = null;
    private volatile ArrayList<Card> kickers = null;
    private volatile int hand_value = -1;
    private volatile String hand_name = null;
    private volatile double fuerza;

    public double getFuerza() {
        return fuerza;
    }

    public void setFuerza(double fuerza) {
        this.fuerza = fuerza;
    }

    public Hand(ArrayList<Card> cartas) {
        if (!cartas.isEmpty()) {
            this.cartas_utilizables = new ArrayList<>(cartas);
            Object[] mejor_jugada = calcularMejorJugada();
            this.hand_value = (int) mejor_jugada[0];
            this.hand_name = NOMBRES_JUGADAS[this.hand_value - 1];
            this.winners = (ArrayList<Card>) mejor_jugada[1];
            this.mano = new ArrayList<>(this.winners);

            if (mejor_jugada.length == 3 && mejor_jugada[2] != null) {
                this.kickers = (ArrayList<Card>) mejor_jugada[2];
                this.mano.addAll(kickers);
            } else {
                this.kickers = null;
            }
        }

    }

    @Override
    public String toString() {
        return this.hand_name + " " + Card.collection2String(this.winners) + (this.kickers != null ? " (" + Card.collection2String(this.kickers) + ")" : "");
    }

    public ArrayList<Card> getWinners() {
        return winners;
    }

    public int getValue() {
        return hand_value;
    }

    public ArrayList<Card> getMano() {
        return mano;
    }

    public String getName() {
        return hand_name;
    }

    private Object[] calcularMejorJugada() {

        ArrayList<Card> kick_cards;

        ArrayList<Card> mejor_jugada = Hand.hayEscaleraReal(cartas_utilizables);

        if (mejor_jugada != null) {
            return new Object[]{ESCALERA_COLOR_REAL, mejor_jugada};
        }

        mejor_jugada = Hand.hayEscaleraColor(cartas_utilizables);

        if (mejor_jugada != null) {
            return new Object[]{ESCALERA_COLOR, mejor_jugada};
        }

        mejor_jugada = Hand.hayPoker(cartas_utilizables);

        if (mejor_jugada != null) {

            kick_cards = new ArrayList<>(cartas_utilizables);

            kick_cards.removeAll(mejor_jugada);

            return new Object[]{POKER, mejor_jugada, kick_cards.isEmpty() ? null : buscarCartasValoresMasAltos(kick_cards, Crupier.CARTAS_MAX - mejor_jugada.size())};
        }

        mejor_jugada = Hand.hayFull(cartas_utilizables);

        if (mejor_jugada != null) {
            return new Object[]{FULL, mejor_jugada};
        }

        mejor_jugada = Hand.hayColor(cartas_utilizables);

        if (mejor_jugada != null) {
            return new Object[]{COLOR, mejor_jugada};
        }

        mejor_jugada = Hand.hayEscalera(cartas_utilizables);

        if (mejor_jugada != null) {
            return new Object[]{ESCALERA, mejor_jugada};
        }

        mejor_jugada = Hand.hayTrio(cartas_utilizables);

        if (mejor_jugada != null) {

            kick_cards = new ArrayList<>(cartas_utilizables);

            kick_cards.removeAll(mejor_jugada);

            return new Object[]{TRIO, mejor_jugada, kick_cards.isEmpty() ? null : buscarCartasValoresMasAltos(kick_cards, Crupier.CARTAS_MAX - mejor_jugada.size())};
        }

        mejor_jugada = Hand.hayDoblePareja(cartas_utilizables);

        if (mejor_jugada != null) {

            kick_cards = new ArrayList<>(cartas_utilizables);

            kick_cards.removeAll(mejor_jugada);

            return new Object[]{DOBLE_PAREJA, mejor_jugada, kick_cards.isEmpty() ? null : buscarCartasValoresMasAltos(kick_cards, Crupier.CARTAS_MAX - mejor_jugada.size())};

        }

        mejor_jugada = Hand.hayPareja(cartas_utilizables);

        if (mejor_jugada != null) {

            kick_cards = new ArrayList<>(cartas_utilizables);

            kick_cards.removeAll(mejor_jugada);

            return new Object[]{PAREJA, mejor_jugada, kick_cards.isEmpty() ? null : buscarCartasValoresMasAltos(kick_cards, Crupier.CARTAS_MAX - mejor_jugada.size())};

        }

        mejor_jugada = buscarCartasValoresMasAltos(cartas_utilizables, Crupier.CARTAS_MAX);

        return new Object[]{CARTA_ALTA, mejor_jugada};
    }

}
