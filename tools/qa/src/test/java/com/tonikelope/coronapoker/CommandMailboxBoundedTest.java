package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class CommandMailboxBoundedTest {
    @Test
    public void overflowClosesInsteadOfGrowingOrDroppingSilently() {
        GameCommandMailbox mailbox = new GameCommandMailbox(2);
        assertTrue(mailbox.offer("A", null));
        assertTrue(mailbox.offer("B", null));
        AtomicBoolean closed = new AtomicBoolean();
        assertFalse(mailbox.offer("C", () -> closed.set(true)));
        assertTrue(closed.get());
    }
}
