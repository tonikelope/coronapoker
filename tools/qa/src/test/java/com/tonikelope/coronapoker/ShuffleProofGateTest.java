/*
 * Gate "exigir prueba de barajado" (rotacion-3): decision PURA shouldWarnMissingShuffleProof.
 * Avisar al revelar community SII: fase community + reparto fresco + no verificado + no avisado.
 * Cubre los casos que importan para CERO falsos positivos (recover, pocket, fold, doble aviso).
 */
package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ShuffleProofGateTest {

    private static final int POCKET = Crupier.UNLOCK_PHASE_POCKET;
    private static final int COMMUNITY = POCKET + 1; // cualquier fase != pocket (flop/turn/river/rabbit)

    private static byte[] deck(int tag) {
        byte[] b = new byte[32];
        b[0] = (byte) tag;
        return b;
    }

    @Test
    public void freshUnverifiedCommunityWaits() {
        // A fresh deal must never reveal community until its proof has verified.
        byte[] m = deck(1);
        assertEquals(Crupier.ShuffleProofGateDecision.WAIT,
                Crupier.shuffleProofGateDecision(COMMUNITY, m, m, null, null),
                "fresh + community + proof pending -> wait");
    }

    @Test
    public void verifiedCommunityAllows() {
        byte[] m = deck(1);
        assertEquals(Crupier.ShuffleProofGateDecision.ALLOW,
                Crupier.shuffleProofGateDecision(COMMUNITY, m, m, deck(1), null));
    }

    @Test
    public void pocketPhaseAllowsWhileProofIsPending() {
        byte[] m = deck(1);
        assertEquals(Crupier.ShuffleProofGateDecision.ALLOW,
                Crupier.shuffleProofGateDecision(POCKET, m, m, null, null));
    }

    @Test
    public void recoveredDeckWithoutPersistedVerificationRejects() {
        byte[] m = deck(2);
        assertEquals(Crupier.ShuffleProofGateDecision.REJECT,
                Crupier.shuffleProofGateDecision(COMMUNITY, m, null, null, null),
                "recovery without a persisted proof must fail closed");
    }

    @Test
    public void failedProofRejects() {
        byte[] m = deck(1);
        assertEquals(Crupier.ShuffleProofGateDecision.REJECT,
                Crupier.shuffleProofGateDecision(COMMUNITY, m, m, null, deck(1)));
    }

    @Test
    public void nullMegapacketRejectsCommunityUnlock() {
        assertEquals(Crupier.ShuffleProofGateDecision.REJECT,
                Crupier.shuffleProofGateDecision(COMMUNITY, null, null, null, null));
    }
}
