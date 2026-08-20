package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class RabbitDuplicateRequestIdempotentTest {
    @Test
    public void duplicateNonceReturnsSameAuthorizationAndChargesOnce() {
        RabbitFeeLedger ledger = new RabbitFeeLedger(RabbitClientChosenCounterRejectedTest.hand(2), 3, 10, 20);
        RabbitFeeLedger.Request request = new RabbitFeeLedger.Request(
                RabbitClientChosenCounterRejectedTest.hand(2), "alice",
                RabbitClientChosenCounterRejectedTest.nonce(9));
        RabbitFeeLedger.Authorization first = ledger.authorize(request).value();
        RabbitFeeLedger.Authorization duplicate = ledger.authorize(request).value();
        assertArrayEquals(first.encode(), duplicate.encode());
        assertEquals(RabbitFeeLedger.Acceptance.ACCEPTED, ledger.accept(first));
        assertEquals(RabbitFeeLedger.Acceptance.DUPLICATE, ledger.accept(duplicate));
    }
}
