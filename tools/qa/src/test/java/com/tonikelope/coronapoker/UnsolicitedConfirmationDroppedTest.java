package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class UnsolicitedConfirmationDroppedTest {

    @Test
    public void onlyPendingIdAndExpectedPeerAreAccepted() {
        ConfirmationTracker tracker = new ConfirmationTracker();
        ConfirmationTracker.Request request = tracker.register(7, Collections.singleton("alice"));

        assertFalse(tracker.confirm("alice", 99));
        assertFalse(tracker.confirm("mallory", 7));
        assertEquals(1, tracker.pendingRequestCount());
        assertTrue(tracker.isPending(request, "alice"));
    }
}
