/*
 * ZERO-TRUST (future-board CRITICAL): Crupier.communitySlotRange is the canonical
 * per-phase community-slot layout the client uses to REJECT a host that asks to
 * decrypt a card from a FUTURE street (turn/river during the flop). The street
 * gate validates the phase LABEL; this range binds the phase to the exact SLOTS
 * it is allowed to touch. If this layout ever drifts from the real emitters
 * (enviarCartasComunitarias / rabbit / SIDE-B), the guard would either miss an
 * attack or false-reject an honest reveal, so it is pinned here for all 9
 * community phases and every table size.
 *
 * Layout: pocketCount = 2N; FLOP off = 2N+1 (3 cards), TURN 2N+5 (1), RIVER 2N+7
 * (1); the RIT2 second board is shifted by RIT2_BOARD_SPAN (8).
 */
package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CommunitySlotRangeTest {

    @Test
    public void flopTurnRiverLayoutForEveryTableSize() {
        for (int n = 2; n <= 10; n++) {
            assertArrayEquals(new int[]{2 * n + 1, 3},
                    Crupier.communitySlotRange(Crupier.UNLOCK_PHASE_FLOP, n), "flop N=" + n);
            assertArrayEquals(new int[]{2 * n + 5, 1},
                    Crupier.communitySlotRange(Crupier.UNLOCK_PHASE_TURN, n), "turn N=" + n);
            assertArrayEquals(new int[]{2 * n + 7, 1},
                    Crupier.communitySlotRange(Crupier.UNLOCK_PHASE_RIVER, n), "river N=" + n);
        }
    }

    @Test
    public void rabbitPhasesShareTheSameLayoutAsTheNormalStreets() {
        for (int n = 2; n <= 10; n++) {
            assertArrayEquals(Crupier.communitySlotRange(Crupier.UNLOCK_PHASE_FLOP, n),
                    Crupier.communitySlotRange(Crupier.UNLOCK_PHASE_RABBIT_FLOP, n), "rabbit flop N=" + n);
            assertArrayEquals(Crupier.communitySlotRange(Crupier.UNLOCK_PHASE_TURN, n),
                    Crupier.communitySlotRange(Crupier.UNLOCK_PHASE_RABBIT_TURN, n), "rabbit turn N=" + n);
            assertArrayEquals(Crupier.communitySlotRange(Crupier.UNLOCK_PHASE_RIVER, n),
                    Crupier.communitySlotRange(Crupier.UNLOCK_PHASE_RABBIT_RIVER, n), "rabbit river N=" + n);
        }
    }

    @Test
    public void rit2SecondBoardIsShiftedByTheBoardSpan() {
        // RIT2_BOARD_SPAN = 8: the second run-it-twice board sits 8 slots past the first.
        for (int n = 2; n <= 10; n++) {
            assertArrayEquals(new int[]{2 * n + 8 + 1, 3},
                    Crupier.communitySlotRange(Crupier.UNLOCK_PHASE_RIT2_FLOP, n), "rit2 flop N=" + n);
            assertArrayEquals(new int[]{2 * n + 8 + 5, 1},
                    Crupier.communitySlotRange(Crupier.UNLOCK_PHASE_RIT2_TURN, n), "rit2 turn N=" + n);
            assertArrayEquals(new int[]{2 * n + 8 + 7, 1},
                    Crupier.communitySlotRange(Crupier.UNLOCK_PHASE_RIT2_RIVER, n), "rit2 river N=" + n);
        }
    }

    @Test
    public void pocketAndUnknownPhasesReturnNull() {
        // POCKET is covered by the disjoint-scalar + self-strip guards, not by a slot range.
        assertNull(Crupier.communitySlotRange(Crupier.UNLOCK_PHASE_POCKET, 6));
        assertNull(Crupier.communitySlotRange(-1, 6));
        assertNull(Crupier.communitySlotRange(999, 6));
    }

    @Test
    public void streetsAreDisjointAndMonotonicWithOneBurnBetweenThem() {
        // The security property the guard leans on: a phase can pin EXACTLY its own
        // slots, each street strictly after the previous with a single burn card
        // between, so a future-street request never falls inside the current window.
        int n = 6;
        int[] flop = Crupier.communitySlotRange(Crupier.UNLOCK_PHASE_FLOP, n);
        int[] turn = Crupier.communitySlotRange(Crupier.UNLOCK_PHASE_TURN, n);
        int[] river = Crupier.communitySlotRange(Crupier.UNLOCK_PHASE_RIVER, n);
        // one burn between the end of the flop block and the turn card
        assertEquals(flop[0] + flop[1] + 1, turn[0]);
        // one burn between the turn card and the river card
        assertEquals(turn[0] + turn[1] + 1, river[0]);
        // and the flop starts right after a single burn past the 2N pocket slots
        assertEquals(2 * n + 1, flop[0]);
    }
}
