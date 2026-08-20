package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class StaleSessionCallbackIgnoredTest {
    @Test
    public void delayedCallbackIsRejectedAfterCloseOpen() throws Exception {
        SessionGuard guard = new SessionGuard();
        SessionGuard.Generation old = guard.beginSession();
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger state = new AtomicInteger();
        Thread callback = new Thread(() -> {
            try { release.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            guard.runIfCurrent(old, state::incrementAndGet);
        });
        callback.start();
        guard.invalidate(old);
        guard.beginSession();
        release.countDown();
        callback.join(2000);

        assertEquals(0, state.get());
    }
}
