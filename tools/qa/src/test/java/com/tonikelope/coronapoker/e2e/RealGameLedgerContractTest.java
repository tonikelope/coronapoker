package com.tonikelope.coronapoker.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class RealGameLedgerContractTest {

    @Test
    void readsCumulativeBuyinsAfterARebuy() {
        String ledger = "CP_E2E_LEDGER handId=7 end=1 potCents=80 "
                + "balanceRows=4 stackCents=5000 buyinCents=5000";

        assertEquals(4, RealGameLoopbackE2EIT.ledgerMetric(ledger, "balanceRows"));
        assertEquals(5000, RealGameLoopbackE2EIT.ledgerMetric(ledger, "stackCents"));
        assertEquals(5000, RealGameLoopbackE2EIT.ledgerMetric(ledger, "buyinCents"));
        assertEquals(0, RealGameLoopbackE2EIT.ledgerCapitalDeltaCents(ledger));
    }

    @Test
    void detectsCreatedOrMissingMoney() {
        String ledger = "CP_E2E_LEDGER balanceRows=4 stackCents=4999 buyinCents=5000";

        assertEquals(-1, RealGameLoopbackE2EIT.ledgerCapitalDeltaCents(ledger));
    }

    @Test
    void rejectsAReportWithoutTheRequestedMetric() {
        assertThrows(IllegalArgumentException.class,
                () -> RealGameLoopbackE2EIT.ledgerMetric(
                        "CP_E2E_LEDGER balanceRows=4 stackCents=5000", "buyinCents"));
    }
}
