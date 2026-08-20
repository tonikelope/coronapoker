package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class CommandStarvationTest {
    @Test
    public void expectedCommandBehindFutureCommandIsStillReached() {
        GameCommandMailbox mailbox = new GameCommandMailbox(4, 1000L, () -> 0L);
        mailbox.offer("FUTURE", null);
        mailbox.offer("EXPECTED", null);
        String future = mailbox.poll();
        assertEquals("EXPECTED", mailbox.poll());
        mailbox.restoreRejected(Collections.singletonList(future));
        assertEquals("FUTURE", mailbox.poll());
    }
}
