/*
 * Gate "exigir prueba de barajado" (rotacion-3): decision PURA shouldWarnMissingShuffleProof.
 * Avisar al revelar community SII: fase community + reparto fresco + no verificado + no avisado.
 * Cubre los casos que importan para CERO falsos positivos (recover, pocket, fold, doble aviso).
 */
package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShuffleProofGateTest {

    private static final int POCKET = Crupier.UNLOCK_PHASE_POCKET;
    private static final int COMMUNITY = POCKET + 1; // cualquier fase != pocket (flop/turn/river/rabbit)

    private static byte[] deck(int tag) {
        byte[] b = new byte[32];
        b[0] = (byte) tag;
        return b;
    }

    @Test
    public void freshUnverifiedCommunityWarns() {
        // EL CASO QUE IMPORTA: reparto fresco, host no mando bundle, voy a revelar community -> aviso.
        byte[] m = deck(1);
        assertTrue(Crupier.shouldWarnMissingShuffleProof(COMMUNITY, m, m, null, null),
                "fresco + community + no verificado -> avisa");
    }

    @Test
    public void verifiedCommunityDoesNotWarn() {
        // Caso honesto: el bundle verifico para este mazo -> NO avisa.
        byte[] m = deck(1);
        assertFalse(Crupier.shouldWarnMissingShuffleProof(COMMUNITY, m, m, deck(1), null),
                "verificado -> no avisa");
    }

    @Test
    public void pocketPhaseDoesNotWarn() {
        // Fase pocket no es la ventana de lectura del smuggle -> NO avisa.
        byte[] m = deck(1);
        assertFalse(Crupier.shouldWarnMissingShuffleProof(POCKET, m, m, null, null),
                "fase pocket -> no avisa");
    }

    @Test
    public void recoveredDeckDoesNotWarn() {
        // Recover: el mazo restaurado NO se marco como 'expect' (no paso por el MEGAPACKET fresco) ->
        // expect es de otro mazo (o null) -> NO avisa (el barajado se verifico pre-crash).
        byte[] m = deck(2);
        assertFalse(Crupier.shouldWarnMissingShuffleProof(COMMUNITY, m, deck(1), null, null),
                "mazo de recover (expect != mazo) -> no avisa");
        assertFalse(Crupier.shouldWarnMissingShuffleProof(COMMUNITY, m, null, null, null),
                "mazo de recover (expect null) -> no avisa");
    }

    @Test
    public void alreadyWarnedDoesNotWarnAgain() {
        // Aviso unico por mazo: turn/river del mismo mazo no re-avisan.
        byte[] m = deck(1);
        assertFalse(Crupier.shouldWarnMissingShuffleProof(COMMUNITY, m, m, null, deck(1)),
                "ya avisado para este mazo -> no re-avisa");
    }

    @Test
    public void nullMegapacketDoesNotWarn() {
        assertFalse(Crupier.shouldWarnMissingShuffleProof(COMMUNITY, null, null, null, null),
                "megapacket null -> no avisa");
    }
}
