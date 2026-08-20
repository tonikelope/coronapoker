package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class ConfirmationStateRegressionTest {

    @Test
    public void confirmationCompletesOnlyItsOwnRequestInExpectedPeerOrder() {
        ConfirmationTracker tracker = new ConfirmationTracker();
        ConfirmationTracker.Request first = tracker.register(10, Arrays.asList("alice", "bob"));
        ConfirmationTracker.Request second = tracker.register(11, Collections.singleton("carol"));

        assertTrue(tracker.confirm("bob", 10));
        assertEquals(Collections.singletonList("alice"), tracker.remaining(first));
        assertEquals(Collections.singletonList("carol"), tracker.remaining(second));
        assertFalse(tracker.isComplete(first));
        assertFalse(tracker.isComplete(second));
    }
}
