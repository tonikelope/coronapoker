/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.graphics.Color;
import com.tonikelope.coronapoker.table.TableSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GdxLatencyIndicatorTest {

    @Test
    void mapsFreshLatencySamplesToTheExpectedQualityColours() {
        long now = System.currentTimeMillis();

        assertColour(0x4caf50ff, player(42, 50, now));
        assertColour(0xffc107ff, player(180, 220, now));
        assertColour(0xff9800ff, player(320, 390, now));
        assertColour(0xf44336ff, player(480, 510, now));
    }

    @Test
    void usesTheAvailableSampleAndMarksMissingOrStaleTelemetry() {
        long now = System.currentTimeMillis();

        assertColour(0x4caf50ff, player(-1, 75, now));
        assertColour(0xf44336ff, player(-1, -1, now));
        assertColour(0x9e9e9eff, player(20, 25, 0L));
    }

    private static void assertColour(int expected,
            TableSnapshot.PlayerSnapshot player) {
        assertEquals(expected,
                Color.rgba8888(CoronaPokerGdxTable.latencyColor(player)));
    }

    private static TableSnapshot.PlayerSnapshot player(int latency,
            int previousLatency, long measuredAt) {
        return new TableSnapshot.PlayerSnapshot("remote", 10d, 0d, 0d,
                true, false, false, false, latency, previousLatency, 0,
                measuredAt, false, TableSnapshot.Position.NONE, "", "",
                List.of());
    }
}
