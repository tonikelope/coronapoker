package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class CriticalMessageOrderingRegressionTest {
    @Test
    public void currentGenerationKeepsFifoOrder() {
        SessionOutbox outbox = new SessionOutbox(8, 1024);
        assertTrue(outbox.offer("SEATS#one"));
        assertTrue(outbox.offer("GAMECONFIG#two"));
        assertTrue(outbox.offer("INIT#three"));

        for (String expected : new String[]{"SEATS#one", "GAMECONFIG#two", "INIT#three"}) {
            SessionOutbox.Entry entry = outbox.peek();
            assertEquals(expected, entry.command());
            assertTrue(outbox.removeIfHead(entry));
        }
        assertTrue(outbox.isEmpty());
    }
}
