/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.graphics.Color;
import com.tonikelope.coronapoker.core.LobbyParticipant;
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

    @Test
    void tooltipUsesTheSameEffectiveLatencyAsTheColourIndicator() {
        assertEquals(42, CoronaPokerGdxTable.effectiveLatencyMillis(
                player(42, 50, System.currentTimeMillis())));
        assertEquals(75, CoronaPokerGdxTable.effectiveLatencyMillis(
                player(-1, 75, System.currentTimeMillis())));
        assertEquals(-1, CoronaPokerGdxTable.effectiveLatencyMillis(
                player(-1, -1, System.currentTimeMillis())));
    }

    @Test
    void latencyTooltipHitAreaCoversTheDotWithoutStealingNearbyInput() {
        assertEquals(true, CoronaPokerGdxTable.latencyDotContains(
                100f, 200f, 100f, 200f));
        assertEquals(true, CoronaPokerGdxTable.latencyDotContains(
                113f, 200f, 100f, 200f));
        assertEquals(false, CoronaPokerGdxTable.latencyDotContains(
                115f, 200f, 100f, 200f));
    }

    @Test
    void lobbyTreatsConnectedLocalEndpointsAsZeroLatency() {
        LobbyParticipant local = lobbyParticipant("host", true, false);
        LobbyParticipant bot = lobbyParticipant("CoronaBot$1", false, true);
        LobbyParticipant unresolvedRemote = lobbyParticipant("remote", false,
                false);

        assertEquals(0, GdxFrontendScreen.lobbyEffectiveLatencyMillis(local));
        assertEquals(0, GdxFrontendScreen.lobbyEffectiveLatencyMillis(bot));
        assertEquals(-1,
                GdxFrontendScreen.lobbyEffectiveLatencyMillis(unresolvedRemote));
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

    private static LobbyParticipant lobbyParticipant(String nickname,
            boolean local, boolean bot) {
        return new LobbyParticipant(nickname, null, local, local, bot,
                true, false, true, LobbyParticipant.NO_LATENCY,
                LobbyParticipant.NO_LATENCY);
    }
}
