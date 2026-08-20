package com.tonikelope.coronapoker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class SessionGenerationGuardTest {
    @Test
    public void currentGenerationRunsCriticalEffectExactlyOnce() {
        SessionGuard guard = new SessionGuard();
        SessionGuard.Generation current = guard.beginSession();
        AtomicInteger state = new AtomicInteger();

        assertTrue(guard.runIfCurrent(current, state::incrementAndGet));
        assertEquals(1, state.get());
    }

    @Test
    public void newSessionWaitsForKnownCriticalCallbackToTerminate() throws Exception {
        SessionGuard guard = new SessionGuard();
        SessionGuard.Generation current = guard.beginSession();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch opened = new CountDownLatch(1);
        Thread callback = new Thread(() -> guard.runIfCurrent(current, () -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        }));
        callback.start();
        assertTrue(entered.await(1, TimeUnit.SECONDS));
        Thread opener = new Thread(() -> {
            guard.beginSession();
            opened.countDown();
        });
        opener.start();

        assertFalse(opened.await(100, TimeUnit.MILLISECONDS));
        release.countDown();
        assertTrue(opened.await(1, TimeUnit.SECONDS));
        callback.join(1000);
        opener.join(1000);
    }
}
