/*
 * Regression test for the run-it-twice SIDE-B abort decision.
 *
 * The second board of a run-it-twice deals over the network UNDER lock_contabilidad (it runs
 * inside the showdown). A stop or a disconnect while that deal is in flight must NOT be settled
 * with a half-finished board, nor voided/refunded, nor turned into a "malicious peer" game-over:
 * the hand is LEFT IN PROGRESS (hand.end stays 0) so the recover replays the whole run-it-twice
 * from the fossil. shouldLeaveRunItTwiceHandInProgress is the pure decision behind that: it fires
 * ONLY when the SIDE-B deal did not complete and the cause is a termination reason (a stop /
 * teardown, a fin-de-transmision, or an explicit SIDE-B interrupt from a peer drop routed to
 * recover) AND the bets were not already refunded (which is the MISDEAL branch, a different path).
 * A completed deal always settles normally.
 */
package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RunItTwiceSideBAbortTest {

    // Atajo legible: (dealt, refunded, termination, fin, interrupted).
    private static boolean decide(boolean dealt, boolean refunded, boolean termination,
            boolean fin, boolean interrupted) {
        return Crupier.shouldLeaveRunItTwiceHandInProgress(dealt, refunded, termination, fin, interrupted);
    }

    @Test
    public void aCompletedSideBAlwaysSettles() {
        // dealt=true: la cara-B se repartio entera -> se liquida normal, nunca "en curso",
        // aunque haya cualquier senal encima.
        assertFalse(decide(true, false, false, false, false));
        assertFalse(decide(true, false, true, true, true));
    }

    @Test
    public void aStopDuringSideBLeavesTheHandInProgress() {
        // Corte por terminacion (detener / teardown) sin repartir la cara-B ni devolver -> en curso.
        assertTrue(decide(false, false, true, false, false), "termination_pending -> en curso");
        assertTrue(decide(false, false, false, true, false), "fin_de_la_transmision -> en curso");
        assertTrue(decide(false, false, false, false, true), "rit_sideb_interrupted (peer drop) -> en curso");
    }

    @Test
    public void aRefundedAbortIsTheMisdealBranchNotThis() {
        // Si ya se devolvieron las apuestas es el camino MISDEAL (anula+cierra): esta decision NO.
        assertFalse(decide(false, true, true, false, false));
        assertFalse(decide(false, true, false, false, true));
    }

    @Test
    public void aPlainFailureWithoutTerminationIsNotLeftInProgress() {
        // Cara-B sin repartir, sin devolver y SIN ninguna senal de terminacion (p.ej. el jugador
        // local abandonando por otra via): NO se deja en curso por esta decision; lo maneja el
        // resto del settle. El "dejar en curso" es exclusivo de la terminacion.
        assertFalse(decide(false, false, false, false, false));
    }
}
