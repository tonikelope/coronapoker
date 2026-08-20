package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class SessionGenerationInvalidatesOutboxTest {
    @Test
    public void reconnectInvalidatesEveryEntryFromPreviousGeneration() {
        SessionOutbox outbox = new SessionOutbox(8, 1024);
        assertTrue(outbox.offer("GAMEINFO#old"));
        SessionOutbox.Entry old = outbox.peek();

        outbox.advanceGeneration();

        assertFalse(outbox.isCurrent(old));
        assertTrue(outbox.isEmpty());
    }
}
