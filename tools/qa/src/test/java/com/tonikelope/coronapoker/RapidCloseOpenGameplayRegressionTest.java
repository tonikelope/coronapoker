package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class RapidCloseOpenGameplayRegressionTest {
    @Test
    public void oneHundredCloseOpenCyclesLeaveOnlyLatestSessionWritable() {
        SessionGuard guard = new SessionGuard();
        SessionGuard.Generation stale = null;
        for (int i = 0; i < 100; i++) {
            SessionGuard.Generation current = guard.beginSession();
            if (stale != null) guard.runIfCurrent(stale, () -> { throw new AssertionError("stale ran"); });
            guard.invalidate(current);
            stale = current;
        }
        SessionGuard.Generation latest = guard.beginSession();
        AtomicInteger state = new AtomicInteger();
        guard.runIfCurrent(latest, state::incrementAndGet);
        assertEquals(1, state.get());
    }
}
