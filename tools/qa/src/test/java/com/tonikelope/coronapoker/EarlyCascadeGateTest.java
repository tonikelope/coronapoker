/*
 * Anti early-cascade gate test (regression guard for the chain migration).
 *
 * The old REQ_SRA_UNLOCK_BATCH layered servedAnyForPhase() on top of the street gate
 * because it distrusted the broadcast-set flags. The chain path keeps only the street
 * gate (awaitStreetForUnlockPhase -> isUnlockPhaseStateSafe). This is safe ONLY because
 * the local street advances in lockstep with betting (rondaApuestas), not by the host's
 * broadcast — so a hostile host cannot make a peer reveal a FUTURE street.
 *
 * This pins the pure gating predicate: a future-street unlock is refused; the current or
 * a past street is allowed (re-asking a past street is inert under the chain binding —
 * the residual is deterministic and anchored, so a replay extracts nothing); RABBIT_*
 * needs show_time.
 */
package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EarlyCascadeGateTest {

    @Test
    public void futureStreetUnlockIsRefused() {
        // POCKET is always servable (hand start, before any community street).
        assertTrue(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_POCKET, Crupier.PREFLOP, false));
        assertTrue(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_POCKET, Crupier.RIVER, false));

        // EARLY-CASCADE (the attack): a host asking us to unlock a FUTURE street while our
        // local street machine has not reached it MUST be refused.
        assertFalse(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_FLOP, Crupier.PREFLOP, false),
                "FLOP unlock requested at PREFLOP — early cascade, refuse");
        assertFalse(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_TURN, Crupier.FLOP, false),
                "TURN unlock requested at FLOP — early cascade, refuse");
        assertFalse(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_RIVER, Crupier.FLOP, false),
                "RIVER unlock requested at FLOP — early cascade, refuse");
        assertFalse(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_RIVER, Crupier.TURN, false),
                "RIVER unlock requested at TURN — early cascade, refuse");
    }

    @Test
    public void currentAndPastStreetAllowed_rabbitNeedsShowTime() {
        // Current street: allowed (this is the legitimate reveal).
        assertTrue(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_FLOP, Crupier.FLOP, false));
        assertTrue(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_TURN, Crupier.TURN, false));
        assertTrue(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_RIVER, Crupier.RIVER, false));

        // Past street: allowed by the gate, but inert under the chain binding (the residual
        // is deterministic and anchored to the MEGAPACKET, so re-asking extracts nothing).
        assertTrue(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_FLOP, Crupier.RIVER, false));

        // RABBIT_* require show_time (post-showdown), never before.
        assertFalse(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_RABBIT_FLOP, Crupier.RIVER, false),
                "rabbit before show_time — refuse");
        assertTrue(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_RABBIT_FLOP, Crupier.RIVER, true),
                "rabbit at show_time — allowed");
    }
}
