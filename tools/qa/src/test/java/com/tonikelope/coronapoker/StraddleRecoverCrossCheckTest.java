/*
 * Regression test for the recover zero-trust cross-check of the voluntary straddle.
 *
 * On recover the client applies the host's STRADDLE_RESULT (deliberate: the hand was verified
 * live and is restored from the trusted fossil). To keep zero-trust, the client ALSO reads its
 * OWN fossil's STRADDLE@ record and, if the host's result contradicts it, warns WITHOUT changing
 * the applied value. recoverHostDecisionMismatch is the pure decision behind that warning: a
 * null local value (no applicable completed decision) must NEVER mismatch, so a client without
 * a decision to compare never accuses an honest host -- the false-positive this guards.
 */
package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StraddleRecoverCrossCheckTest {

    @Test
    public void nullFossilNeverMismatches() {
        // No applicable completed decision -> never accuse the host.
        assertFalse(Crupier.recoverHostDecisionMismatch(null, true));
        assertFalse(Crupier.recoverHostDecisionMismatch(null, false));
    }

    @Test
    public void agreementDoesNotMismatch() {
        assertFalse(Crupier.recoverHostDecisionMismatch(Boolean.TRUE, true), "fossil POST + host POST -> ok");
        assertFalse(Crupier.recoverHostDecisionMismatch(Boolean.FALSE, false), "fossil NO + host NO -> ok");
    }

    @Test
    public void contradictionMismatches() {
        assertTrue(Crupier.recoverHostDecisionMismatch(Boolean.FALSE, true), "fossil NO but host says POST -> warn");
        assertTrue(Crupier.recoverHostDecisionMismatch(Boolean.TRUE, false), "fossil POST but host says NO -> warn");
    }
}
