package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class RabbitFeeSequencePropertyTest {
    @Test
    public void everyPeerDerivesTheSameCountAndFee() {
        RabbitFeeLedger host = new RabbitFeeLedger(RabbitClientChosenCounterRejectedTest.hand(1), 3, 10, 20);
        RabbitFeeLedger peer = new RabbitFeeLedger(RabbitClientChosenCounterRejectedTest.hand(1), 3, 10, 20);
        long[] fees = {0, 10, 20, 20, 20};
        for (int i = 0; i < fees.length; i++) {
            RabbitFeeLedger.Request request = new RabbitFeeLedger.Request(
                    RabbitClientChosenCounterRejectedTest.hand(1), "alice",
                    RabbitClientChosenCounterRejectedTest.nonce(i + 1));
            RabbitFeeLedger.Authorization auth = host.authorize(request).value();
            assertEquals(i + 1, auth.count());
            assertEquals(fees[i], auth.feeCents());
            assertTrue(peer.accept(auth).isAccepted());
        }
    }

    @Test
    public void alteredHostFeeIsRejectedByIndependentDerivation() {
        RabbitFeeLedger host = new RabbitFeeLedger(RabbitClientChosenCounterRejectedTest.hand(3), 3, 10, 20);
        RabbitFeeLedger peer = new RabbitFeeLedger(RabbitClientChosenCounterRejectedTest.hand(3), 3, 10, 20);
        RabbitFeeLedger.Request first = new RabbitFeeLedger.Request(
                RabbitClientChosenCounterRejectedTest.hand(3), "alice",
                RabbitClientChosenCounterRejectedTest.nonce(1));
        RabbitFeeLedger.Authorization authorization = host.authorize(first).value();
        byte[] altered = authorization.encode();
        altered[altered.length - 1] = 1; // count 1 must cost exactly zero.
        RabbitFeeLedger.Result<RabbitFeeLedger.Authorization> decoded
                = RabbitFeeLedger.Authorization.decode(altered);
        assertTrue(decoded.isOk());
        assertFalse(peer.accept(decoded.value()).isAccepted());
    }
}
