/*
 * Copyright (C) 2026 tonikelope
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.tonikelope.coronapoker;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ZERO-TRUST RECOVER: detecting a seat that was skipped by MUTUAL OMISSION during the live hand.
 *
 * When a player disconnects mid-hand and its turn comes up, live skips that seat (auto-fold /
 * exit): it contributes NO action to the stored sequence, and H_t goes straight from the previous
 * absorbed action to the next. On recover the player may have reconnected, so its seat is active
 * again; if it plays a fresh action for the slot it missed, the chain gains a record the live hand
 * never had and the next ORIGINAL record fails its PREV_H. {@link Crupier#isSkippedSeatDuringRecover}
 * detects the missed slot purely from the stored action order: the seat whose turn it is must equal
 * the player of the next stored action (order.get(contaAccion)); if it does not, live skipped it and
 * recover must skip it too.
 *
 * The scenario mirrors the real smoke: "cliente" disconnected before acting on the flop, so the flop
 * chain went CoronaBot$3 -> CoronaBot$1 directly (cliente omitted).
 */
class RecoverSkippedSeatTest {

    // Stored actions in counter order (what live actually processed). cliente acts once (preflop),
    // then is absent on the flop: the flop entries are server, CoronaBot$3, CoronaBot$1 — no cliente.
    private static final List<String> ORDER = Arrays.asList(
            "cliente", "CoronaBot$2", "CoronaBot$1", "server", "CoronaBot$3", // preflop (idx 0..4)
            "server", "CoronaBot$3", "CoronaBot$1");                          // flop    (idx 5..7)

    @Test
    @DisplayName("The disconnected seat whose turn does not match the next stored action is skipped")
    void mismatchedSeatIsSkipped() {
        // Flop: after replaying server(idx5) and CoronaBot$3(idx6), contaAccion=7. The betting order
        // reaches cliente, but the next stored action (idx7) is CoronaBot$1 -> cliente was omitted live.
        assertTrue(Crupier.isSkippedSeatDuringRecover("cliente", 7, ORDER));
    }

    @Test
    @DisplayName("The seat that matches the next stored action is replayed, not skipped")
    void matchingSeatIsReplayed() {
        // Same point (contaAccion=7): CoronaBot$1 IS the next stored action -> replay it.
        assertFalse(Crupier.isSkippedSeatDuringRecover("CoronaBot$1", 7, ORDER));
        // And every in-order seat earlier in the hand replays normally.
        assertFalse(Crupier.isSkippedSeatDuringRecover("cliente", 0, ORDER));
        assertFalse(Crupier.isSkippedSeatDuringRecover("server", 5, ORDER));
        assertFalse(Crupier.isSkippedSeatDuringRecover("CoronaBot$3", 6, ORDER));
    }

    @Test
    @DisplayName("Once the replay reaches the live front, nothing is skipped (play live)")
    void caughtUpNeverSkips() {
        // contaAccion == order.size(): all stored actions replayed, hand continues live.
        assertFalse(Crupier.isSkippedSeatDuringRecover("cliente", ORDER.size(), ORDER));
        assertFalse(Crupier.isSkippedSeatDuringRecover("cliente", ORDER.size() + 3, ORDER));
    }

    @Test
    @DisplayName("No order (not a recover, or degraded) never skips")
    void noOrderNeverSkips() {
        assertFalse(Crupier.isSkippedSeatDuringRecover("cliente", 3, null));
        assertFalse(Crupier.isSkippedSeatDuringRecover("cliente", 0, Arrays.asList()));
    }

    // ---- security: a hostile host cannot silently drop MY OWN action via the skip ----

    @Test
    @DisplayName("Skipping MY seat while I still have un-replayed local actions = host omitting -> alert")
    void hostOmittingMyOwnActionIsDetected() {
        // The host's order asks to skip my seat, but my local DB still has actions I have not replayed:
        // the host is dropping an action I actually made. Detected against my own (host-uncontrolled) DB.
        assertTrue(Crupier.isHostOmittingOwnActionOnSkip(true, 0, 1));
        assertTrue(Crupier.isHostOmittingOwnActionOnSkip(true, 1, 2));
    }

    @Test
    @DisplayName("A genuine missed slot (all my actions replayed) is not flagged")
    void legitMissedSlotNotFlagged() {
        // I disconnected before acting on this street: I replayed every action I had stored, so skipping
        // my seat here is legitimate mutual omission, not an omission attack.
        assertFalse(Crupier.isHostOmittingOwnActionOnSkip(true, 2, 2));
        assertFalse(Crupier.isHostOmittingOwnActionOnSkip(true, 0, 0));
    }

    @Test
    @DisplayName("Skipping someone else's seat is never an own-action omission (the chain guards it)")
    void otherSeatSkipIsNotOwnOmission() {
        assertFalse(Crupier.isHostOmittingOwnActionOnSkip(false, 0, 5));
        assertFalse(Crupier.isHostOmittingOwnActionOnSkip(false, 3, 9));
    }
}
