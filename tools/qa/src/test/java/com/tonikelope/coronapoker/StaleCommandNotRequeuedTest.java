package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

public class StaleCommandNotRequeuedTest {
    @Test
    public void staleCriticalCommandIsNeverPutBack() {
        AtomicLong clock = new AtomicLong();
        GameCommandMailbox mailbox = new GameCommandMailbox(2, 10L, clock::get);
        mailbox.offer("GAME#1#OLD", () -> { });
        String old = mailbox.poll();
        clock.set(11L);
        assertEquals(1, mailbox.restoreRejected(Collections.singletonList(old)));
        assertNull(mailbox.poll());
    }
}
