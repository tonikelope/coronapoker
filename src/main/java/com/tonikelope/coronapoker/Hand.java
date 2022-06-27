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

import static com.tonikelope.coronapoker.Crupier.CARTAS_MAX;
import static com.tonikelope.coronapoker.Crupier.CARTAS_PAREJA;
import static com.tonikelope.coronapoker.Crupier.CARTAS_POKER;
import static com.tonikelope.coronapoker.Crupier.CARTAS_TRIO;
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

    public static int handNAME2HandVal(String name) {

        int i = 1;

        for (String s : NOMBRES_JUGADAS) {

            if (s.equals(name)) {
                return i;
            }

            i++;
        }

        return -1;
    }

    private static ArrayList<Card> buscarCartasAltas(ArrayList<Card> c, int total) {

        if (c == null || c.size() < total) {
            return null;
        }

        ArrayList<Card> cartas = new ArrayList<>(c);

        Card.sortCollection(cartas);

        return new ArrayList<>(cartas.subList(0, total));
    }

    private static ArrayList<Card> buscarCartasValoresRepetidos(ArrayList<Card> c, int repetidas) {

        if (c == null || c.size() < repetidas) {
            return null;
        }

        ArrayList<Card> cartas = new ArrayList<>(c);

        Card.sortCollection(cartas);

        int piv = 0, i, t = 0;

        ArrayList<Card> jugada = new ArrayList<>();

        while (t < repetidas && cartas.size() - piv >= repetidas) {

            i = piv + 1;

            t = 1;

            jugada.add(cartas.get(piv));

            while (t < repetidas && i < cartas.size() && piv < i) {

                if (cartas.get(piv).getValorNumerico() == cartas.get(i).getValorNumerico()) {

                    jugada.add(cartas.get(i));
                    i++;
                    t++;

                } else {

                    piv = i;
                    jugada.clear();
                }
            }
        }

        return (t == repetidas) ? jugada : null;

    }

    public static ArrayList<Card> hayPareja(ArrayList<Card> c) {

        return buscarCartasValoresRepetidos(c, CARTAS_PAREJA);

    }

    public static ArrayList<Card> hayTrio(ArrayList<Card> c) {

        return buscarCartasValoresRepetidos(c, CARTAS_TRIO);

    }

    public static ArrayList<Card> hayPoker(ArrayList<Card> c) {

        return buscarCartasValoresRepetidos(c, CARTAS_POKER);

    }

    public static ArrayList<Card> hayFull(ArrayList<Card> c) {

        ArrayList<Card> cartas = new ArrayList<>(c);

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

    public static ArrayList<Card> hayDoblePareja(ArrayList<Card> c) {

        ArrayList<Card> cartas = new ArrayList<>(c);

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

    private static ArrayList<Card> buscarCartasCorrelativas(ArrayList<Card> c, boolean sort_ace_low, int tot_cards) {

        if (c == null || c.size() < tot_cards) {
            return null;
        }

        ArrayList<Card> cartas = new ArrayList<>(c);

        if (sort_ace_low) {
            Card.sortAceLowCollection(cartas);
        } else {
            Card.sortCollection(cartas);
        }

        int piv = 0, i, t = 0;

        ArrayList<Card> escalera = new ArrayList<>();

        while (t < tot_cards && cartas.size() - piv >= tot_cards) {

            i = piv + 1;

            t = 1;

            escalera.add(cartas.get(piv));

            while (t < tot_cards && i < cartas.size() && piv < i) {

                if (cartas.get(piv).getValorNumerico(sort_ace_low) - cartas.get(i).getValorNumerico(sort_ace_low) == t) {

                    escalera.add(cartas.get(i));
                    i++;
                    t++;

                } else {

                    piv = i;
                    escalera.clear();
                }
            }
        }

        return (t == tot_cards) ? escalera : null;
    }

    public static ArrayList<Card> hayEscalera(ArrayList<Card> c) {

        ArrayList<Card> cartas = Card.removeCollectionDuplicatedCardValues(c);

        if (cartas.size() < Crupier.CARTAS_ESCALERA) {
            return null;
        }

        ArrayList<Card> escalera_alta = buscarCartasCorrelativas(cartas, false, Crupier.CARTAS_ESCALERA);

        if (escalera_alta != null) {

            return escalera_alta;
        }

        return buscarCartasCorrelativas(cartas, true, Crupier.CARTAS_ESCALERA);
    }

    public static ArrayList<Card> hayEscaleraColor(ArrayList<Card> c) {

        return buscarCartasCorrelativas(buscarCartasMismoPalo(c, Crupier.CARTAS_COLOR), true, Crupier.CARTAS_ESCALERA);

    }

    public static ArrayList<Card> hayEscaleraReal(ArrayList<Card> c) {

        ArrayList<Card> posible_escalera_real = buscarCartasCorrelativas(buscarCartasMismoPalo(c, Crupier.CARTAS_COLOR), false, Crupier.CARTAS_ESCALERA);

        return (posible_escalera_real != null && posible_escalera_real.get(0).getValor().equals("A")) ? posible_escalera_real : null;

    }

    private static ArrayList<Card> buscarCartasMismoPalo(ArrayList<Card> c, int size) {

        if (c == null || c.size() < size) {
            return null;
        }

        HashMap<String, ArrayList<Card>> palos = new HashMap<>();
        palos.put("P", new ArrayList<>());
        palos.put("D", new ArrayList<>());
        palos.put("T", new ArrayList<>());
        palos.put("C", new ArrayList<>());
        ArrayList<Card> color = new ArrayList<>();

        for (Card carta : c) {

            ArrayList<Card> palo = palos.get(carta.getPalo());

            palo.add(carta);

            if (palo.size() > color.size()) {
                color = palo;
            }
        }

        return color.size() >= size ? color : null;
    }

    public static ArrayList<Card> hayColor(ArrayList<Card> c) {

        ArrayList<Card> color = buscarCartasMismoPalo(c, Crupier.CARTAS_COLOR);

        Card.sortCollection(color);

        return color != null ? new ArrayList<>(color.subList(0, Crupier.CARTAS_COLOR)) : null;
    }

    public static boolean isEscaleraAs(Hand jugada) {

        return (jugada.getVal() == ESCALERA && jugada.getMano().get(0).getValor().equals("A"));
    }

    ArrayList<Card> cartas_utilizables = null;
    ArrayList<Card> mano = null;
    ArrayList<Card> winners = null;
    ArrayList<Card> kickers = null;
    int val = -1;
    String name = null;

    public Hand(ArrayList<Card> cartas) {
        if (!cartas.isEmpty()) {
            this.cartas_utilizables = new ArrayList<>(cartas);
            Object[] mejor_jugada = calcularMejorJugada();
            this.val = (int) mejor_jugada[0];
            this.name = NOMBRES_JUGADAS[this.val - 1];
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
        return this.name + " " + Card.collection2String(this.winners) + (this.kickers != null ? " (" + Card.collection2String(this.kickers) + ")" : "");
    }

    public ArrayList<Card> getWinners() {
        return winners;
    }

    public int getVal() {
        return val;
    }

    public ArrayList<Card> getMano() {
        return mano;
    }

    public String getName() {
        return name;
    }

    private Object[] calcularMejorJugada() {
        ArrayList<Card> k;
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

            k = new ArrayList<>(cartas_utilizables);

            k.removeAll(mejor_jugada);

            return new Object[]{POKER, mejor_jugada, k.isEmpty() ? null : buscarCartasAltas(k, CARTAS_MAX - mejor_jugada.size())};
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

            k = new ArrayList<>(cartas_utilizables);

            k.removeAll(mejor_jugada);

            return new Object[]{TRIO, mejor_jugada, k.isEmpty() ? null : buscarCartasAltas(k, CARTAS_MAX - mejor_jugada.size())};
        }
        mejor_jugada = Hand.hayDoblePareja(cartas_utilizables);
        if (mejor_jugada != null) {

            k = new ArrayList<>(cartas_utilizables);

            k.removeAll(mejor_jugada);

            return new Object[]{DOBLE_PAREJA, mejor_jugada, k.isEmpty() ? null : buscarCartasAltas(k, CARTAS_MAX - mejor_jugada.size())};

        }
        mejor_jugada = Hand.hayPareja(cartas_utilizables);
        if (mejor_jugada != null) {

            k = new ArrayList<>(cartas_utilizables);

            k.removeAll(mejor_jugada);

            return new Object[]{PAREJA, mejor_jugada, k.isEmpty() ? null : buscarCartasAltas(k, CARTAS_MAX - mejor_jugada.size())};

        }
        mejor_jugada = buscarCartasAltas(cartas_utilizables, CARTAS_MAX);
        return new Object[]{CARTA_ALTA, mejor_jugada};
    }

}
