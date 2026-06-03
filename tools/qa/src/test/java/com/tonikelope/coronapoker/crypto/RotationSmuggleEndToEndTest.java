/*
 * ¿ES FACTIBLE EL ATAQUE? Ejecuta el smuggle COMPLETO de extremo a extremo con las primitivas
 * reales (RistrettoSRA) y el check EXACTO de GATE 6, para responder sin suposiciones:
 *
 *  - El host cuela la pocket de un jugador ACTIVO (X) en una posición comunitaria NO USADA.
 *  - La rotación (cada peer: strip k_pocket + add k_community) la lleva a community-space.
 *  - El host la lee pidiendo a X e Y que pelen su k_community (GATE 6 silencioso porque el host
 *    se guarda SU lock para el final), y pela el suyo al final -> obtiene la carta de X.
 *  - El BOARD REAL queda intacto (otras posiciones comunitarias) y la carta robada NO aparece en
 *    el board ni colisiona con nada -> nadie se entera, el flujo no se rompe.
 *
 * Si los asserts pasan, el ataque es factible tal cual está master hoy.
 */
package com.tonikelope.coronapoker.crypto;

import com.tonikelope.coronapoker.Helpers;
import java.security.SecureRandom;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RotationSmuggleEndToEndTest {

    @BeforeAll
    public static void ensureRng() {
        if (Helpers.CSPRNG_GENERATOR == null) {
            Helpers.CSPRNG_GENERATOR = new SecureRandom();
        }
    }

    // Anillo: host H + victima ACTIVA X + tercero Y. Cada uno con k_pocket y k_community.
    private byte[] kpH, kpX, kpY, kcH, kcX, kcY;

    private void freshRing() {
        kpH = RistrettoSRA.generateLockScalar();
        kpX = RistrettoSRA.generateLockScalar();
        kpY = RistrettoSRA.generateLockScalar();
        kcH = RistrettoSRA.generateLockScalar();
        kcX = RistrettoSRA.generateLockScalar();
        kcY = RistrettoSRA.generateLockScalar();
    }

    private static byte[] card(byte[] genesis, int idx) {
        return Arrays.copyOfRange(genesis, idx * 32, (idx + 1) * 32);
    }

    /** Post-cascada: una carta bajo TODOS los pocket-locks del anillo. */
    private byte[] pocketLockAll(byte[] cardPoint) {
        byte[] p = RistrettoSRA.applyCommutativeLock(cardPoint, kpH);
        p = RistrettoSRA.applyCommutativeLock(p, kpX);
        return RistrettoSRA.applyCommutativeLock(p, kpY);
    }

    /** Rotación del anillo: cada peer quita su k_pocket y añade su k_community (incl. X sin saberlo). */
    private byte[] rotate(byte[] pocketLockedPiece) {
        byte[] r = pocketLockedPiece;
        r = RistrettoSRA.applyCommutativeLock(r, RistrettoSRA.getUnlockScalar(kpH));
        r = RistrettoSRA.applyCommutativeLock(r, kcH);
        r = RistrettoSRA.applyCommutativeLock(r, RistrettoSRA.getUnlockScalar(kpX));
        r = RistrettoSRA.applyCommutativeLock(r, kcX);
        r = RistrettoSRA.applyCommutativeLock(r, RistrettoSRA.getUnlockScalar(kpY));
        r = RistrettoSRA.applyCommutativeLock(r, kcY);
        return r;
    }

    /**
     * Reveal community de una posición (copia del host): los helpers X e Y pelan su k_community
     * (GATE 6 EXACTO del handler: si el residuo resuelve a genesis tras pelar, lockdown), y el host
     * pela el suyo al FINAL (local, sin GATE 6). Devuelve el índice de carta que lee el host.
     * assertGate6Silent: comprueba que GATE 6 nunca dispara en los pasos de helper.
     */
    private int hostReadsViaCommunityChain(byte[] communityLockedPiece) {
        byte[] r = communityLockedPiece;
        // helper X pela su k_community
        r = RistrettoSRA.applyCommutativeLock(r, RistrettoSRA.getUnlockScalar(kcX));
        assertTrue(RistrettoSRA.resolveCardIndex(r) < 0, "GATE 6 silencioso tras pelar X (queda kcH, kcY)");
        // helper Y pela su k_community
        r = RistrettoSRA.applyCommutativeLock(r, RistrettoSRA.getUnlockScalar(kcY));
        assertTrue(RistrettoSRA.resolveCardIndex(r) < 0, "GATE 6 silencioso tras pelar Y (queda kcH)");
        // el host pela el suyo al FINAL (local, sin GATE 6) -> lee la carta
        r = RistrettoSRA.applyCommutativeLock(r, RistrettoSRA.getUnlockScalar(kcH));
        return RistrettoSRA.resolveCardIndex(r);
    }

    @Test
    public void hostSilentlyReadsActivePlayerHoleCardWithBoardIntact() {
        freshRing();
        byte[] genesis = RistrettoSRA.getGenesisDeck();

        final int victimHoleCard = 42;            // la hole card de X que el host quiere leer
        final int[] boardCards = {3, 4, 5, 6, 7}; // el board REAL (flop/turn/river)
        final int unusedCard = 11;                // una comunitaria que NO se usa (nadie la ve)

        // --- Estado post-cascada (todo bajo los 3 pocket-locks) ---
        byte[] xPocketPiece = pocketLockAll(card(genesis, victimHoleCard)); // slot pocket de X
        byte[][] boardPieces = new byte[boardCards.length][];
        for (int j = 0; j < boardCards.length; j++) {
            boardPieces[j] = pocketLockAll(card(genesis, boardCards[j]));
        }
        // Posición comunitaria NO USADA: legítimamente tendría 'unusedCard'...
        // ...pero el HOST la SUSTITUYE por la pocket de X (el smuggle).
        byte[] smuggledUnusedPiece = xPocketPiece; // <-- EL ATAQUE

        // --- Rotación de TODAS las comunitarias (board + la no usada smuggleada) ---
        byte[][] rotatedBoard = new byte[boardCards.length][];
        for (int j = 0; j < boardCards.length; j++) {
            rotatedBoard[j] = rotate(boardPieces[j]);
        }
        byte[] rotatedSmuggled = rotate(smuggledUnusedPiece);

        // --- (1) El board REAL se revela intacto (juego normal) ---
        for (int j = 0; j < boardCards.length; j++) {
            int idx = hostReadsViaCommunityChain(rotatedBoard[j]);
            assertEquals(boardCards[j], idx, "el board real se revela correcto (carta " + boardCards[j] + ")");
        }

        // --- (2) El host LEE la hole card de X por la posición no usada ---
        int stolen = hostReadsViaCommunityChain(rotatedSmuggled);
        assertEquals(victimHoleCard, stolen,
                "el host lee la hole card del jugador ACTIVO X (" + victimHoleCard + ") en silencio");

        // --- (3) Nadie se entera: la carta robada NO está en el board -> sin colisión ---
        boolean onBoard = false;
        for (int b : boardCards) {
            if (b == stolen) {
                onBoard = true;
            }
        }
        assertFalse(onBoard, "la carta robada no aparece en el board -> ningun check de colision la caza");

        // --- Resumen del veredicto ---
        System.out.println("[ATAQUE] FACTIBLE: host leyo la hole card " + stolen
                + " de un jugador activo; board real intacto " + Arrays.toString(boardCards)
                + "; sin colision, sin -1, sin GATE 6, sin romper el flujo.");
    }
}
