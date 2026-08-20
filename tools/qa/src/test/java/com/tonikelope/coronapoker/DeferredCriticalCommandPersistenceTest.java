package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class DeferredCriticalCommandPersistenceTest {

    @Test
    public void acceptedCriticalCommandRemainsUntilItsPhaseConsumesIt() {
        AtomicBoolean closed = new AtomicBoolean();
        GameCommandMailbox mailbox = new GameCommandMailbox(4);
        assertTrue(mailbox.offer("GAME#1#FUTURE", () -> closed.set(true)));

        String future = mailbox.poll();
        for (int pass = 0; pass < 100_000; pass++) {
            mailbox.restoreRejected(Collections.singletonList(future));
            future = mailbox.poll();
        }

        assertFalse(closed.get());
        assertEquals("GAME#1#FUTURE", future);
    }
}
