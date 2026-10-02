/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.jna.ptr.IntByReference;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

final class GdxScreenWakeLockTest {

    @Test
    void currentWindowsNativeApiAcceptsAcquireAndRelease() {
        assumeTrue(System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).startsWith("windows"));
        GdxScreenWakeLock lock = GdxScreenWakeLock.forCurrentPlatform();
        try {
            lock.acquire();
            assertTrue(lock.held());
        } finally {
            lock.close();
        }
        assertFalse(lock.held());
    }

    @Test
    void windowsLockUsesOneContinuousAcquireAndMatchingRelease() {
        ArrayList<Integer> calls = new ArrayList<>();
        ArrayList<Long> threads = new ArrayList<>();
        long caller = Thread.currentThread().getId();
        GdxScreenWakeLock lock = GdxScreenWakeLock.windowsForTesting(
                flags -> {
                    calls.add(flags);
                    threads.add(Thread.currentThread().getId());
                    return 1;
                });

        lock.acquire();
        lock.acquire();
        assertTrue(lock.held());
        lock.close();

        assertFalse(lock.held());
        assertEquals(java.util.List.of(GdxScreenWakeLock.REQUIRED_FLAGS,
                GdxScreenWakeLock.RELEASE_FLAGS), calls);
        assertEquals(threads.get(0), threads.get(1));
        assertFalse(threads.get(0) == caller,
                "GLFW must not share the wake-lock owner thread");
    }

    @Test
    void unsupportedPlatformNeverClaimsToHoldALock() {
        GdxScreenWakeLock lock = GdxScreenWakeLock.unsupportedForTesting();

        lock.acquire();
        lock.close();

        assertFalse(lock.held());
    }

    @Test
    void rejectedAcquireDoesNotPretendToHoldOrReleaseALock() {
        ArrayList<Integer> calls = new ArrayList<>();
        GdxScreenWakeLock lock = GdxScreenWakeLock.windowsForTesting(
                flags -> {
                    calls.add(flags);
                    return 0;
                });

        lock.acquire();
        lock.close();

        assertFalse(lock.held());
        assertEquals(java.util.List.of(GdxScreenWakeLock.REQUIRED_FLAGS),
                calls);
    }

    @Test
    void macLockRetainsAndReleasesTheReturnedAssertion() {
        ArrayList<String> acquired = new ArrayList<>();
        ArrayList<Integer> released = new ArrayList<>();
        GdxScreenWakeLock lock = GdxScreenWakeLock.macForTesting(
                new GdxScreenWakeLock.MacPowerApi() {
            @Override
            public int acquire(String assertionType, String reason,
                    IntByReference assertionId) {
                assertEquals("CoronaPoker game in progress", reason);
                acquired.add(assertionType);
                assertionId.setValue(acquired.size() == 1 ? 73 : 74);
                return 0;
            }

            @Override public int release(int assertionId) {
                released.add(assertionId);
                return 0;
            }
        });

        lock.acquire();
        assertTrue(lock.held());
        lock.close();

        assertFalse(lock.held());
        assertEquals(java.util.List.of("PreventUserIdleDisplaySleep",
                "PreventUserIdleSystemSleep"), acquired);
        assertEquals(java.util.List.of(74, 73), released);
    }

    @Test
    void macRollsBackDisplayAssertionIfSystemAssertionFails() {
        ArrayList<Integer> released = new ArrayList<>();
        GdxScreenWakeLock lock = GdxScreenWakeLock.macForTesting(
                new GdxScreenWakeLock.MacPowerApi() {
            private int calls;

            @Override
            public int acquire(String assertionType, String reason,
                    IntByReference assertionId) {
                calls++;
                assertionId.setValue(91);
                return calls == 1 ? 0 : 1;
            }

            @Override public int release(int assertionId) {
                released.add(assertionId);
                return 0;
            }
        });

        lock.acquire();

        assertFalse(lock.held());
        assertEquals(java.util.List.of(91), released);
    }

    @Test
    void linuxInhibitorIsBoundToTheCoronaPokerProcessLifetime() {
        assertEquals(java.util.List.of("systemd-inhibit",
                "--what=idle:sleep", "--mode=block",
                "--who=CoronaPoker", "--why=Poker game in progress",
                "tail", "--pid=4182", "-f", "/dev/null"),
                GdxScreenWakeLock.linuxInhibitorCommand(4182L));
    }
}
