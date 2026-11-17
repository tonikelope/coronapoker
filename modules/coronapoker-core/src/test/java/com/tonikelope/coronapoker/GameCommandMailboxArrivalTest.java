package com.tonikelope.coronapoker;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class GameCommandMailboxArrivalTest {
    @Test
    void arrivalBetweenScanAndWaitSkipsBlocking() throws InterruptedException {
        GameCommandMailbox mailbox = new GameCommandMailbox(4);
        long observed = mailbox.arrivalSequence();
        mailbox.add("GAME#1#DECK_CASCADE_PROOF#response");

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> mailbox.awaitAfterScan(observed, 20_000L));
        assertEquals("GAME#1#DECK_CASCADE_PROOF#response", mailbox.poll());
    }

    @Test
    void restoringDeferredCommandsDoesNotBypassTheBoundedWait() {
        GameCommandMailbox mailbox = new GameCommandMailbox(4);
        mailbox.add("GAME#1#FUTURE");
        long observed = mailbox.arrivalSequence();
        mailbox.restoreRejected(List.of(mailbox.poll()));

        assertEquals(observed, mailbox.arrivalSequence());
        assertEquals("GAME#1#FUTURE", mailbox.poll());
    }

    @Test
    void interruptionStopsTheConsumerEvenWithFreshTraffic() {
        GameCommandMailbox mailbox = new GameCommandMailbox(4);
        long observed = mailbox.arrivalSequence();
        mailbox.add("GAME#1#FRESH");
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> mailbox.awaitAfterScan(observed, 250L));
        } finally {
            Thread.interrupted();
        }
    }
}
