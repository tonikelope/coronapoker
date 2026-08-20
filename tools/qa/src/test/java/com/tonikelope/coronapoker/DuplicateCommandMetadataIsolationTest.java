package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

public class DuplicateCommandMetadataIsolationTest {

    @Test
    public void equalCommandsKeepIndependentAgeAndCloseActions() {
        AtomicLong clock = new AtomicLong();
        AtomicInteger firstClosed = new AtomicInteger();
        AtomicInteger secondClosed = new AtomicInteger();
        GameCommandMailbox mailbox = new GameCommandMailbox(4, 100L, clock::get);
        String repeatedInput = new String("GAME#7#HANDVERIFY");

        assertTrue(mailbox.offer(repeatedInput, firstClosed::incrementAndGet));
        assertTrue(mailbox.offer(repeatedInput, secondClosed::incrementAndGet));
        String first = mailbox.poll();
        String second = mailbox.poll();

        clock.set(101L);
        assertEquals(2, mailbox.restoreRejected(Arrays.asList(first, second)));
        assertEquals(1, firstClosed.get());
        assertEquals(1, secondClosed.get());
        assertTrue(mailbox.isEmpty());
    }
}
