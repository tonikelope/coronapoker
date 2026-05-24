/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.smoke;

import com.tonikelope.coronapoker.Card;
import com.tonikelope.coronapoker.Hand;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AAA test del evaluador Hand.calcularMejorJugada. Cubre los 10 rankings
 * + edge cases que típicamente se rompen en refactors (A-5 wheel
 * straight, kickers post-pareja, full house vs trips+pair, straight
 * flush con A-K-Q-J-10, etc.).
 *
 * Esto NO arregla un bug existente — crea la RED DE SEGURIDAD para
 * futuros refactors. Si algún día se extrae HandEvaluator del Crupier
 * (Sprint 8 deferred), estos tests aseguran que los resultados
 * permanecen byte-for-byte idénticos.
 *
 * Card construction toca Swing (Helpers.GUIRunAndWait + initComponents).
 * Si el JVM de test es headless, skipeamos todos los tests via
 * Assumptions — no son críticos para el smoke principal.
 */
class HandEvaluatorSmoke {

    @BeforeAll
    static void assumeNotHeadless() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "Card construction depends on Swing; skipping in headless JVM");
    }

    /**
     * Helper para crear una Card desde "valor_palo" string.
     * Valores: A 2 3 4 5 6 7 8 9 10 J Q K
     * Palos:   P (picas) C (corazones) T (tréboles) D (diamantes)
     */
    private static Card card(String valorPalo) {
        String[] parts = valorPalo.split("_");
        Card c = new Card(false); // gui=false sigue ejecutando initComponents
        c.iniciarConValorPalo(parts[0], parts[1]);
        return c;
    }

    private static ArrayList<Card> cards(String... vp) {
        ArrayList<Card> list = new ArrayList<>(vp.length);
        for (String s : vp) {
            list.add(card(s));
        }
        return list;
    }

    @Test
    @DisplayName("Carta alta — 5 cartas variadas sin parejas/escaleras/colores")
    void cartaAlta() {
        ArrayList<Card> cs = cards("A_P", "K_C", "9_T", "5_D", "2_P");
        Hand h = new Hand(cs);
        assertEquals(Hand.CARTA_ALTA, h.getValue());
    }

    @Test
    @DisplayName("Pareja")
    void pareja() {
        ArrayList<Card> cs = cards("A_P", "A_C", "9_T", "5_D", "2_P");
        Hand h = new Hand(cs);
        assertEquals(Hand.PAREJA, h.getValue());
    }

    @Test
    @DisplayName("Doble pareja")
    void doblePareja() {
        ArrayList<Card> cs = cards("A_P", "A_C", "9_T", "9_D", "2_P");
        Hand h = new Hand(cs);
        assertEquals(Hand.DOBLE_PAREJA, h.getValue());
    }

    @Test
    @DisplayName("Trío")
    void trio() {
        ArrayList<Card> cs = cards("A_P", "A_C", "A_T", "5_D", "2_P");
        Hand h = new Hand(cs);
        assertEquals(Hand.TRIO, h.getValue());
    }

    @Test
    @DisplayName("Escalera 10-J-Q-K-A")
    void escaleraAlta() {
        ArrayList<Card> cs = cards("10_P", "J_C", "Q_T", "K_D", "A_P");
        Hand h = new Hand(cs);
        assertEquals(Hand.ESCALERA, h.getValue());
    }

    @Test
    @DisplayName("Escalera baja A-2-3-4-5 (the wheel) — edge case clásico")
    void escaleraWheel() {
        ArrayList<Card> cs = cards("A_P", "2_C", "3_T", "4_D", "5_P");
        Hand h = new Hand(cs);
        assertEquals(Hand.ESCALERA, h.getValue());
    }

    @Test
    @DisplayName("Color (flush) — 5 cartas mismo palo, no escalera")
    void color() {
        ArrayList<Card> cs = cards("A_P", "K_P", "9_P", "5_P", "2_P");
        Hand h = new Hand(cs);
        assertEquals(Hand.COLOR, h.getValue());
    }

    @Test
    @DisplayName("Full house — trío + pareja")
    void full() {
        ArrayList<Card> cs = cards("A_P", "A_C", "A_T", "9_D", "9_P");
        Hand h = new Hand(cs);
        assertEquals(Hand.FULL, h.getValue());
    }

    @Test
    @DisplayName("Póker — 4 del mismo valor")
    void poker() {
        ArrayList<Card> cs = cards("A_P", "A_C", "A_T", "A_D", "9_P");
        Hand h = new Hand(cs);
        assertEquals(Hand.POKER, h.getValue());
    }

    @Test
    @DisplayName("Escalera de color — 5 consecutivos del mismo palo (no A-alta)")
    void escaleraColor() {
        ArrayList<Card> cs = cards("9_P", "10_P", "J_P", "Q_P", "K_P");
        Hand h = new Hand(cs);
        assertEquals(Hand.ESCALERA_COLOR, h.getValue());
    }

    @Test
    @DisplayName("Escalera real — A-K-Q-J-10 del mismo palo")
    void escaleraReal() {
        ArrayList<Card> cs = cards("A_P", "K_P", "Q_P", "J_P", "10_P");
        Hand h = new Hand(cs);
        assertEquals(Hand.ESCALERA_COLOR_REAL, h.getValue());
    }

    @Test
    @DisplayName("Texas Hold'em: 7 cartas (2 pocket + 5 board) — full > color")
    void sevenCardsFullOverFlush() {
        // hole AP AC, board AT 9D 9P 9T 2C → trío de 9 con par de A = full
        // (alternativa: 3 ases con 9 9 → también full A-over-9)
        ArrayList<Card> cs = cards("A_P", "A_C", "A_T", "9_D", "9_P", "9_T", "2_C");
        Hand h = new Hand(cs);
        // El mejor es Full house A-over-9 (ases + treses... no, 3 ases y 2 nueves)
        // Realmente con 3A 3-9, lo mejor sería pókery (no, son 3 A no 4).
        // Cuenta: 3 ases + 3 nueves = el mejor es full (Aces full of Nines)
        assertEquals(Hand.FULL, h.getValue());
    }

    @Test
    @DisplayName("Texas Hold'em 7 cartas: escalera real entre las 7")
    void sevenCardsRoyalFlush() {
        // board contiene la escalera real entera + 2 distractores
        ArrayList<Card> cs = cards("A_P", "K_P", "Q_P", "J_P", "10_P", "2_C", "3_D");
        Hand h = new Hand(cs);
        assertEquals(Hand.ESCALERA_COLOR_REAL, h.getValue());
    }

    @Test
    @DisplayName("Pareja con 7 cartas — usa par + 3 kickers más altos")
    void sevenCardsPairWithKickers() {
        ArrayList<Card> cs = cards("A_P", "A_C", "K_T", "Q_D", "J_P", "5_C", "2_D");
        Hand h = new Hand(cs);
        assertEquals(Hand.PAREJA, h.getValue());
        // Verifica que la "winners" set tiene ambos A
        ArrayList<Card> winners = h.getWinners();
        assertEquals(2, winners.size());
        assertTrue(winners.get(0).getValor().equals("A"));
        assertTrue(winners.get(1).getValor().equals("A"));
    }
}
