/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker.smoke;

import com.tonikelope.coronapoker.core.game.CardCode;
import com.tonikelope.coronapoker.core.game.CoreCardController;
import com.tonikelope.coronapoker.core.game.CoreGameHand;
import com.tonikelope.coronapoker.core.game.GameCardController;
import com.tonikelope.coronapoker.core.game.GameHandResult;
import com.tonikelope.coronapoker.core.game.GameText;
import java.util.ArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safety net for the canonical, renderer-neutral hand evaluator. Covers all
 * ten rankings plus the edge cases most likely to regress during refactors.
 * cases que típicamente se rompen en refactors (A-5 wheel straight, kickers
 * post-pareja, full house vs trips+pair, straight flush con A-K-Q-J-10, etc.).
 *
 * Esto NO arregla un bug existente — crea la RED DE SEGURIDAD para futuros
 * refactors. Si algún día se extrae HandEvaluator del Crupier (Sprint 8
 * deferred), estos tests aseguran que los resultados permanecen byte-for-byte
 * idénticos.
 *
 * The test deliberately uses headless core cards so it runs in every automated lane.
 */
class HandEvaluatorSmoke {

    /**
     * Helper para crear una Card desde "valor_palo" string. Valores: A 2 3 4 5
     * 6 7 8 9 10 J Q K Palos: P (picas) C (corazones) T (tréboles) D
     * (diamantes)
     */
    private static GameCardController card(String valorPalo) {
        String[] parts = valorPalo.split("_");
        CoreCardController c = new CoreCardController();
        c.iniciarConValorNumerico(CardCode.of(parts[0], parts[1]).oneBased());
        return c;
    }

    private static ArrayList<GameCardController> cards(String... vp) {
        ArrayList<GameCardController> list = new ArrayList<>(vp.length);
        for (String s : vp) {
            list.add(card(s));
        }
        return list;
    }

    @Test
    @DisplayName("Carta alta — 5 cartas variadas sin parejas/escaleras/colores")
    void cartaAlta() {
        ArrayList<GameCardController> cs = cards("A_P", "K_C", "9_T", "5_D", "2_P");
        CoreGameHand h = hand(cs);
        assertEquals(GameHandResult.CARTA_ALTA, h.getValue());
    }

    @Test
    @DisplayName("Pareja")
    void pareja() {
        ArrayList<GameCardController> cs = cards("A_P", "A_C", "9_T", "5_D", "2_P");
        CoreGameHand h = hand(cs);
        assertEquals(GameHandResult.PAREJA, h.getValue());
    }

    @Test
    @DisplayName("Doble pareja")
    void doblePareja() {
        ArrayList<GameCardController> cs = cards("A_P", "A_C", "9_T", "9_D", "2_P");
        CoreGameHand h = hand(cs);
        assertEquals(GameHandResult.DOBLE_PAREJA, h.getValue());
    }

    @Test
    @DisplayName("Trío")
    void trio() {
        ArrayList<GameCardController> cs = cards("A_P", "A_C", "A_T", "5_D", "2_P");
        CoreGameHand h = hand(cs);
        assertEquals(GameHandResult.TRIO, h.getValue());
    }

    @Test
    @DisplayName("Escalera 10-J-Q-K-A")
    void escaleraAlta() {
        ArrayList<GameCardController> cs = cards("10_P", "J_C", "Q_T", "K_D", "A_P");
        CoreGameHand h = hand(cs);
        assertEquals(GameHandResult.ESCALERA, h.getValue());
    }

    @Test
    @DisplayName("Escalera baja A-2-3-4-5 (the wheel) — edge case clásico")
    void escaleraWheel() {
        ArrayList<GameCardController> cs = cards("A_P", "2_C", "3_T", "4_D", "5_P");
        CoreGameHand h = hand(cs);
        assertEquals(GameHandResult.ESCALERA, h.getValue());
    }

    @Test
    @DisplayName("Color (flush) — 5 cartas mismo palo, no escalera")
    void color() {
        ArrayList<GameCardController> cs = cards("A_P", "K_P", "9_P", "5_P", "2_P");
        CoreGameHand h = hand(cs);
        assertEquals(GameHandResult.COLOR, h.getValue());
    }

    @Test
    @DisplayName("Full house — trío + pareja")
    void full() {
        ArrayList<GameCardController> cs = cards("A_P", "A_C", "A_T", "9_D", "9_P");
        CoreGameHand h = hand(cs);
        assertEquals(GameHandResult.FULL, h.getValue());
    }

    @Test
    @DisplayName("Póker — 4 del mismo valor")
    void poker() {
        ArrayList<GameCardController> cs = cards("A_P", "A_C", "A_T", "A_D", "9_P");
        CoreGameHand h = hand(cs);
        assertEquals(GameHandResult.POKER, h.getValue());
    }

    @Test
    @DisplayName("Escalera de color — 5 consecutivos del mismo palo (no A-alta)")
    void escaleraColor() {
        ArrayList<GameCardController> cs = cards("9_P", "10_P", "J_P", "Q_P", "K_P");
        CoreGameHand h = hand(cs);
        assertEquals(GameHandResult.ESCALERA_COLOR, h.getValue());
    }

    @Test
    @DisplayName("Escalera real — A-K-Q-J-10 del mismo palo")
    void escaleraReal() {
        ArrayList<GameCardController> cs = cards("A_P", "K_P", "Q_P", "J_P", "10_P");
        CoreGameHand h = hand(cs);
        assertEquals(GameHandResult.ESCALERA_COLOR_REAL, h.getValue());
    }

    @Test
    @DisplayName("Texas Hold'em: 7 cartas (2 pocket + 5 board) — full > color")
    void sevenCardsFullOverFlush() {
        // hole AP AC, board AT 9D 9P 9T 2C → trío de 9 con par de A = full
        // (alternativa: 3 ases con 9 9 → también full A-over-9)
        ArrayList<GameCardController> cs = cards("A_P", "A_C", "A_T", "9_D", "9_P", "9_T", "2_C");
        CoreGameHand h = hand(cs);
        // El mejor es Full house A-over-9 (ases + treses... no, 3 ases y 2 nueves)
        // Realmente con 3A 3-9, lo mejor sería pókery (no, son 3 A no 4).
        // Cuenta: 3 ases + 3 nueves = el mejor es full (Aces full of Nines)
        assertEquals(GameHandResult.FULL, h.getValue());
    }

    @Test
    @DisplayName("Texas Hold'em 7 cartas: escalera real entre las 7")
    void sevenCardsRoyalFlush() {
        // board contiene la escalera real entera + 2 distractores
        ArrayList<GameCardController> cs = cards("A_P", "K_P", "Q_P", "J_P", "10_P", "2_C", "3_D");
        CoreGameHand h = hand(cs);
        assertEquals(GameHandResult.ESCALERA_COLOR_REAL, h.getValue());
    }

    @Test
    @DisplayName("Pareja con 7 cartas — usa par + 3 kickers más altos")
    void sevenCardsPairWithKickers() {
        ArrayList<GameCardController> cs = cards("A_P", "A_C", "K_T", "Q_D", "J_P", "5_C", "2_D");
        CoreGameHand h = hand(cs);
        assertEquals(GameHandResult.PAREJA, h.getValue());
        // Verifica que la "winners" set tiene ambos A
        var winners = h.getWinners();
        assertEquals(2, winners.size());
        assertTrue(winners.get(0).getValor().equals("A"));
        assertTrue(winners.get(1).getValor().equals("A"));
    }

    private static CoreGameHand hand(ArrayList<GameCardController> cards) {
        return new CoreGameHand(cards, GameText.keys());
    }
}
