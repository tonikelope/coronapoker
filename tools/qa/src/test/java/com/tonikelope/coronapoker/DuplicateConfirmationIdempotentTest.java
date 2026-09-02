package com.tonikelope.coronapoker;

import com.tonikelope.coronapoker.core.network.ConfirmationTracker;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class DuplicateConfirmationIdempotentTest {

    @Test
    public void duplicateCannotCompleteAnythingTwice() {
        ConfirmationTracker tracker = new ConfirmationTracker();
        ConfirmationTracker.Request request = tracker.register(8, Collections.singleton("alice"));

        assertTrue(tracker.confirm("alice", 8));
        assertFalse(tracker.confirm("alice", 8));
        assertTrue(tracker.isComplete(request));
    }
}
