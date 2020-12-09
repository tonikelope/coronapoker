package com.tonikelope.coronapoker;

import static com.tonikelope.coronapoker.Crupier.CARTAS_ESCALERA;
import static com.tonikelope.coronapoker.Crupier.CARTAS_MAX;
import static com.tonikelope.coronapoker.Crupier.CARTAS_PAREJA;
import static com.tonikelope.coronapoker.Crupier.CARTAS_POKER;
import static com.tonikelope.coronapoker.Crupier.CARTAS_TRIO;
import java.util.ArrayList;

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
public class Hand {

    public static final String[] NOMBRES_JUGADAS_ES = new String[]{"CARTA ALTA", "PAREJA", "DOBLE PAREJA", "TRÍO", "ESCALERA", "COLOR", "FULL", "PÓKER", "ESCALERA DE COLOR", "ESCALERA REAL"};
    public static final String[] NOMBRES_JUGADAS_EN = new String[]{"HIGH CARD", "ONE PAIR", "TWO PAIR", "THREE OF A KIND", "STRAIGHT", "FLUSH", "FULL HOUSE", "FOUR OF A KIND", "STRAIGHT FLUSH", "ROYAL FLUSH"};
    public static volatile String[] NOMBRES_JUGADAS = Game.LANGUAGE.equals("es") ? NOMBRES_JUGADAS_ES : NOMBRES_JUGADAS_EN;
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

    public static int getHandValue(String name) {

        int i = 0;

        for (String s : NOMBRES_JUGADAS) {

            if (s.equals(name)) {
                return i;
            }

            i++;
        }

        return -1;
    }

    private static ArrayList<Card> getCartasAltas(ArrayList<Card> c, int total) {

        ArrayList<Card> cartas = new ArrayList<>(c);

        Card.sortCollection(cartas);

        ArrayList<Card> altas = new ArrayList<>();

        for (int i = 0; i < total && i < cartas.size(); i++) {

            altas.add(cartas.get(i));
        }

        return altas;
    }

    private static ArrayList<Card> checkRepetidas(ArrayList<Card> c, int repetidas) {

        ArrayList<Card> cartas = new ArrayList<>(c);

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

        ArrayList<Card> cartas = new ArrayList<>(c);

        Card.sortCollection(cartas);

        return checkRepetidas(cartas, CARTAS_PAREJA);

    }

    public static ArrayList<Card> hayTrio(ArrayList<Card> c) {

        ArrayList<Card> cartas = new ArrayList<>(c);

        Card.sortCollection(cartas);

        return checkRepetidas(cartas, CARTAS_TRIO);

    }

    public static ArrayList<Card> hayPoker(ArrayList<Card> c) {

        ArrayList<Card> cartas = new ArrayList<>(c);

        Card.sortCollection(cartas);

        return checkRepetidas(cartas, CARTAS_POKER);

    }

    public static ArrayList<Card> hayFull(ArrayList<Card> c) {

        ArrayList<Card> cartas = new ArrayList<>(c);

        Card.sortCollection(cartas);

        ArrayList<Card> trio = hayTrio(cartas);

        if (trio != null) {

            cartas.removeAll(trio);

            ArrayList<Card> pareja = hayPareja(cartas);

            if (pareja != null) {

                trio.addAll(pareja);

                return trio;
            }

        }

        return null;

    }

    public static ArrayList<Card> hayDoblePareja(ArrayList<Card> c) {

        ArrayList<Card> cartas = new ArrayList<>(c);

        if (hayPoker(cartas) == null) {

            ArrayList<Card> pareja1 = hayPareja(cartas);

            if (pareja1 != null) {

                cartas.removeAll(pareja1);

                while (cartas.size() >= CARTAS_PAREJA) {

                    ArrayList<Card> pareja2 = hayPareja(cartas);

                    if (pareja2 != null) {

                        if (pareja1.get(0).equals(pareja2.get(0))) {
                            //Check if it is poker and skip this case
                            cartas.removeAll(pareja2);
                        } else {
                            pareja1.addAll(pareja2);

                            return pareja1;
                        }

                    } else {
                        return null;
                    }
                }
            }

        }

        return null;

    }

    private static ArrayList<Card> checkCorrelativas(ArrayList<Card> c, boolean sort_low_ace) {

        ArrayList<Card> cartas = new ArrayList<>(c);

        int piv = 0, i, t = 0;

        ArrayList<Card> escalera = new ArrayList<>();

        while (t < CARTAS_ESCALERA && cartas.size() - piv >= CARTAS_ESCALERA) {

            i = piv + 1;

            t = 1;

            escalera.add(cartas.get(piv));

            while (t < CARTAS_ESCALERA && i < cartas.size() && piv < i) {

                if (cartas.get(piv).getValorNumerico(sort_low_ace) - cartas.get(i).getValorNumerico(sort_low_ace) == t) {

                    escalera.add(cartas.get(i));
                    i++;
                    t++;

                } else {

                    piv = i;
                    escalera.clear();
                }
            }
        }

        return (t == CARTAS_ESCALERA) ? escalera : null;
    }

    public static ArrayList<Card> hayEscalera(ArrayList<Card> c) {

        ArrayList<Card> cartas = new ArrayList<>(c);

        //Primero escalera al AS
        Card.sortCollection(cartas);

        ArrayList<Card> norepes = new ArrayList<>();

        int last = -1;

        for (Card carta : cartas) {

            if (last == -1 || carta.getValorNumerico() != last) {
                norepes.add(carta);
                last = carta.getValorNumerico();
            }
        }

        ArrayList<Card> escalera_as;

        if (norepes.size() >= Crupier.CARTAS_ESCALERA && (escalera_as = checkCorrelativas(norepes, false)) != null) {

            return escalera_as;
        }

        Card.sortAceLowCollection(norepes);

        return norepes.size() >= Crupier.CARTAS_ESCALERA ? checkCorrelativas(norepes, true) : null;
    }

    public static ArrayList<Card> hayEscaleraColor(ArrayList<Card> c) {

        ArrayList<Card> cartas = new ArrayList<>(c);

        ArrayList<Card> posible_escalera_color = checkMismoPalo(cartas, Crupier.CARTAS_COLOR);

        if (posible_escalera_color != null && posible_escalera_color.size() >= Crupier.CARTAS_ESCALERA) {

            Card.sortAceLowCollection(posible_escalera_color);

            return checkCorrelativas(posible_escalera_color, true);
        }

        return null;
    }

    public static ArrayList<Card> hayEscaleraColorReal(ArrayList<Card> c) {

        ArrayList<Card> cartas = new ArrayList<>(c);

        ArrayList<Card> posible_escalera_color = checkMismoPalo(cartas, Crupier.CARTAS_COLOR);

        if (posible_escalera_color != null && posible_escalera_color.size() >= Crupier.CARTAS_ESCALERA) {

            Card.sortCollection(posible_escalera_color);

            ArrayList<Card> escalera = checkCorrelativas(posible_escalera_color, false);

            return (escalera != null && escalera.get(0).getValor().equals("A")) ? escalera : null;
        }

        return null;
    }

    private static ArrayList<Card> checkMismoPalo(ArrayList<Card> c, int size) {

        ArrayList<Card> cartas = new ArrayList<>(c);

        ArrayList<Card> picas = new ArrayList<>();
        ArrayList<Card> diamantes = new ArrayList<>();
        ArrayList<Card> treboles = new ArrayList<>();
        ArrayList<Card> corazones = new ArrayList<>();
        ArrayList<Card> color = null;

        for (Card carta : cartas) {

            switch (carta.getPalo()) {
                case "P":
                    picas.add(carta);
                    break;
                case "D":
                    diamantes.add(carta);
                    break;
                case "T":
                    treboles.add(carta);
                    break;
                case "C":
                    corazones.add(carta);
                    break;
                default:
                    break;
            }
        }

        if (picas.size() >= size) {

            color = picas;

        } else if (diamantes.size() >= size) {

            color = diamantes;

        } else if (treboles.size() >= size) {

            color = treboles;

        } else if (corazones.size() >= size) {

            color = corazones;
        }

        return color;
    }

    public static ArrayList<Card> hayColor(ArrayList<Card> c) {

        ArrayList<Card> cartas = new ArrayList<>(c);

        Card.sortCollection(cartas);

        ArrayList<Card> color = checkMismoPalo(cartas, Crupier.CARTAS_COLOR);

        return (color != null && color.size() >= Crupier.CARTAS_COLOR) ? new ArrayList<Card>(color.subList(0, Crupier.CARTAS_COLOR)) : null;
    }

    public static boolean isEscaleraAs(Hand escalera) {

        return escalera.getVal() == ESCALERA && escalera.getMano().get(0).getValor().equals("A");
    }

    ArrayList<Card> cartas_utilizables;
    ArrayList<Card> mano;
    ArrayList<Card> winners;
    ArrayList<Card> kickers;
    int val;
    String name;

    public Hand(ArrayList<Card> cartas) {
        this.cartas_utilizables = new ArrayList<>(cartas);
        Object[] mejor_jugada = calcularMejorJugada();
        this.val = (int) mejor_jugada[0];
        this.name = NOMBRES_JUGADAS[this.val - 1];
        this.winners = (ArrayList<Card>) mejor_jugada[1];
        this.mano = new ArrayList<>(this.winners);

        if (mejor_jugada.length == 3) {
            this.kickers = (ArrayList<Card>) mejor_jugada[2];
            this.mano.addAll(kickers);
        } else {
            this.kickers = null;
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
        ArrayList<Card> mejor_jugada = Hand.hayEscaleraColorReal(cartas_utilizables);
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

            return new Object[]{POKER, mejor_jugada, getCartasAltas(k, CARTAS_MAX - mejor_jugada.size())};
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

            return new Object[]{TRIO, mejor_jugada, getCartasAltas(k, CARTAS_MAX - mejor_jugada.size())};
        }
        mejor_jugada = Hand.hayDoblePareja(cartas_utilizables);
        if (mejor_jugada != null) {

            k = new ArrayList<>(cartas_utilizables);

            k.removeAll(mejor_jugada);

            return new Object[]{DOBLE_PAREJA, mejor_jugada, getCartasAltas(k, CARTAS_MAX - mejor_jugada.size())};

        }
        mejor_jugada = Hand.hayPareja(cartas_utilizables);
        if (mejor_jugada != null) {

            k = new ArrayList<>(cartas_utilizables);

            k.removeAll(mejor_jugada);

            return new Object[]{PAREJA, mejor_jugada, getCartasAltas(k, CARTAS_MAX - mejor_jugada.size())};

        }
        mejor_jugada = getCartasAltas(cartas_utilizables, CARTAS_MAX);
        return new Object[]{CARTA_ALTA, mejor_jugada};
    }

}
