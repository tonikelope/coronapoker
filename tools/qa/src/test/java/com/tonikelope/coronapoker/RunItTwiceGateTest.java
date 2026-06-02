/*
 * Run-it-twice SIDE-B unlock-gate oracle test.
 *
 * SIDE-B deals the remaining community cards a SECOND time from FRESH megapacket
 * offsets, under dedicated RIT2_* unlock phases (so they get their own single-serve
 * tags, disjoint from SIDE-A's FLOP/TURN/RIVER). Two security invariants are pinned
 * here on the pure gating predicate:
 *
 *  1. ANTI-LEAK: a RIT2_* unlock is refused whenever SIDE-B dealing is NOT in progress
 *     (ritSideB == false), EVEN at river / show_time. Otherwise a hostile host could ask
 *     a peer to de-lock the spare-deck points during an ordinary hand and learn cards
 *     that are still live in the stub.
 *
 *  2. ANTI EARLY-CASCADE (within SIDE-B): once SIDE-B is running, the same lockstep gate
 *     as the live board applies — a future SIDE-B street is refused until the local street
 *     machine has re-advanced to it; the current/past street is allowed.
 *
 * Normal (live-board) phases must be unaffected by the SIDE-B flag.
 */
package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RunItTwiceGateTest {

    @Test
    public void rit2RefusedWhenNotDealingSideB() {
        // No SIDE-B in progress: every RIT2 phase is refused, even at the most permissive
        // local state (river + show_time). This is the anti-leak boundary.
        for (boolean showTime : new boolean[]{false, true}) {
            for (int street : new int[]{Crupier.PREFLOP, Crupier.FLOP, Crupier.TURN, Crupier.RIVER}) {
                assertFalse(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_RIT2_FLOP, street, showTime, false),
                        "RIT2_FLOP must be refused outside SIDE-B dealing");
                assertFalse(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_RIT2_TURN, street, showTime, false),
                        "RIT2_TURN must be refused outside SIDE-B dealing");
                assertFalse(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_RIT2_RIVER, street, showTime, false),
                        "RIT2_RIVER must be refused outside SIDE-B dealing");
            }
        }
    }

    @Test
    public void rit2FutureStreetRefusedDuringSideB() {
        // During SIDE-B (ritSideB == true) the anti early-cascade ordering still holds.
        assertFalse(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_RIT2_FLOP, Crupier.PREFLOP, false, true),
                "RIT2_FLOP at PREFLOP — early cascade, refuse");
        assertFalse(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_RIT2_TURN, Crupier.FLOP, false, true),
                "RIT2_TURN at FLOP — early cascade, refuse");
        assertFalse(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_RIT2_RIVER, Crupier.FLOP, false, true),
                "RIT2_RIVER at FLOP — early cascade, refuse");
        assertFalse(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_RIT2_RIVER, Crupier.TURN, false, true),
                "RIT2_RIVER at TURN — early cascade, refuse");
    }

    @Test
    public void rit2CurrentAndPastStreetAllowedDuringSideB() {
        // Current street: the legitimate SIDE-B reveal.
        assertTrue(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_RIT2_FLOP, Crupier.FLOP, false, true));
        assertTrue(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_RIT2_TURN, Crupier.TURN, false, true));
        assertTrue(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_RIT2_RIVER, Crupier.RIVER, false, true));

        // Past street: allowed by the gate, inert under the chain binding (anchored residual).
        assertTrue(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_RIT2_FLOP, Crupier.RIVER, false, true));
        assertTrue(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_RIT2_TURN, Crupier.RIVER, false, true));
    }

    @Test
    public void liveBoardPhasesUnaffectedBySideBFlag() {
        // The SIDE-B flag never changes the live-board gating.
        assertTrue(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_POCKET, Crupier.PREFLOP, false, true));
        assertTrue(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_FLOP, Crupier.FLOP, false, true));
        assertFalse(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_TURN, Crupier.FLOP, false, true),
                "live TURN at FLOP still refused regardless of SIDE-B flag");
        assertTrue(Crupier.isUnlockPhaseAllowedForStreet(Crupier.UNLOCK_PHASE_RABBIT_FLOP, Crupier.RIVER, true, false));
    }
}
