package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class RabbitCanonicalHandIdIntegrationTest {

    @Test
    public void showdownLedgerAcceptsTheCanonicalRuntimeHandId() {
        byte[] runtimeHandId = new byte[CanonicalActionRecord.HAND_ID_BYTES];

        assertEquals(CanonicalActionRecord.HAND_ID_BYTES, RabbitFeeLedger.HAND_BYTES);
        assertDoesNotThrow(() -> new RabbitFeeLedger(runtimeHandId, 0, 10L, 20L));
    }
}
