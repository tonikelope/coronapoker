package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class DuplicateCommandMetadataIsolationTest {

    @Test
    public void equalCommandsKeepIndependentSourceCloseActions() {
        AtomicInteger firstClosed = new AtomicInteger();
        AtomicInteger secondClosed = new AtomicInteger();
        GameCommandMailbox mailbox = new GameCommandMailbox(4);
        String repeatedInput = new String("GAME#7#HANDVERIFY");

        assertTrue(mailbox.offer(repeatedInput, firstClosed::incrementAndGet));
        assertTrue(mailbox.offer(repeatedInput, secondClosed::incrementAndGet));
        String first = mailbox.poll();
        String second = mailbox.poll();

        assertNotSame(first, second);
        assertTrue(mailbox.reject(first));
        assertEquals(1, firstClosed.get());
        assertEquals(0, secondClosed.get());
        assertTrue(mailbox.reject(second));
        assertEquals(1, secondClosed.get());
    }
}
