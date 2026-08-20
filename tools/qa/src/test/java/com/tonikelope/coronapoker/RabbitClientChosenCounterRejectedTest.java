package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class RabbitClientChosenCounterRejectedTest {
    @Test
    public void requestWireHasNoEconomicCounterAndRejectsAppendedData() {
        RabbitFeeLedger.Request request = new RabbitFeeLedger.Request(
                hand(1), "alice", nonce(7), signature(3));
        byte[] exact = request.encode();
        assertTrue(RabbitFeeLedger.Request.decode(exact).isOk());
        byte[] withClientCounter = Arrays.copyOf(exact, exact.length + 4);
        withClientCounter[withClientCounter.length - 1] = 1;
        assertFalse(RabbitFeeLedger.Request.decode(withClientCounter).isOk());
    }

    static byte[] hand(int marker) { byte[] out = new byte[RabbitFeeLedger.HAND_BYTES]; out[0] = (byte) marker; return out; }
    static byte[] nonce(int marker) { byte[] out = new byte[16]; out[0] = (byte) marker; return out; }
    static byte[] signature(int marker) { byte[] out = new byte[64]; out[0] = (byte) marker; return out; }
}
