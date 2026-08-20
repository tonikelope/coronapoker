package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class RejectedCriticalCommandClosesSourceTest {

    @Test
    public void consumedInvalidCriticalCommandClosesItsExactSourceOnce() {
        AtomicInteger closed = new AtomicInteger();
        GameCommandMailbox mailbox = new GameCommandMailbox(2, 100L, () -> 0L);
        assertTrue(mailbox.offer("GAME#1#DECK_CASCADE_PROOF#hash#bad", closed::incrementAndGet));

        String invalid = mailbox.poll();
        assertTrue(mailbox.reject(invalid));
        assertEquals(1, closed.get());
        assertTrue(!mailbox.reject(invalid));
        assertEquals(1, closed.get());
    }
}
