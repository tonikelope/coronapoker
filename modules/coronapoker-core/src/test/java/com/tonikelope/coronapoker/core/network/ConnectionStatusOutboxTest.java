/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionStatusOutboxTest {
    @Test
    void repeatedSnapshotsKeepOnlyTheLatestWithinBothBounds() {
        var outbox = new ConnectionStatusOutbox(2, 100);
        outbox.resetGeneration(3);
        for (int index = 0; index < 10_000; index++) {
            assertTrue(outbox.offer(3, "TELEMETRY#" + index + "#"));
            assertTrue(outbox.offer(3, "TIMEOUT#Ym9i"));
            assertEquals(2, outbox.size());
            assertTrue(outbox.queuedBytes() <= 100);
        }
        assertEquals("TELEMETRY#9999#", outbox.poll().command());
        assertEquals("TIMEOUT#Ym9i", outbox.poll().command());
        assertNull(outbox.poll());
        assertEquals(0L, outbox.queuedBytes());
    }

    @Test
    void refusesNewKeysAndOversizedReplacementsWithoutLosingThePreviousSnapshot() {
        var outbox = new ConnectionStatusOutbox(1, 25);
        outbox.resetGeneration(1);
        assertTrue(outbox.offer(1, "TELEMETRY#1#"));
        assertFalse(outbox.offer(1, "TIMEOUT#Ym9i"));
        assertFalse(outbox.offer(1, "TELEMETRY#2#" + "x".repeat(100)));
        assertEquals("TELEMETRY#1#", outbox.poll().command());
        assertEquals(0L, outbox.queuedBytes());
    }

    @Test
    void rejectsCriticalAndMalformedCommands() {
        var outbox = new ConnectionStatusOutbox(16, 64 * 1024);
        outbox.resetGeneration(1);
        for (String command : new String[]{"ACTION#signed", "HANDVERIFY#signed", "DUALLOCK_BUNDLE#proof",
                "SERVEREXIT", "TIMEOUT#%%%", "TIMEOUT#", "TIMEOUT#/w==", "TELEMETRY#broken"}) {
            assertThrows(IllegalArgumentException.class, () -> outbox.offer(1, command), command);
        }
        assertTrue(outbox.isEmpty());
    }

    @Test
    void replacementBelongsToTheNewSocketGeneration() {
        var outbox = new ConnectionStatusOutbox(2, 100);
        outbox.resetGeneration(1);
        assertTrue(outbox.offer(1, "TELEMETRY#1#"));
        outbox.resetGeneration(2);
        assertFalse(outbox.offer(1, "TIMEOUT#Ym9i"));
        assertTrue(outbox.offer(2, "TELEMETRY#2#"));
        assertFalse(outbox.offer(1, "TELEMETRY#1#"));
        assertEquals("TELEMETRY#2#", outbox.poll().command());
        assertTrue(outbox.offer(2, "TIMEOUT#Ym9i"));
        outbox.resetGeneration(-1L);
        assertFalse(outbox.offer(2, "TIMEOUT#Ym9i"));
        assertTrue(outbox.isEmpty());
        assertEquals(0L, outbox.queuedBytes());
    }
}
