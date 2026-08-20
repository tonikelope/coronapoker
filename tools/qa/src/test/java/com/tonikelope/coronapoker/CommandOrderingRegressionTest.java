package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class CommandOrderingRegressionTest {
    @Test
    public void rejectedOlderCommandReturnsAheadOfUnscannedNewerCommand() {
        GameCommandMailbox mailbox = new GameCommandMailbox(4);
        mailbox.offer("A", null);
        mailbox.offer("B", null);
        mailbox.offer("C", null);
        String rejected = mailbox.poll();
        assertEquals("B", mailbox.poll());
        mailbox.restoreRejected(Collections.singletonList(rejected));
        assertEquals("A", mailbox.poll());
        assertEquals("C", mailbox.poll());
    }
}
