package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

public class FuturePhaseCommandExpiresTest {
    @Test
    public void deferredFutureCommandExpiresWithExplicitClose() {
        AtomicLong clock = new AtomicLong();
        AtomicBoolean closed = new AtomicBoolean();
        GameCommandMailbox mailbox = new GameCommandMailbox(4, 100L, clock::get);
        mailbox.offer("GAME#1#FUTURE", () -> closed.set(true));
        String future = mailbox.poll();
        clock.set(101L);
        assertEquals(1, mailbox.restoreRejected(Collections.singletonList(future)));
        assertTrue(closed.get());
        assertTrue(mailbox.isEmpty());
    }
}
