/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class JvmDeadlockWatchdogTest {

    @Test void reportsOnlyOnceAndStopsCleanly() throws Exception {
        AtomicInteger reports = new AtomicInteger();
        CountDownLatch delivered = new CountDownLatch(1);
        try (JvmDeadlockWatchdog watchdog = new JvmDeadlockWatchdog(10L,
                () -> "deadlocked threads", ignored -> {
                    reports.incrementAndGet();
                    delivered.countDown();
                })) {
            watchdog.start();
            watchdog.start();
            assertTrue(delivered.await(2L, TimeUnit.SECONDS));
            Thread.sleep(40L);
            assertEquals(1, reports.get());
        }
    }
}
