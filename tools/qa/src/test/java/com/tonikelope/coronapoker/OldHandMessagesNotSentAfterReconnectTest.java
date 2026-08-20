package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class OldHandMessagesNotSentAfterReconnectTest {
    @Test
    public void onlyNewGenerationCanBeSelectedForWriting() {
        SessionOutbox outbox = new SessionOutbox(2, 32);
        assertTrue(outbox.offer("OLD#critical"));
        SessionOutbox.Entry old = outbox.peek();
        outbox.advanceGeneration();
        assertTrue(outbox.offer("NEW#critical"));

        assertFalse(outbox.isCurrent(old));
        assertEquals("NEW#critical", outbox.peek().command());
    }

    @Test
    public void elementAndByteOverflowAreExplicit() {
        SessionOutbox outbox = new SessionOutbox(1, 4);
        assertTrue(outbox.offer("1234"));
        assertFalse(outbox.offer("x"));
        assertEquals(1, outbox.size());

        SessionOutbox byteBounded = new SessionOutbox(2, 4);
        assertFalse(byteBounded.offer("12345"));
        assertTrue(byteBounded.isEmpty());
    }
}
