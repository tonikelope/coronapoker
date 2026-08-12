/*
 * Regression test for the recover zero-trust cross-check of the voluntary straddle.
 *
 * On recover the client applies the host's STRADDLE_RESULT (deliberate: the hand was verified
 * live and is restored from the trusted fossil). To keep zero-trust, the client ALSO reads its
 * OWN fossil's STRADDLE@ record and, if the host's result contradicts it, warns WITHOUT changing
 * the applied value. straddleRecoverResultMismatch is the pure decision behind that warning: a
 * null fossil value (fresh hand, or an old fossil without the field) must NEVER mismatch, so a
 * client that lacks the datum never accuses an honest host -- the false-positive this guards.
 */
package com.tonikelope.coronapoker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StraddleRecoverCrossCheckTest {

    @Test
    public void nullFossilNeverMismatches() {
        // No datum in our fossil (fresh hand / old fossil) -> never accuse the host.
        assertFalse(Crupier.straddleRecoverResultMismatch(null, true));
        assertFalse(Crupier.straddleRecoverResultMismatch(null, false));
    }

    @Test
    public void agreementDoesNotMismatch() {
        assertFalse(Crupier.straddleRecoverResultMismatch(Boolean.TRUE, true), "fossil POST + host POST -> ok");
        assertFalse(Crupier.straddleRecoverResultMismatch(Boolean.FALSE, false), "fossil NO + host NO -> ok");
    }

    @Test
    public void contradictionMismatches() {
        assertTrue(Crupier.straddleRecoverResultMismatch(Boolean.FALSE, true), "fossil NO but host says POST -> warn");
        assertTrue(Crupier.straddleRecoverResultMismatch(Boolean.TRUE, false), "fossil POST but host says NO -> warn");
    }
}
