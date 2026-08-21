package com.tonikelope.coronapoker;

import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

public class ConfirmationTrackerAffinityTest {

    @Test
    public void foreignOrClosedRequestCannotLookFullyConfirmed() {
        ConfirmationTracker original = new ConfirmationTracker();
        ConfirmationTracker replacement = new ConfirmationTracker();
        ConfirmationTracker.Request request = original.register(
                41, Collections.singleton("alice"));

        assertThrows(IllegalStateException.class,
                () -> replacement.remaining(request));

        original.close(request);
        assertThrows(IllegalStateException.class,
                () -> original.remaining(request));
    }
}
