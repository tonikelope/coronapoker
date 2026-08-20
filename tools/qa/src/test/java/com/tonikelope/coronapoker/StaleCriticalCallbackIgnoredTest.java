package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class StaleCriticalCallbackIgnoredTest {
    @Test
    public void staleCriticalCallbackCannotMutateCurrentState() {
        SessionGuard guard = new SessionGuard();
        SessionGuard.Generation old = guard.beginSession();
        guard.invalidate(old);
        guard.beginSession();
        AtomicInteger state = new AtomicInteger();

        assertFalse(guard.runIfCurrent(old, state::incrementAndGet));
        assertEquals(0, state.get());
    }
}
